#!/usr/bin/env bash
# ============================================================================
# 从 master 一键出包并部署（构建机执行，通过 SSH 推送到应用服务器）
#
# 用法：
#   ./deploy/deploy_from_master.sh                  # 构建 master + 部署 + 健康检查
#   ./deploy/deploy_from_master.sh --build-only     # 只构建，产物留在 target/ 与 dist/
#   ./deploy/deploy_from_master.sh --ref v1.0.1     # 指定分支 / Tag / 提交
#   ./deploy/deploy_from_master.sh --allow-dirty    # 允许工作区有未提交改动（不推荐）
#   ./deploy/deploy_from_master.sh --help
#
# 可覆盖的环境变量：
#   TARGET_HOST=192.168.1.14   TARGET_USER=zk   APP_DIR=/opt/report
#   SERVICE=report             API_PORT=8763    WEB_PORT=8080
#
# 前置条件：
#   - 构建机：git、JDK 17、Maven 3.8+、Node 18+/npm 9+、tar、ssh、scp
#   - 目标机：已完成首次部署（/opt/report、systemd 单元、nginx），且 $TARGET_USER 可免密 sudo
#   - 构建机到目标机已配置 SSH 免密
# ============================================================================
set -euo pipefail

TARGET_HOST="${TARGET_HOST:-192.168.1.14}"
TARGET_USER="${TARGET_USER:-zk}"
APP_DIR="${APP_DIR:-/opt/report}"
SERVICE="${SERVICE:-report}"
API_PORT="${API_PORT:-8763}"
WEB_PORT="${WEB_PORT:-8080}"

REF="master"
DO_DEPLOY=1
ALLOW_DIRTY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref) [[ $# -ge 2 ]] || { echo "--ref 需要参数"; exit 1; }; REF="$2"; shift 2 ;;
    --build-only|--no-deploy) DO_DEPLOY=0; shift ;;
    --allow-dirty) ALLOW_DIRTY=1; shift ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[x]\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- 0. 环境检查
log "检查构建环境"
for c in git mvn node npm tar ssh scp; do
  command -v "$c" >/dev/null 2>&1 || die "缺少命令: $c"
done
JAVA_VER="$(java -version 2>&1 | head -1)"
echo "    $JAVA_VER"
echo "$JAVA_VER" | grep -qE '"(1[7-9]|2[0-9])' || die "需要 JDK 17+（当前：$JAVA_VER）"

if [[ "$ALLOW_DIRTY" -eq 0 && -n "$(git status --porcelain)" ]]; then
  die "工作区有未提交改动；提交后再部署，或加 --allow-dirty"
fi

# ---------------------------------------------------------------- 1. 同步代码
log "同步代码到 $REF"
git fetch --tags --quiet origin || warn "git fetch 失败，使用本地 $REF"
git checkout --quiet "$REF"
if git rev-parse --verify --quiet "origin/$REF" >/dev/null; then
  git reset --quiet --hard "origin/$REF"
fi
COMMIT="$(git rev-parse --short HEAD)"
COMMIT_FULL="$(git rev-parse HEAD)"
echo "    提交: $COMMIT  $(git log -1 --pretty=%s)"

# ---------------------------------------------------------------- 2. 构建
log "构建后端（跳过测试；测试请单独执行 mvn test）"
( cd backend && mvn -B -DskipTests clean package )
JAR="backend/target/report-system-1.0.0.jar"
[[ -f "$JAR" ]] || die "后端产物不存在：$JAR"

log "构建前端"
if [[ ! -d frontend2/node_modules ]]; then
  ( cd frontend2 && npm ci --no-audit --no-fund )
fi
( cd frontend2 && npm run build )
[[ -f frontend2/dist/index.html ]] || die "前端产物不存在"

JAR_SHA="$(sha256sum "$JAR" | cut -d' ' -f1)"
FE_ENTRY="$(grep -o 'assets/index-[A-Za-z0-9_-]*\.js' frontend2/dist/index.html | head -1 || true)"
BUILD_TIME="$(date '+%F %T %z')"

log "产物信息"
echo "    提交    : $COMMIT"
echo "    jar     : $JAR ($(du -h "$JAR" | cut -f1))"
echo "    sha256  : $JAR_SHA"
echo "    前端入口: ${FE_ENTRY:--}"
echo "    时间    : $BUILD_TIME"

if [[ "$DO_DEPLOY" -eq 0 ]]; then
  log "仅构建模式：产物已就绪，退出"
  exit 0
fi

# ---------------------------------------------------------------- 3. 打包上传
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/BUILD_INFO.txt" <<EOF
build_time=$BUILD_TIME
git_commit=$COMMIT ($COMMIT_FULL)
spring_boot=3.5.16
java=$(java -version 2>&1 | head -1)
jar_sha256=$JAR_SHA
frontend_entry=${FE_ENTRY:--}
built_by=deploy_from_master.sh
EOF

( cd frontend2/dist && tar -czf "$TMP/dist.tar.gz" . )

cat > "$TMP/remote_deploy.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$APP_DIR"
if [ -f report-api.jar ]; then
  cp -f report-api.jar report-api.prev.jar
fi
install -m 644 -o "$TARGET_USER" -g "$TARGET_USER" /tmp/report-api.jar "$APP_DIR/report-api.jar"
install -m 644 /tmp/BUILD_INFO.txt "$APP_DIR/BUILD_INFO.txt"
rm -rf "$APP_DIR/static"/*
tar -xzf /tmp/report-dist.tar.gz -C "$APP_DIR/static"
chown -R "$TARGET_USER:$TARGET_USER" "$APP_DIR/static"
systemctl restart "$SERVICE"
EOF

log "上传产物到 $TARGET_USER@$TARGET_HOST"
scp -q "$JAR"                  "$TARGET_USER@$TARGET_HOST:/tmp/report-api.jar"
scp -q "$TMP/dist.tar.gz"      "$TARGET_USER@$TARGET_HOST:/tmp/report-dist.tar.gz"
scp -q "$TMP/BUILD_INFO.txt"   "$TARGET_USER@$TARGET_HOST:/tmp/BUILD_INFO.txt"
scp -q "$TMP/remote_deploy.sh" "$TARGET_USER@$TARGET_HOST:/tmp/remote_deploy.sh"

# ---------------------------------------------------------------- 4. 切换并重启
log "备份旧包、切换产物并重启服务"
ssh "$TARGET_USER@$TARGET_HOST" "sudo -n bash /tmp/remote_deploy.sh" \
  || die "远程部署失败（需要免密 sudo，或查看 $APP_DIR/logs/app.log）"

# ---------------------------------------------------------------- 5. 健康检查
log "健康检查"
ok=0
for i in $(seq 1 30); do
  sleep 2
  if curl -fsS -o /dev/null --max-time 5 "http://$TARGET_HOST:$API_PORT/api/auth/captcha"; then
    ok=1
    echo "    后端就绪（约 $((i * 2)) 秒）"
    break
  fi
done

if [[ "$ok" -eq 0 ]]; then
  warn "后端未就绪，回滚到上一个版本"
  ssh "$TARGET_USER@$TARGET_HOST" \
    "sudo -n bash -c 'cd $APP_DIR && test -f report-api.prev.jar && cp -f report-api.prev.jar report-api.jar && systemctl restart $SERVICE'"
  die "部署失败，已尝试回滚；请检查 $APP_DIR/logs/app.log"
fi

if curl -fsS -o /dev/null --max-time 5 "http://$TARGET_HOST:$WEB_PORT/"; then
  echo "    前端可访问（$WEB_PORT）"
else
  warn "前端 $WEB_PORT 未响应，请检查 nginx"
fi

REMOTE_SHA="$(ssh "$TARGET_USER@$TARGET_HOST" "sha256sum $APP_DIR/report-api.jar" | cut -d' ' -f1)"
if [[ "$REMOTE_SHA" == "$JAR_SHA" ]]; then
  echo "    产物校验一致（$JAR_SHA）"
else
  die "上传产物与本地不一致：local=$JAR_SHA remote=$REMOTE_SHA"
fi

log "完成：$COMMIT → $TARGET_HOST"
echo "    回滚：ssh $TARGET_USER@$TARGET_HOST 'sudo cp -f $APP_DIR/report-api.prev.jar $APP_DIR/report-api.jar && sudo systemctl restart $SERVICE'"
