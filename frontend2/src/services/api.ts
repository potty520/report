import request from '@/utils/request';

// ==================== Type Definitions ====================

export interface ApiResponse<T = any> {
  code: number;
  data: T;
  msg: string;
}

export interface PageResult<T = any> {
  list: T[];
  total: number;
  pageSize: number;
  pageNum: number;
  pages: number;
}

export interface PageParams {
  current?: number;
  size?: number;
  [key: string]: any;
}

// ==================== Auth ====================
export interface LoginParams {
  username: string;
  password: string;
  uuid: string;
  captcha: string;
}

export interface LoginResult {
  token: string;
  userInfo: UserInfo;
  permissions: string[];
  menus: MenuItem[];
}

export interface UserInfo {
  id: number;
  username: string;
  nickname: string;
  avatar?: string;
  email?: string;
  phone?: string;
  deptId?: number;
  deptName?: string;
  status: number;
  createTime: string;
}

export interface MenuItem {
  id: number;
  parentId: number;
  menuName: string;
  path: string;
  component?: string;
  icon?: string;
  sort: number;
  menuType: string; // C=页面 M=目录
  permission?: string;
  visible: number;
  children?: MenuItem[];
}

export interface CaptchaResult {
  uuid: string;
  captchaImage: string; // base64
}

/**
 * 修改密码参数。
 *
 * 后端 AuthController.updatePassword 用 @RequestParam 接收明文密码并直接做 BCrypt 比对，
 * 没有任何解密逻辑，因此这里必须是明文，且走 query/form 而不是 JSON body。
 */
export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

// ==================== User Management ====================
export interface UserItem {
  id: number;
  username: string;
  nickname: string;
  email: string;
  phone: string;
  gender: number;
  deptId: number;
  deptName: string;
  status: number;
  roleIds: number[];
  roleNames: string[];
  createTime: string;
}

export interface UserFormParams {
  id?: number;
  username: string;
  nickname: string;
  email?: string;
  phone?: string;
  gender?: number;
  deptId?: number;
  roleIds?: number[];
  status?: number;
  password?: string;
}

// ==================== Role Management ====================
export interface RoleItem {
  id: number;
  name: string;
  code: string;
  sort: number;
  status: number;
  remark?: string;
  menuIds?: number[];
  createTime: string;
}

export interface RoleFormParams {
  id?: number;
  name: string;
  code: string;
  sort?: number;
  status?: number;
  remark?: string;
  menuIds?: number[];
}

// ==================== Menu Management ====================
export interface MenuTreeItem extends MenuItem {
  children?: MenuTreeItem[];
}

export interface MenuFormParams {
  id?: number;
  parentId: number;
  menuName: string;
  menuType: string; // C=页面 M=目录 F=按钮
  path?: string;
  component?: string;
  icon?: string;
  sort?: number;
  permission?: string;
  visible?: number;
}

// ==================== Log Management ====================
export interface LogItem {
  id: number;
  username: string;
  ip: string;
  location?: string;
  browser?: string;
  os?: string;
  status: number;
  msg?: string;
  createTime: string;
}

// ==================== Dept Management ====================
export interface DeptItem {
  id: number;
  parentId: number;
  deptName: string;
  deptCode?: string;
  sort: number;
  leader?: string;
  phone?: string;
  email?: string;
  status: number;
  children?: DeptItem[];
  projects?: ProjectItem[];
  createTime?: string;
}

export interface DeptFormParams {
  id?: number;
  parentId: number;
  deptName: string;
  sort?: number;
  leader?: string;
  phone?: string;
  email?: string;
  status?: number;
}

// ==================== Project Management ====================
export interface ProjectItem {
  id: number;
  parentId: number;
  projectName: string;
  projectCode?: string;
  sort: number;
  status: number;
  remark?: string;
  children?: ProjectItem[];
  createTime?: string;
}

export interface ProjectFormParams {
  id?: number;
  parentId: number;
  projectName: string;
  projectCode?: string;
  sort?: number;
  status?: number;
  remark?: string;
}

// ==================== Template ====================
export interface TemplateItem {
  id: number;
  name: string;
  code: string;
  description?: string;
  version: number;
  status: number; // 0=draft 1=published 2=disabled
  excelFileName?: string;
  fields: TemplateField[];
  datasourceId?: number;
  datasourceName?: string;
  createTime: string;
  updateTime: string;
}

export interface TemplateField {
  id?: number;
  templateId?: number;
  fieldName: string;
  fieldLabel: string;
  fieldType: string; // string/number/date/datetime
  excelColumn: string; // e.g. A, B, C
  required: number;
  sort: number;
  options?: string; // JSON for select options
  /** 后端 rpt_template_field.fieldCode：dataJson 解析时优先按它取 key */
  fieldCode?: string;
  dbColumn?: string;
  fieldLength?: number;
  /** 后端字段（与 required 同义，API 层归一化时两个都会填） */
  isRequired?: number;
}

export interface TemplateFormParams {
  id?: number;
  name: string;
  code: string;
  description?: string;
  status?: number;
  fields?: TemplateField[];
  datasourceId?: number;
}

// ==================== Datasource ====================
export interface DatasourceItem {
  /** 后端 rpt_datasource 的真实字段名（dsName/dsType）；name/type 由 API 层归一化补出 */
  dsName?: string;
  dsType?: string;
  id: number;
  name: string;
  type: string; // mysql/postgresql/sqlserver
  host: string;
  port: number;
  databaseName: string;
  username: string;
  status: number;
  remark?: string;
  createTime: string;
}

export interface DatasourceFormParams {
  id?: number;
  name: string;
  type: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password?: string;
  status?: number;
  remark?: string;
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

// ==================== Task ====================
export interface TaskItem {
  /** 后端 rpt_task 的真实字段名；name 由 API 层归一化补出 */
  taskName?: string;
  id: number;
  name: string;
  templateId: number;
  templateName: string;
  year: number;
  period: string;
  startDate: string;
  endDate: string;
  status: number; // 0=draft 1=published 2=closed
  description?: string;
  createTime: string;
}

export interface TaskFormParams {
  id?: number;
  name: string;
  templateId: number;
  year: number;
  period: string;
  startDate: string;
  endDate: string;
  status?: number;
  description?: string;
}

export interface TaskAssignItem {
  id: number;
  taskId: number;
  taskName: string;
  deptId: number;
  deptName: string;
  projectId?: number;
  projectName?: string;
  status: number; // 0=未开始 1=进行中 2=已完成
  assignTime: string;
}

export interface TaskAssignFormParams {
  taskId: number;
  /** 后端 @RequestParam(required = false) List<Long> deptIds */
  deptIds?: number[];
  /** 后端 @RequestParam(required = false) List<Long> userIds */
  userIds?: number[];
  /** 后端 @RequestParam String assignType（必填）：FILL=填报分派 AUDIT=审核分派 */
  assignType: 'FILL' | 'AUDIT';
}

export interface TaskLedgerItem {
  id: number;
  taskId: number;
  taskName: string;
  templateName: string;
  deptName: string;
  projectName?: string;
  status: number;
  fillStatus: number; // 0=未填报 1=已填报 2=已提交
  assignTime: string;
  fillTime?: string;
  submitTime?: string;
}

// ==================== Fill ====================
/**
 * 填报列表/详情行。
 *
 * 后端 /fill/task/page、/fill/task/{taskId} 直接返回 rpt_task_data 实体：
 * 里面只有 taskId，**没有分配记录主键字段**。旧前端误把不存在的分配ID当作 taskId，
 * 请求会变成 /fill/task/undefined，数据填报整条链路全错位。
 */
export interface FillTaskItem {
  id: number;
  taskId: number;
  templateId?: number;
  taskName?: string;
  templateName?: string;
  deptName?: string;
  status: number; // 0=草稿(未提交) 1=已提交
  auditStatus?: number;
  dataJson?: string;
  fileUrl?: string;
  fileName?: string;
  submitTime?: string;
  createTime?: string;
  /** 仅前端使用：该行来自「已分派任务」但还没有任何填报记录 */
  pending?: boolean;
}

/** 我被分派的填报任务（含尚未保存草稿、从未填报过的任务） */
export interface AssignedTaskItem {
  id: number;
  taskName: string;
  templateId: number;
  templateName?: string;
  status?: number;
  belongTime?: string;
  fillDeadline?: string;
  totalCount?: number;
  fillCount?: number;
}

/** 填报详情（GET /fill/task/{taskId}，后端注释即「填报详情(通过taskId)」） */
export type FillDetail = FillTaskItem;

/** 提交 / 保存草稿参数：后端全部用 @RequestParam 接收，不是 JSON body */
export interface FillSubmitParams {
  taskId: number;
  /** JSON.stringify 后的填报内容，key 使用模板字段的 fieldCode / fieldName */
  dataJson: string;
  fileUrl?: string;
}

/** 附件上传结果（POST /fill/upload） */
export interface FillUploadResult {
  fileId: string;
  fileName: string;
  fileUrl: string;
  fileSize?: string;
  md5?: string;
}

export interface AuditItem {
  id: number;
  fillId: number;
  taskName: string;
  templateName: string;
  deptName: string;
  projectName?: string;
  fillTime: string;
  submitTime: string;
  status: number; // 0=待审核 1=审核通过 2=审核拒绝
  auditType: string; // dept / task
}

export interface AuditFormParams {
  ids: number[];
  status: number; // 1=通过 2=拒绝
  remark?: string;
}

// ==================== Data Apply ====================
export interface DataApplyItem {
  id: number;
  taskId?: number;
  taskName?: string;
  title: string;
  reason: string;
  dataScope: string;
  applyTime: string;
  status: number; // 0=待审批 1=已通过 2=已拒绝
  applicantName: string;
  approverName?: string;
  approveTime?: string;
  approveRemark?: string;
}

export interface DataApplyFormParams {
  taskId: number;
  title: string;
  reason: string;
  dataScope: string;
}

export interface ApproveFormParams {
  id: number;
  status: number;
  remark?: string;
}

// ==================== Overview ====================
export interface OverviewStats {
  totalTasks: number;
  completedTasks: number;
  pendingTasks: number;
  overdueTasks: number;
  totalFills: number;
  submittedFills: number;
  auditPassRate: number;
  totalDepts: number;
}

export interface ChartDataItem {
  name: string;
  value: number;
}

export interface TrendDataItem {
  date: string;
  fillCount: number;
  submitCount: number;
  auditCount: number;
}

// ==================== API Methods ====================

// --- Auth ---
export async function loginApi(params: LoginParams) {
  return request.post<ApiResponse<LoginResult>>('/auth/login', params);
}

export async function logoutApi() {
  return request.post<ApiResponse<null>>('/auth/logout');
}

export async function getCaptchaApi() {
  return request.get<ApiResponse<CaptchaResult>>('/auth/captcha');
}

export async function getCurrentUserApi() {
  return request.get<ApiResponse<UserInfo>>('/auth/current-user'); // 后端已在 CompatController 补齐
}

export async function changePasswordApi(params: ChangePasswordParams) {
  // 后端是 PUT /auth/password，参数为 @RequestParam(旧/新密码)，明文，不走 JSON body
  return request.put<ApiResponse<null>>('/auth/password', null, { params });
}

// --- User ---
export async function getUserListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<UserItem>>>('/user/page', { params });
}

export async function getUserDetailApi(id: number) {
  return request.get<ApiResponse<UserItem>>(`/user/${id}`);
}

export async function createUserApi(data: UserFormParams) {
  return request.post<ApiResponse<null>>('/user', data);
}

export async function updateUserApi(data: UserFormParams) {
  return request.put<ApiResponse<null>>('/user', data);
}

export async function deleteUserApi(id: number) {
  return request.delete<ApiResponse<null>>(`/user/${id}`);
}

export async function updateUserStatusApi(id: number, status: number) {
  return request.put<ApiResponse<null>>(`/user/${id}/status`, { status });
}

export async function assignUserRolesApi(userId: number, roleIds: number[]) {
  return request.put<ApiResponse<null>>(`/user/${userId}/roles`, { roleIds });
}

// --- Role ---
export async function getRoleListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<RoleItem>>>('/role/page', { params });
}

export async function getAllRolesApi() {
  return request.get<ApiResponse<RoleItem[]>>('/role/list');
}

export async function getRoleDetailApi(id: number) {
  return request.get<ApiResponse<RoleItem>>(`/role/${id}`);
}

export async function createRoleApi(data: RoleFormParams) {
  return request.post<ApiResponse<null>>('/role', data);
}

export async function updateRoleApi(data: RoleFormParams) {
  return request.put<ApiResponse<null>>('/role', data);
}

export async function deleteRoleApi(id: number) {
  return request.delete<ApiResponse<null>>(`/role/${id}`);
}

export async function getRoleMenuIdsApi(roleId: number) {
  return request.get<ApiResponse<number[]>>(`/role/${roleId}/menus`);
}

// --- Menu ---
export async function getMenuTreeApi() {
  return request.get<ApiResponse<MenuTreeItem[]>>('/menu/tree');
}

export async function getMenuDetailApi(id: number) {
  return request.get<ApiResponse<MenuTreeItem>>(`/menu/${id}`);
}

export async function createMenuApi(data: MenuFormParams) {
  return request.post<ApiResponse<null>>('/menu', data);
}

export async function updateMenuApi(data: MenuFormParams) {
  return request.put<ApiResponse<null>>('/menu', data);
}

export async function deleteMenuApi(id: number) {
  return request.delete<ApiResponse<null>>(`/menu/${id}`);
}

// --- Log ---
export async function getLogListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<LogItem>>>('/system/log/page', { params });
}

export async function getLogDetailApi(id: number) {
  return request.get<ApiResponse<LogItem>>(`/system/log/${id}`);
}

// --- Dept ---
export async function getDeptTreeApi() {
  return request.get<ApiResponse<DeptItem[]>>('/dept/tree');
}

export async function getDeptDetailApi(id: number) {
  return request.get<ApiResponse<DeptItem>>(`/dept/${id}`);
}

export async function createDeptApi(data: DeptFormParams) {
  return request.post<ApiResponse<null>>('/dept', data);
}

export async function updateDeptApi(data: DeptFormParams) {
  return request.put<ApiResponse<null>>('/dept', data);
}

export async function deleteDeptApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dept/${id}`);
}

// --- Project ---
export async function getProjectTreeApi() {
  return request.get<ApiResponse<DeptItem[]>>('/project/tree');
}

export async function getProjectDetailApi(id: number) {
  return request.get<ApiResponse<ProjectItem>>(`/project/${id}`);
}

export async function createProjectApi(data: ProjectFormParams) {
  return request.post<ApiResponse<null>>('/project', data);
}

export async function updateProjectApi(data: ProjectFormParams) {
  return request.put<ApiResponse<null>>('/project', data);
}

export async function deleteProjectApi(id: number) {
  return request.delete<ApiResponse<null>>(`/project/${id}`);
}

// --- Template ---
// 契约说明（重要）：后端 rpt_template 的字段是
//   templateName / templateCode / fillingCycle / tableName / datasourceId / materialized
// 而早期前端页面用的是 name / code / version；字段表后端为
//   fieldName(中文名) / fieldCode(编码) / dbColumn(库列名) / isRequired
// 早期前端用 fieldLabel / required。此处**在 API 层做双向归一化**，
// 页面无需改动即可正确读写（读：补出 name/code/fieldLabel/required；写：还原成后端字段名）。
type AnyRec = Record<string, any>;

/** 后端模板 → 前端视图模型 */
export function normalizeTemplate(row: AnyRec): AnyRec {
  if (!row) return row;
  const out: AnyRec = { ...row };
  out.name = row.name ?? row.templateName;
  out.code = row.code ?? row.templateCode;
  out.templateName = row.templateName ?? row.name;
  out.templateCode = row.templateCode ?? row.code;
  out.excelFileName = row.excelFileName ?? row.fileName;
  if (Array.isArray(row.fields)) out.fields = row.fields.map(normalizeTemplateField);
  return out;
}

/** 后端字段 → 前端视图模型 */
export function normalizeTemplateField(f: AnyRec): AnyRec {
  if (!f) return f;
  const out: AnyRec = { ...f };
  out.fieldLabel = f.fieldLabel ?? f.fieldName;
  out.dbColumn = f.dbColumn ?? f.fieldCode;
  out.required = f.required ?? f.isRequired ?? 0;
  out.isRequired = f.isRequired ?? f.required ?? 0;
  return out;
}

/** 前端字段 → 后端字段 */
export function denormalizeTemplateField(f: TemplateSaveField): AnyRec {
  const out: AnyRec = {
    fieldName: f.fieldName ?? f.fieldLabel,
    fieldCode: f.fieldCode ?? f.dbColumn ?? f.fieldName,
    dbColumn: f.dbColumn ?? f.fieldCode ?? f.fieldName,
    fieldType: f.fieldType ?? 'VARCHAR',
    fieldLength: f.fieldLength ?? 255,
    isRequired: f.isRequired ?? f.required ?? 0,
    isKey: f.isKey ?? 0,
    excelColumn: f.excelColumn ?? '',
    sort: f.sort ?? 0,
  };
  if (f.id !== undefined) out.id = f.id;
  return out;
}

/** 前端模板 → 后端模板（兼容新旧两种命名） */
export function denormalizeTemplate(data: TemplateSaveParams): AnyRec {
  const out: AnyRec = {};
  if (data.id !== undefined) out.id = data.id;
  out.templateName = data.templateName ?? data.name;
  out.templateCode = data.templateCode ?? data.code;
  if (data.fillingCycle !== undefined) out.fillingCycle = data.fillingCycle;
  if (data.deptId !== undefined) out.deptId = data.deptId;
  if (data.projectId !== undefined) out.projectId = data.projectId;
  if (data.tableName !== undefined) out.tableName = data.tableName;
  if (data.datasourceId !== undefined) out.datasourceId = data.datasourceId;
  if (data.description !== undefined) out.description = data.description;
  if (data.status !== undefined) out.status = data.status;
  if (Array.isArray(data.fields)) out.fields = data.fields.map(denormalizeTemplateField);
  return out;
}

export interface TemplateSaveField {
  id?: number;
  fieldName?: string;
  fieldLabel?: string;
  fieldCode?: string;
  dbColumn?: string;
  fieldType?: string;
  fieldLength?: number;
  isRequired?: number;
  required?: number;
  isKey?: number;
  excelColumn?: string;
  sort?: number;
}

/** 新建/更新模板的入参：新旧命名都接受 */
export interface TemplateSaveParams {
  id?: number;
  templateName?: string;
  templateCode?: string;
  name?: string;
  code?: string;
  fillingCycle?: number;
  deptId?: number;
  projectId?: number;
  tableName?: string;
  datasourceId?: number;
  description?: string;
  status?: number;
  fields?: TemplateSaveField[];
}

export async function getTemplateListApi(params: PageParams) {
  const res = await request.get<ApiResponse<PageResult<TemplateItem>>>('/template/page', { params });
  const page = res.data.data as unknown as AnyRec;
  if (page && Array.isArray(page.list)) {
    page.list = page.list.map(normalizeTemplate);
  }
  return res;
}

export async function getTemplateDetailApi(id: number) {
  const res = await request.get<ApiResponse<TemplateItem>>(`/template/${id}`);
  const data = res.data.data as unknown as AnyRec;
  if (data) {
    res.data.data = normalizeTemplate(data) as unknown as TemplateItem;
  }
  return res;
}

export async function getTemplateFieldsApi(id: number) {
  const res = await request.get<ApiResponse<TemplateField[]>>(`/template/${id}/fields`);
  const list = res.data.data as unknown as AnyRec[];
  if (Array.isArray(list)) {
    res.data.data = list.map(normalizeTemplateField) as unknown as TemplateField[];
  }
  return res;
}

export async function createTemplateApi(data: TemplateSaveParams) {
  // 后端返回新建模板的主键 id（便于随后物化/下载）
  return request.post<ApiResponse<number>>('/template', denormalizeTemplate(data));
}

export async function updateTemplateApi(data: TemplateSaveParams) {
  // 后端是 PUT /template（id 在 body 里），不是 PUT /template/{id}
  return request.put<ApiResponse<null>>('/template', denormalizeTemplate(data));
}

export async function deleteTemplateApi(id: number) {
  return request.delete<ApiResponse<null>>(`/template/${id}`);
}

export async function updateTemplateStatusApi(id: number, status: number) {
  // 后端取 @RequestParam status，不是 body
  return request.put<ApiResponse<null>>(`/template/${id}/status`, {}, { params: { status } });
}

export async function saveTemplateFieldsApi(id: number, fields: TemplateSaveField[]) {
  return request.post<ApiResponse<null>>(`/template/${id}/fields`, fields.map(denormalizeTemplateField));
}

/** 解析 Excel 表头 → 推荐字段（5 步向导第 1 步） */
export async function parseTemplateExcelApi(file: File, headerRowIndex = 0) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('headerRowIndex', String(headerRowIndex));
  return request.post<ApiResponse<{ headers: string[]; fieldCount: number; fields: AnyRec[] }>>(
    '/template/parse-excel',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
}

/** 物化：按模板字段在目标业务库建表（5 步向导第 4/5 步） */
export async function materializeTemplateApi(id: number, recreate = false) {
  return request.post<ApiResponse<{ templateId: number; tableName: string; datasourceId: number }>>(
    `/template/${id}/materialize`,
    {},
    { params: { recreate } },
  );
}

/** 发布/部署模板（datasourceId / tableName 可选） */
export async function deployTemplateApi(id: number, datasourceId?: number, tableName?: string) {
  return request.post<ApiResponse<null>>(`/template/${id}/deploy`, {}, { params: { datasourceId, tableName } });
}

export async function downloadTemplateApi(id: number) {
  return request.get(`/template/${id}/download`, { responseType: 'blob' });
}

/** @deprecated 后端没有该端点（原前端调 /template/{id}/upload-excel 会 404）；请用 parseTemplateExcelApi */
export async function uploadTemplateExcelApi(_templateId: number, formData: FormData) {
  return request.post<ApiResponse<null>>('/template/parse-excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function submitTemplateApi(id: number) {
  // 后端没有 /submit，发布对应 POST /template/{id}/deploy
  return request.post<ApiResponse<null>>(`/template/${id}/deploy`);
}

export async function auditTemplateApi(id: number, status: number, remark?: string) {
  // 后端取 @RequestParam auditStatus / auditComment，且方法为 PUT
  return request.put<ApiResponse<null>>(`/template/${id}/audit`, {}, {
    params: { auditStatus: status, auditComment: remark },
  });
}

// --- Datasource ---
export async function getDatasourceListApi(params: PageParams) {
  const res = await request.get<ApiResponse<PageResult<DatasourceItem>>>('/datasource/page', { params });
  // 归一化：后端是 dsName/dsType，早期页面按 name/type 读 → 这里双向补全，避免下拉显示 undefined
  const page = res.data.data as unknown as Record<string, any>;
  if (page && Array.isArray(page.list)) {
    page.list = page.list.map((d: Record<string, any>) => ({
      ...d,
      name: d.name ?? d.dsName,
      type: d.type ?? d.dsType,
      dsName: d.dsName ?? d.name,
      dsType: d.dsType ?? d.type,
    }));
  }
  return res;
}

export async function getDatasourceDetailApi(id: number) {
  return request.get<ApiResponse<DatasourceItem>>(`/datasource/${id}`);
}

export async function createDatasourceApi(data: DatasourceFormParams) {
  return request.post<ApiResponse<null>>('/datasource', data);
}

export async function updateDatasourceApi(data: DatasourceFormParams) {
  return request.put<ApiResponse<null>>('/datasource', data);
}

export async function deleteDatasourceApi(id: number) {
  return request.delete<ApiResponse<null>>(`/datasource/${id}`);
}

export async function testDatasourceConnectionApi(data: DatasourceFormParams) {
  // 后端端点是 POST /datasource/test（@RequestParam），不是 /test-connection(JSON)
  const d = data as unknown as Record<string, any>;
  return request.post<ApiResponse<TestConnectionResult>>('/datasource/test', {}, {
    params: {
      ip: d.ip, port: d.port, dbName: d.dbName ?? d.database,
      username: d.username, password: d.password, dsType: d.dsType ?? d.type,
    },
  });
}

// --- Task ---

/**
 * 任务列表归一化。
 *
 * 背景（业务流转测试发现）：后端返回的是 rpt_task 实体字段
 *   { taskName, templateId, belongTime, fillDeadline, auditDeadline, fillingCycle, status }
 * 而前端 TaskItem 期望
 *   { name, templateName, year, period, startDate, endDate, status }
 * 之前只补了 name，导致：
 *   - 列表「模板/年度/期数/开始日期/结束日期」列全部显示 "-"
 *   - 操作列按 record.status 判断本身没问题，但编辑弹窗回填日期为空
 */
function normalizeTaskRow(x: Record<string, any>): Record<string, any> {
  const belongTime: string = x.belongTime ?? '';
  // belongTime 形如 "2026-10"，拆出年度与期数
  const yearFromBelong = belongTime && /^\d{4}/.test(belongTime)
    ? Number(belongTime.slice(0, 4))
    : undefined;
  const periodFromBelong = belongTime && belongTime.length > 4
    ? belongTime.slice(5)
    : undefined;
  return {
    ...x,
    name: x.name ?? x.taskName,
    taskName: x.taskName ?? x.name,
    year: x.year ?? yearFromBelong,
    period: x.period ?? periodFromBelong ?? '',
    // 后端只有 fillDeadline（填报截止），作为结束日期展示；
    // 开始日期后端无对应字段，保留接口返回（若未来新增则自动生效）
    startDate: x.startDate ?? x.startTime ?? '',
    endDate: x.endDate ?? (x.fillDeadline ? String(x.fillDeadline).slice(0, 10) : ''),
    templateName: x.templateName ?? (x.templateId != null ? `模板#${x.templateId}` : ''),
  };
}

export async function getTaskListApi(params: PageParams) {
  const res = await request.get<ApiResponse<PageResult<TaskItem>>>('/task/page', { params });
  const page = res.data.data as unknown as Record<string, any>;
  if (page && Array.isArray(page.list)) {
    page.list = page.list.map(normalizeTaskRow);
  }
  return res;
}

export async function getTaskDetailApi(id: number) {
  const res = await request.get<ApiResponse<TaskItem>>(`/task/${id}`);
  const data = res.data.data as unknown as Record<string, any>;
  if (data) res.data.data = normalizeTaskRow(data) as unknown as TaskItem;
  return res;
}

/**
 * 任务提交参数转换：前端表单字段 → 后端实体字段。
 *
 * 修复 P0（业务流转测试发现）：前端 TaskFormParams 是
 *   { name, year, period, startDate, endDate, templateId, description }
 * 后端 RptTask 需要
 *   { taskName, templateId, belongTime, fillDeadline, fillingCycle }
 * 字段名不一致导致 POST /task 返回 500、任务无法创建。
 * 这里做统一转换，避免把后端字段名泄漏到页面层。
 */
export function toTaskPayload(data: TaskFormParams): Record<string, any> {
  const belongTime =
    data.year != null
      ? (data.period ? `${data.year}-${data.period}` : String(data.year))
      : undefined;
  // 结束日期 → fillDeadline；LocalDateTime 需要 ISO 形式，补 23:59:59
  const fillDeadline = data.endDate
    ? (data.endDate.length === 10 ? `${data.endDate}T23:59:59` : data.endDate)
    : undefined;
  const payload: Record<string, any> = {
    taskName: data.name,
    templateId: data.templateId,
    belongTime,
    fillDeadline,
    description: data.description,
  };
  if (data.id != null) payload.id = data.id;
  if (data.status != null) payload.status = data.status;
  return payload;
}

export async function createTaskApi(data: TaskFormParams) {
  return request.post<ApiResponse<null>>('/task', toTaskPayload(data));
}

export async function updateTaskApi(data: TaskFormParams) {
  return request.put<ApiResponse<null>>('/task', toTaskPayload(data));
}

export async function deleteTaskApi(id: number) {
  return request.delete<ApiResponse<null>>(`/task/${id}`);
}

export async function publishTaskApi(id: number) {
  return request.put<ApiResponse<null>>(`/task/${id}/publish`);
}

export async function closeTaskApi(id: number) {
  return request.put<ApiResponse<null>>(`/task/${id}/close`);
}

// --- Task Assign ---
export async function getTaskAssignListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<TaskAssignItem>>>('/task/assign/page', { params });
}

export async function assignTaskApi(data: TaskAssignFormParams) {
  // 后端端点是 POST /task/{id}/assign（query 参数 deptIds/userIds/assignType）
  // 注意：
  // 1) assignType 是后端必填参数（没有 required=false），缺参直接 400，必须显式传 FILL/AUDIT；
  // 2) 后端用 @RequestParam List<Long> 接收，axios 默认会序列化成 deptIds[]=1&deptIds[]=2，
  //    Spring 无法绑定这种带方括号的参数名，这里改成逗号分隔（Spring 支持 String→List 转换）。
  const taskId = data.taskId;
  const params: Record<string, any> = {
    assignType: data.assignType,
  };
  if (data.deptIds && data.deptIds.length > 0) params.deptIds = data.deptIds.join(',');
  if (data.userIds && data.userIds.length > 0) params.userIds = data.userIds.join(',');
  return request.post<ApiResponse<null>>(`/task/${taskId}/assign`, {}, { params });
}

export async function cancelAssignApi(id: number) {
  return request.delete<ApiResponse<null>>(`/task/assign/${id}`);
}

// --- Task Ledger ---
export async function getTaskLedgerListApi(params: PageParams & { perspective?: 'host' | 'executor' }) {
  return request.get<ApiResponse<PageResult<TaskLedgerItem>>>('/ledger/hostTaskTotal', { params });
}

// --- Fill ---
export async function getFillTaskListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<FillTaskItem>>>('/fill/task/page', { params });
}

/**
 * 我被分派的任务（含从未填报过的）。
 *
 * 缺陷修复：「我的填报任务」页原先只查 /fill/task/page（rpt_task_data），
 * 从未保存过草稿的任务不会出现，新分派的任务在界面上完全没有入口（鸡生蛋问题）。
 */
export async function getMyAssignedTasksApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<AssignedTaskItem>>>('/fill/my-assigned', { params });
}

export async function getFillTaskDetailApi(taskId: number) {
  // 后端：GET /fill/task/{taskId}，按 taskId 查当前用户在该任务下的填报数据
  return request.get<ApiResponse<FillDetail>>(`/fill/task/${taskId}`);
}

/** 把参数对象转成 application/x-www-form-urlencoded 的 body（后端 @RequestParam 只认 query/form） */
function toFormBody(data: Record<string, any>): URLSearchParams {
  const body = new URLSearchParams();
  Object.keys(data).forEach((key) => {
    const value = data[key];
    if (value !== undefined && value !== null) body.append(key, String(value));
  });
  return body;
}

export async function submitFillDataApi(data: FillSubmitParams) {
  // 后端 POST /fill/submit：@RequestParam taskId / dataJson / fileUrl
  return request.post<ApiResponse<null>>('/fill/submit', toFormBody(data), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
}

/**
 * 把已审核通过的填报数据写回目标业务库（入库）。
 *
 * 界面上原先没有任何入口，只能手工调接口；台账页「入库」按钮走这里。
 */
export async function commitTaskDataApi(taskId: number, dataId?: number, force = false) {
  return request.post<ApiResponse<any>>(`/fill/${taskId}/commit`, null, {
    params: { dataId, force },
  });
}

export async function saveFillDraftApi(data: FillSubmitParams) {
  // 后端 POST /fill/save：参数同上
  return request.post<ApiResponse<null>>('/fill/save', toFormBody(data), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
}

/** 填报附件上传：后端 POST /fill/upload（multipart），返回 fileUrl */
export async function uploadFillFileApi(file: File, taskId?: number) {
  const fd = new FormData();
  fd.append('file', file);
  if (taskId != null) fd.append('taskId', String(taskId));
  return request.post<ApiResponse<FillUploadResult>>('/fill/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

// --- Audit ---
export async function getAuditListApi(params: PageParams & { auditType?: 'dept' | 'task' }) {
  return request.get<ApiResponse<PageResult<AuditItem>>>('/audit/myTasks', { params });
}

export async function getAuditDetailApi(id: number) {
  return request.get<ApiResponse<any>>(`/audit/task/${id}`);
}

export async function submitAuditApi(data: AuditFormParams) {
  // 后端端点为 POST /audit/dept-audit | /audit/task-audit（query: taskDataId/pass/comment）
  const d = data as unknown as Record<string, any>;
  const path = d.auditType === 'task' ? '/audit/task-audit' : '/audit/dept-audit';
  const taskDataId = d.taskDataId ?? d.dataId ?? d.id;
  const pass = d.pass ?? d.approved ?? (d.status === 1);
  return request.post<ApiResponse<null>>(path, {}, {
    params: { taskDataId, pass, comment: d.comment ?? d.auditComment },
  });
}

// --- Data Apply ---
export async function getDataApplyListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<DataApplyItem>>>('/dataApply/', { params });
}

export async function getDataApplyDetailApi(id: number) {
  return request.get<ApiResponse<DataApplyItem>>(`/dataApply/${id}`);
}

export async function createDataApplyApi(data: DataApplyFormParams) {
  // 后端已改为接收 JSON body（taskId/title/reason/dataScope），末尾不再带斜杠
  return request.post<ApiResponse<null>>('/dataApply', data);
}

export async function updateDataApplyApi(id: number, data: DataApplyFormParams) {
  return request.put<ApiResponse<null>>(`/dataApply/${id}`, data);
}

export async function deleteDataApplyApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dataApply/${id}`);
}

// Data Apply - Approval
export async function getApproveListApi(params: PageParams) {
  return request.get<ApiResponse<PageResult<DataApplyItem>>>('/dataApply/approveList', { params });
}

export async function submitApprovalApi(data: ApproveFormParams) {
  // 后端为 POST /dataApply/{id}/approve | /reject?auditComment=
  const d = data as unknown as Record<string, any>;
  const id = d.id ?? d.applyId;
  const rejected = d.pass === false || d.approved === false || d.status === 2;
  return rejected
    ? request.post<ApiResponse<null>>(`/dataApply/${id}/reject`, {}, { params: { auditComment: d.comment ?? d.auditComment } })
    : request.post<ApiResponse<null>>(`/dataApply/${id}/approve`);
}

// --- Overview ---
export async function getOverviewStatsApi() {
  return request.get<ApiResponse<OverviewStats>>('/overview/stats');
}

export async function getOverviewChartDataApi(type: string) {
  return request.get<ApiResponse<ChartDataItem[]>>('/overview/chart', { params: { type } });
}

export async function getOverviewTrendApi() {
  return request.get<ApiResponse<TrendDataItem[]>>('/overview/trend');
}

// ==================== P0-C 双通道审核 / 流转轨迹 / 补正 ====================

export interface AuditDataItem {
  id: number;
  taskId?: number;
  templateId?: number;
  deptId?: number;
  fillUserId?: number;
  dataJson?: string;
  status?: number;
  submitTime?: string;
  auditStatus?: number;
  auditComment?: string;
  auditTime?: string;
  deptAuditStatus?: number;
  deptAuditComment?: string;
  deptAuditTime?: string;
  taskAuditStatus?: number;
  taskAuditComment?: string;
  taskAuditTime?: string;
  correctStatus?: number;
  correctComment?: string;
  inboundStatus?: number;
  inboundRows?: number;
  // 关联展示字段
  deptName?: string;
  taskName?: string;
  templateName?: string;
  fillUserName?: string;
  fillRealName?: string;
  belongTime?: string;
  createTime?: string;
}

export interface TrajectoryNode {
  id?: number;
  node_code?: string;
  node_name?: string;
  action?: string;
  operator_name?: string;
  comment?: string;
  create_time?: string;
}

export interface CorrectBatch {
  id?: number;
  task_id?: number;
  data_ids?: string;
  status?: number;
  comment?: string;
  start_time?: string;
  close_time?: string;
  start_user_name?: string;
}

/** 我的审核任务列表（分页） */
export async function getAuditMyTasksApi(params: PageParams & { auditStatus?: number }) {
  return request.get<ApiResponse<PageResult<AuditDataItem>>>('/audit/myTasks', { params });
}

/** 审核详情：某任务下的填报数据列表 */
export async function getAuditTaskDataApi(taskId: number) {
  return request.get<ApiResponse<PageResult<AuditDataItem>>>(`/audit/task/${taskId}`);
}

/** 审核统计（待审/通过/驳回/合计） */
export async function getAuditStatisticsApi() {
  return request.get<ApiResponse<Record<string, number>>>('/audit/statistics');
}

/** 部门审核（第一级） */
export async function deptAuditApi(data: { taskDataId: number; pass: boolean; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/dept-audit', null, { params: data });
}

/** 任务方审核（第二级，需部门审核通过） */
export async function taskAuditApi(data: { taskDataId: number; pass: boolean; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/task-audit', null, { params: data });
}

/** 任务流转轨迹 */
export async function getTrajectoryApi(taskId: number) {
  return request.get<ApiResponse<TrajectoryNode[]>>('/audit/trajectory', { params: { taskId } });
}

/** 发起补正 */
export async function startCorrectApi(data: { taskId: number; dataIds?: string; comment?: string }) {
  return request.post<ApiResponse<number>>('/audit/correct/start', null, { params: data });
}

/** 关闭补正 */
export async function closeCorrectApi(data: { taskId: number; comment?: string }) {
  return request.post<ApiResponse<null>>('/audit/correct/close', null, { params: data });
}

/** 补正批次列表 */
export async function getCorrectListApi(taskId: number) {
  return request.get<ApiResponse<CorrectBatch[]>>('/audit/correct/list', { params: { taskId } });
}

// ==================== P1-E 任务台账 ====================

export interface LedgerTypeItem {
  type: string;
  label: string;
}

export interface LedgerRate {
  dueTask?: number;
  doneTask?: number;
  ontimeTask?: number;
  overdueTask?: number;
  completeRate?: number;
  ontimeRate?: number;
  overdueRate?: number;
  deptName?: string;
  [key: string]: any;
}

/** 台账视图类型（6 个） */
export async function getLedgerTypesApi() {
  return request.get<ApiResponse<LedgerTypeItem[]>>('/ledger/types');
}

/** 三率统计 */
export async function getLedgerRatesApi(params?: { deptId?: number; taskId?: number }) {
  return request.get<ApiResponse<LedgerRate>>('/ledger/rates', { params });
}

/** 台账视图（分页） */
export async function getLedgerViewApi(type: string, params: PageParams) {
  return request.get<ApiResponse<{ list: Record<string, any>[]; total: number; headers?: Record<string, string> }>>(
    `/ledger/${type}`, { params }
  );
}

/** 导出台账（xlsx blob） */
export async function exportLedgerApi(type: string, limit = 20000) {
  return request.get<Blob>('/ledger/export', { params: { type, limit }, responseType: 'blob' });
}

// ==================== P1-D 附件与分片上传 ====================

export interface FileItem {
  id: number;
  taskId?: number;
  dataId?: number;
  templateId?: number;
  fileName: string;
  storePath?: string;
  fileSize?: number;
  fileType?: string;
  contentType?: string;
  md5?: string;
  uploadBy?: number;
  uploadByName?: string;
  createTime?: string;
}

export interface ChunkCheckResult {
  fileKey: string;
  uploadedChunks: number[];
  skipUpload: boolean;
  fileId?: number;
}

/** 普通上传 */
export async function uploadFileApi(file: File, extra?: Record<string, any>) {
  const fd = new FormData();
  fd.append('file', file);
  Object.entries(extra || {}).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') fd.append(k, String(v));
  });
  return request.post<ApiResponse<FileItem>>('/file/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 分片检查（秒传 / 断点续传） */
export async function checkChunkApi(params: { fileKey: string; fileName?: string; md5?: string }) {
  return request.get<ApiResponse<ChunkCheckResult>>('/file/chunk/check', { params });
}

/** 上传单个分片 */
export async function uploadChunkApi(fileKey: string, chunkIndex: number, chunk: Blob) {
  const fd = new FormData();
  fd.append('fileKey', fileKey);
  fd.append('chunkIndex', String(chunkIndex));
  fd.append('file', chunk);
  return request.post<ApiResponse<{ received: number }>>('/file/chunk', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 合并分片 */
export async function mergeChunkApi(data: {
  fileKey: string; fileName: string; chunkTotal: number;
  taskId?: number; dataId?: number; fileType?: string;
}) {
  return request.post<ApiResponse<FileItem>>('/file/chunk/merge', null, { params: data });
}

/** 附件列表（按任务） */
export async function getFileListApi(taskId: number) {
  return request.get<ApiResponse<FileItem[]>>('/file/list', { params: { taskId } });
}

/** 删除附件（软删除） */
export async function deleteFileApi(id: number) {
  return request.delete<ApiResponse<null>>(`/file/${id}`);
}

/** 下载附件（blob，需带 token） */
export async function downloadFileApi(id: number) {
  return request.get<Blob>(`/file/${id}/download`, { responseType: 'blob' });
}

// ==================== P1-F / P2-G 待办 / 字典 / 数据范围 / 审计日志 ====================

export interface TodoSummary {
  /** 真实待办数 = 待填 + 待审 + 待批（不再是未读消息数） */
  todoCount?: number;
  fillTodo?: number;
  auditTodo?: number;
  applyTodo?: number;
  doneCount?: number;
  /** 未读站内消息数（顶栏红点用这个） */
  unreadCount?: number;
  readCount?: number;
  todayCount?: number;
  overdueCount?: number;
  [key: string]: any;
}

export interface TodoMessage {
  id: number;
  userId?: number;
  title: string;
  content?: string;
  msgType?: string;
  bizTaskId?: number;
  readFlag?: number;
  createBy?: number;
  createTime?: string;
}

/** 待办汇总 */
export async function getTodoSummaryApi() {
  return request.get<ApiResponse<TodoSummary>>('/todo/summary');
}

/** 我的消息（分页） */
export async function getTodoListApi(params: PageParams & { readFlag?: number; msgType?: string }) {
  return request.get<ApiResponse<{ list: TodoMessage[]; total: number }>>('/todo/list', { params });
}

/** 标记已读 */
export async function readTodoApi(id: number) {
  return request.post<ApiResponse<null>>(`/todo/${id}/read`);
}

/** 全部已读 */
export async function readAllTodoApi() {
  return request.post<ApiResponse<{ updated: number }>>('/todo/read-all');
}

/** 催办 */
export async function remindTodoApi(data: { taskId: number; comment?: string }) {
  return request.post<ApiResponse<Record<string, any>>>('/todo/remind', null, { params: data });
}

/** 手动触发超期扫描 */
export async function scanOverdueApi() {
  return request.post<ApiResponse<Record<string, any>>>('/todo/scan');
}

export interface DictItem {
  id: number;
  dictType: string;
  dictCode: string;
  dictLabel: string;
  sort?: number;
  status?: number;
  remark?: string;
}

/** 全部字典（按类型分组） */
export async function getDictAllApi() {
  return request.get<ApiResponse<Record<string, DictItem[]>>>('/dict/all');
}

/** 按类型查询（含停用，管理用） */
export async function getDictManageApi(dictType?: string) {
  return request.get<ApiResponse<DictItem[]>>(`/dict/manage/${dictType || ''}`);
}

export async function addDictApi(data: Partial<DictItem>) {
  return request.post<ApiResponse<number>>('/dict', data);
}

export async function updateDictApi(data: Partial<DictItem>) {
  return request.put<ApiResponse<null>>('/dict', data);
}

export async function deleteDictApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dict/${id}`);
}

export interface DataRangeConfig {
  id?: number;
  configName?: string;
  datasourceId?: number;
  sqlText?: string;
  remark?: string;
  status?: number;
  createTime?: string;
  [key: string]: any;
}

export interface DataRangeRunResult {
  sql?: string;
  columns?: string[];
  rows?: Record<string, any>[];
  rowCount?: number;
  costMs?: number;
}

export async function getDataRangeListApi() {
  return request.get<ApiResponse<DataRangeConfig[]>>('/dataRange/list');
}

export async function getDataRangeApi(id: number) {
  return request.get<ApiResponse<DataRangeConfig>>(`/dataRange/${id}`);
}

export async function saveDataRangeApi(data: DataRangeConfig) {
  return request.post<ApiResponse<number>>('/dataRange', data);
}

export async function updateDataRangeApi(data: DataRangeConfig) {
  return request.put<ApiResponse<number>>('/dataRange', data);
}

export async function deleteDataRangeApi(id: number) {
  return request.delete<ApiResponse<null>>(`/dataRange/${id}`);
}

/** 预览（执行配置里的 SQL） */
export async function previewDataRangeApi(id: number, deptId?: number) {
  return request.get<ApiResponse<DataRangeRunResult>>(`/dataRange/${id}/preview`, { params: { deptId } });
}

/** SQL 试跑（不落库） */
export async function runDataRangeSqlApi(data: {
  datasourceId?: number; sql: string; deptId?: number; limit?: number;
}) {
  return request.post<ApiResponse<DataRangeRunResult>>('/dataRange/run', data);
}

export interface OperLogItem {
  id: number;
  userId?: number;
  username?: string;
  module?: string;
  action?: string;
  target?: string;
  ip?: string;
  costMs?: number;
  status?: number;
  errorMsg?: string;
  createTime?: string;
}

export async function getOperLogPageApi(params: PageParams & {
  module?: string; action?: string; userId?: number; status?: number;
}) {
  return request.get<ApiResponse<{ list: OperLogItem[]; total: number }>>('/operLog/page', { params });
}

// ==================== 在线用户 / 异地登录 / 踢下线 ====================

export interface OnlineUserItem {
  id: number;
  userId?: number;
  username?: string;
  sessionId?: string;
  ip?: string;
  userAgent?: string;
  loginTime?: string;
  lastActiveTime?: string;
  status?: number;
  realName?: string;
  deptName?: string;
}

export async function getOnlineListApi(params?: { keyword?: string }) {
  return request.get<ApiResponse<OnlineUserItem[]>>('/online/list', { params });
}

export async function getOnlineCountApi() {
  return request.get<ApiResponse<{ online: number }>>('/online/count');
}

export async function kickOnlineApi(data: { sessionId: string; reason?: string }) {
  return request.post<ApiResponse<number>>('/online/kick', null, { params: data });
}

export async function kickOnlineUserApi(data: { userId: number; reason?: string }) {
  return request.post<ApiResponse<number>>('/online/kick-user', null, { params: data });
}

// ==================== 统计规则配置 ====================

export interface StatisticsRuleItem {
  id?: number;
  ruleCode: string;
  ruleName?: string;
  graceDays?: number;
  remindDays?: number;
  enabled?: number;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

export async function getStatisticsRuleListApi() {
  return request.get<ApiResponse<StatisticsRuleItem[]>>('/statistics-rule/list');
}

export async function getStatisticsRuleEffectiveApi() {
  return request.get<ApiResponse<Record<string, any>>>('/statistics-rule/effective');
}

export async function saveStatisticsRuleApi(data: StatisticsRuleItem) {
  return request.post<ApiResponse<number>>('/statistics-rule', data);
}

export async function deleteStatisticsRuleApi(id: number) {
  return request.delete<ApiResponse<null>>(`/statistics-rule/${id}`);
}

// ==================== 模板两级授权 ====================

export interface TemplateAuthItem {
  id: number;
  templateId?: number;
  authType?: string;
  targetId?: number;
  targetName?: string;
  createBy?: number;
  createTime?: string;
}

export async function getTemplateAuthsApi(templateId: number) {
  return request.get<ApiResponse<TemplateAuthItem[]>>(`/template/${templateId}/auths`);
}

export async function grantTemplateAuthApi(templateId: number, data: { authType: string; targetIds: number[] }) {
  return request.post<ApiResponse<number>>(`/template/${templateId}/auths`, data);
}

export async function revokeTemplateAuthApi(templateId: number, authId: number) {
  return request.delete<ApiResponse<null>>(`/template/${templateId}/auths/${authId}`);
}

// ==================== 分卷附件 ====================

export interface VolumeGroup {
  groupKey: string;
  partCount?: number;
  totalSize?: number;
  parts?: {
    volumeIndex: number; fileId: number; fileName: string;
    fileSize?: number; md5?: string; createTime?: string;
  }[];
}

export async function uploadVolumeApi(file: File, data: {
  groupKey: string; volumeIndex: number; taskId?: number; dataId?: number;
}) {
  const fd = new FormData();
  fd.append('file', file);
  fd.append('groupKey', data.groupKey);
  fd.append('volumeIndex', String(data.volumeIndex));
  if (data.taskId !== undefined) fd.append('taskId', String(data.taskId));
  if (data.dataId !== undefined) fd.append('dataId', String(data.dataId));
  return request.post<ApiResponse<Record<string, any>>>('/file/volume/upload', fd, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export async function getVolumeGroupsApi(taskId?: number) {
  return request.get<ApiResponse<VolumeGroup[]>>('/file/volume/list', { params: { taskId } });
}

export async function downloadVolumeGroupApi(groupKey: string) {
  return request.get<Blob>(`/file/volume/${groupKey}/download`, { responseType: 'blob' });
}

export async function deleteVolumeGroupApi(groupKey: string) {
  return request.delete<ApiResponse<number>>(`/file/volume/${groupKey}`);
}

// ==================== 质检通道 ====================

export async function submitQualityApi(taskDataId: number) {
  return request.post<ApiResponse<Record<string, any>>>(`/quality/${taskDataId}/submit`);
}

export async function getQualityApi(taskDataId: number) {
  return request.get<ApiResponse<Record<string, any>>>(`/quality/${taskDataId}`);
}

export async function getChannelStatusApi() {
  return request.get<ApiResponse<Record<string, any>>>('/quality/channel/status');
}

// ==================== 任务退回 / 无更新 / 参考数据（R-01 ~ R-05）====================

export async function returnTaskByAssignApi(taskId: number, reason: string) {
  return request.post<ApiResponse<void>>(`/task/${taskId}/return`, { reason });
}

export async function getReturnedTasksApi(params: PageParams = {}) {
  return request.get<ApiResponse<Record<string, any>>>('/task/returned', { params });
}

export async function handleReturnedTaskApi(taskId: number, action: 'revoke' | 'change-dept' | 'resubmit',
                                            params: { deptId?: number; reason?: string } = {}) {
  return request.put<ApiResponse<void>>(`/task/${taskId}/${action}`, params.reason ? { reason: params.reason } : {},
    { params: params.deptId ? { deptId: params.deptId } : {} });
}

export async function noUpdateSubmitApi(taskId: number, reason: string) {
  return request.post<ApiResponse<void>>('/fill/no-update', { taskId, reason });
}

export async function returnFillTaskApi(taskId: number, reason: string) {
  return request.post<ApiResponse<void>>(`/fill/${taskId}/return`, { reason });
}

export async function getTaskReferenceApi(taskId: number) {
  return request.get<ApiResponse<Record<string, any>>>(`/fill/${taskId}/reference`);
}

export async function getFillDeptApi() {
  return request.get<ApiResponse<any[]>>('/dept/fill-list');
}

export async function setFillDeptApi(deptIds: number[]) {
  return request.put<ApiResponse<void>>('/dept/fill-dept', { deptIds });
}

export async function publishProjectApi(id: number) {
  return request.put<ApiResponse<void>>(`/project/${id}/publish`);
}

export async function getTaskMakeStatsApi() {
  return request.get<ApiResponse<Record<string, number>>>('/statistics/task-make');
}

export async function getDeptFinishRankApi() {
  return request.get<ApiResponse<any[]>>('/statistics/dept-finish-rank');
}

export async function getFillTaskStatsApi() {
  return request.get<ApiResponse<Record<string, number>>>('/statistics/fill-task');
}

export async function getDeptIssueRankApi() {
  return request.get<ApiResponse<any[]>>('/statistics/dept-issue-rank');
}

export async function getCorrectItemsApi(correctId: number) {
  return request.get<ApiResponse<any[]>>(`/correct/${correctId}/items`);
}

export async function addCorrectItemApi(correctId: number, data: Record<string, any>) {
  return request.post<ApiResponse<void>>(`/correct/${correctId}/item`, data);
}

export async function deleteCorrectItemApi(itemId: number) {
  return request.delete<ApiResponse<void>>(`/correct/item/${itemId}`);
}

// ==================== 模板展示信息配置（R-01）====================
export async function getDisplayConfigApi(templateId: number) {
  return request.get<ApiResponse<Record<string, any>>>(`/template/${templateId}/display-config`);
}

export async function saveDisplayConfigApi(templateId: number, data: Record<string, any>) {
  return request.post<ApiResponse<void>>(`/template/${templateId}/display-config`, data);
}

export async function displayLinkTestApi(datasourceId: number, tableName: string) {
  return request.post<ApiResponse<Record<string, any>>>('/template/display/link-test', { datasourceId, tableName });
}

// ==================== 关联已有表（R-08）====================
export async function getDatasourceTablesApi(datasourceId: number) {
  return request.get<ApiResponse<string[]>>(`/datasource/${datasourceId}/tables`);
}

export async function getDatasourceColumnsApi(datasourceId: number, table: string) {
  return request.get<ApiResponse<Record<string, any>[]>>(`/datasource/${datasourceId}/tables/${encodeURIComponent(table)}/columns`);
}

export async function bindTemplateColumnsApi(templateId: number, binds: Record<string, any>[]) {
  return request.post<ApiResponse<void>>(`/template/${templateId}/bind-columns`, binds);
}
