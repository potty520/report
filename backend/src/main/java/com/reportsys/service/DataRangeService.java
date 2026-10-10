package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.reportsys.config.TargetDataSourceManager;
import com.reportsys.dao.RptDataRangeConfigMapper;
import com.reportsys.dao.RptDatasourceMapper;
import com.reportsys.model.RptDataRangeConfig;
import com.reportsys.model.RptDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据范围 SQL 配置（P2-G，对齐原版 data_range_config：管理员配置 SQL + 部门变量，用于限定填报人可见数据）。
 *
 * 安全约束：只允许单条 SELECT；禁止多语句与写操作关键字；变量 Para_DepID / Para_UserId 白名单替换；
 * 强制行数上限与查询超时。
 */
@Service
public class DataRangeService {

    private static final Logger log = LoggerFactory.getLogger(DataRangeService.class);

    private static final List<String> FORBIDDEN = Arrays.asList(
            "insert ", "update ", "delete ", "drop ", "alter ", "truncate ", "create ", "grant ", "revoke ",
            "merge ", "call ", "execute ", "into outfile", "load_file", "benchmark(", "sleep(");

    @Autowired
    private RptDataRangeConfigMapper configMapper;
    @Autowired
    private RptDatasourceMapper datasourceMapper;
    @Autowired
    private TargetDataSourceManager dataSourceManager;

    @Value("${report.data-range.max-rows:200}")
    private int maxRows;

    @Value("${report.data-range.timeout-seconds:30}")
    private int timeoutSeconds;

    public List<RptDataRangeConfig> list() {
        return configMapper.selectList(Wrappers.<RptDataRangeConfig>lambdaQuery()
                .orderByDesc(RptDataRangeConfig::getId));
    }

    public RptDataRangeConfig getById(Long id) {
        return configMapper.selectById(id);
    }

    @Transactional
    public Long save(RptDataRangeConfig config, Long userId) {
        if (StrUtil.isBlank(config.getConfigName())) throw new RuntimeException("配置名称不能为空");
        if (StrUtil.isBlank(config.getSqlText())) throw new RuntimeException("SQL 不能为空");
        assertSelectOnly(config.getSqlText());
        if (config.getDatasourceId() == null) throw new RuntimeException("请先选择数据源");
        if (config.getId() == null) {
            config.setCreateBy(userId);
            config.setStatus(config.getStatus() == null ? 1 : config.getStatus());
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config.getId();
    }

    @Transactional
    public void delete(Long id) {
        configMapper.deleteById(id);
    }

    /** 预览：执行配置里的 SQL（替换部门变量） */
    public Map<String, Object> preview(Long id, Long deptId, Long userId) {
        RptDataRangeConfig config = configMapper.selectById(id);
        if (config == null) throw new RuntimeException("配置不存在");
        return runSql(config.getDatasourceId(), config.getSqlText(), deptId, userId, maxRows);
    }

    /** 直接执行一段 SQL（供"SQL 试跑"使用） */
    public Map<String, Object> runSql(Long datasourceId, String sql, Long deptId, Long userId, int limit) {
        if (datasourceId == null) throw new RuntimeException("请先选择数据源");
        RptDatasource ds = datasourceMapper.selectById(datasourceId);
        if (ds == null) throw new RuntimeException("数据源不存在");

        String prepared = prepareSql(sql, deptId, userId, limit);
        RptDatasource connectDs = dataSourceManager == null ? ds : decrypt(ds);
        int rowLimit = limit <= 0 ? maxRows : Math.min(limit, maxRows);

        long start = System.currentTimeMillis();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        try (Connection conn = dataSourceManager.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(prepared)) {
            ps.setQueryTimeout(timeoutSeconds);
            ps.setMaxRows(rowLimit);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int count = meta.getColumnCount();
                for (int i = 1; i <= count; i++) columns.add(meta.getColumnLabel(i));
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= count; i++) row.put(meta.getColumnLabel(i), rs.getObject(i));
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("SQL 执行失败: " + e.getMessage(), e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", prepared);
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("rowCount", rows.size());
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("limit", rowLimit);
        log.info("数据范围 SQL 试跑: dsId={} rows={} cost={}ms", datasourceId, rows.size(),
                System.currentTimeMillis() - start);
        return result;
    }

    /** 校验 + 变量替换 + 追加行数限制 */
    public String prepareSql(String sql, Long deptId, Long userId, int limit) {
        assertSelectOnly(sql);
        String prepared = sql.replace("Para_DepID", String.valueOf(deptId == null ? 0L : deptId))
                .replace("Para_UserId", String.valueOf(userId == null ? 0L : userId));
        String lower = prepared.toLowerCase(Locale.ROOT);
        int rowLimit = limit <= 0 ? maxRows : Math.min(limit, maxRows);
        if (!lower.contains(" limit ") && !lower.contains(" rownum")) {
            prepared = "SELECT * FROM (" + prepared + ") dr_limit LIMIT " + rowLimit;
        }
        return prepared;
    }

    /** 只允许单条 SELECT */
    public void assertSelectOnly(String sql) {
        if (StrUtil.isBlank(sql)) throw new RuntimeException("SQL 不能为空");
        String trimmed = sql.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("select")) {
            throw new RuntimeException("只允许执行 SELECT 查询");
        }
        for (String bad : FORBIDDEN) {
            if (lower.contains(bad)) {
                throw new RuntimeException("SQL 中包含不允许的关键字: " + bad.trim());
            }
        }
        String withoutTrailing = trimmed.replaceAll(";\\s*$", "");
        if (withoutTrailing.contains(";")) {
            throw new RuntimeException("不允许多条语句（含分号）");
        }
    }

    private RptDatasource decrypt(RptDatasource ds) {
        return ds;
    }
}
