package com.reportsys.service;

import cn.hutool.core.util.StrUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务台账与统计（P1-E，对齐原版 6 张台账视图 + 完成率/按时率/超期率）。
 *
 * 视图类型：
 *   hostTaskTotal  制定总分（按任务）
 *   fillTask       填报任务（按填报数据）
 *   deptInvolved   涉及部门
 *   execTotal      执行总分（按部门，含三率）
 *   fillDetail     实填明细（任务×部门 已填/未填）
 *   deptAssign     下发部门（分派明细）
 */
@Service
public class ReportStatsService {

    public static final List<String> TYPES = java.util.Arrays.asList(
            "hostTaskTotal", "fillTask", "deptInvolved", "execTotal", "fillDetail", "deptAssign");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public boolean isSupported(String type) {
        return TYPES.contains(type);
    }

    /** 视图表头 */
    public LinkedHashMap<String, String> headers(String type) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        switch (type) {
            case "hostTaskTotal":
                map.put("id", "任务ID");
                map.put("taskName", "任务名称");
                map.put("templateName", "模板");
                map.put("deptName", "制定部门");
                map.put("belongTime", "归属时间");
                map.put("statusText", "任务状态");
                map.put("assignCount", "分派数");
                map.put("submittedCount", "已提交");
                map.put("auditedCount", "审核通过");
                map.put("tableName", "物化表");
                break;
            case "fillTask":
                map.put("id", "数据ID");
                map.put("taskName", "任务名称");
                map.put("templateName", "模板");
                map.put("fillUserName", "填报人");
                map.put("deptName", "部门");
                map.put("statusText", "填报状态");
                map.put("auditText", "审核状态");
                map.put("submitTime", "提交时间");
                map.put("inboundText", "入库状态");
                map.put("inboundRows", "入库行数");
                break;
            case "deptInvolved":
                map.put("deptId", "部门ID");
                map.put("deptName", "部门名称");
                map.put("hostTaskCount", "制定任务数");
                map.put("assignTaskCount", "被分派任务数");
                map.put("submittedCount", "已提交数据");
                break;
            case "execTotal":
                map.put("deptId", "部门ID");
                map.put("deptName", "部门名称");
                map.put("dueTask", "应完成");
                map.put("doneTask", "已完成");
                map.put("ontimeTask", "按时完成");
                map.put("overdueTask", "超期未完成");
                map.put("completeRate", "完成率(%)");
                map.put("ontimeRate", "按时率(%)");
                map.put("overdueRate", "超期率(%)");
                break;
            case "fillDetail":
                map.put("taskId", "任务ID");
                map.put("taskName", "任务名称");
                map.put("deptName", "部门");
                map.put("assignType", "分派类型");
                map.put("fillState", "填报状态");
                map.put("fillDeadline", "填报截止");
                break;
            default: // deptAssign
                map.put("id", "分派ID");
                map.put("taskId", "任务ID");
                map.put("taskName", "任务名称");
                map.put("deptName", "部门");
                map.put("assignUserName", "被分派人");
                map.put("assignType", "分派类型");
                map.put("statusText", "分派状态");
                map.put("createTime", "分派时间");
                break;
        }
        return map;
    }

    /** 分页查询台账视图 */
    public Map<String, Object> view(String type, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headers", headers(type));
        if (!isSupported(type)) {
            result.put("list", new ArrayList<>());
            result.put("total", 0);
            return result;
        }
        String sql = baseSql(type);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + sql + ") cnt", Long.class);
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql + " LIMIT " + pageSize + " OFFSET " + offset);
        decorate(type, rows);
        result.put("list", rows);
        result.put("total", total == null ? 0 : total);
        return result;
    }

    /** 导出用：取全量（上限保护） */
    public List<Map<String, Object>> all(String type, int limit) {
        String sql = baseSql(type);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql + " LIMIT " + limit);
        decorate(type, rows);
        return rows;
    }

    /** 完成率/按时率/超期率（可按部门、任务过滤） */
    public Map<String, Object> rates(Long deptId, Long taskId) {
        StringBuilder filter = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (deptId != null) {
            filter.append(" AND t.dept_id = ? ");
            args.add(deptId);
        }
        if (taskId != null) {
            filter.append(" AND t.id = ? ");
            args.add(taskId);
        }
        String cond = filter.toString();

        Long due = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rpt_task t " + cond + " AND t.status IN (1,2,3)", Long.class, args.toArray());
        Long done = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT d.task_id) FROM rpt_task_data d JOIN rpt_task t ON t.id = d.task_id "
                        + cond + " AND d.status = 1", Long.class, args.toArray());
        Long ontime = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT d.task_id) FROM rpt_task_data d JOIN rpt_task t ON t.id = d.task_id "
                        + cond + " AND d.status = 1 AND (t.fill_deadline IS NULL OR d.submit_time <= t.fill_deadline)",
                Long.class, args.toArray());
        Long overdue = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rpt_task t " + cond
                        + " AND t.status IN (1,2) AND t.fill_deadline IS NOT NULL AND t.fill_deadline < NOW() "
                        + "AND NOT EXISTS (SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id AND d.status = 1)",
                Long.class, args.toArray());

        long dueL = due == null ? 0 : due;
        long doneL = done == null ? 0 : done;
        long ontimeL = ontime == null ? 0 : ontime;
        long overdueL = overdue == null ? 0 : overdue;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dueTask", dueL);
        result.put("doneTask", doneL);
        result.put("ontimeTask", ontimeL);
        result.put("overdueTask", overdueL);
        result.put("completeRate", rate(doneL, dueL));
        result.put("ontimeRate", rate(ontimeL, doneL));
        result.put("overdueRate", rate(overdueL, dueL));
        return result;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) return 0.0;
        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }

    private String baseSql(String type) {
        switch (type) {
            case "hostTaskTotal":
                return "SELECT t.id AS id, t.task_name AS taskName, tp.template_name AS templateName, "
                        + "d.dept_name AS deptName, t.belong_time AS belongTime, t.status AS status, "
                        + "(SELECT COUNT(*) FROM rpt_task_assign a WHERE a.task_id = t.id AND a.assign_type = 'FILL') AS assignCount, "
                        + "(SELECT COUNT(*) FROM rpt_task_data x WHERE x.task_id = t.id AND x.status = 1) AS submittedCount, "
                        + "(SELECT COUNT(*) FROM rpt_task_data x WHERE x.task_id = t.id AND x.audit_status = 1) AS auditedCount, "
                        + "tp.table_name AS tableName "
                        + "FROM rpt_task t LEFT JOIN rpt_template tp ON tp.id = t.template_id "
                        + "LEFT JOIN sys_dept d ON d.id = t.dept_id ORDER BY t.id DESC";
            case "fillTask":
                return "SELECT d.id AS id, t.task_name AS taskName, tp.template_name AS templateName, "
                        + "u.real_name AS fillUserName, dept.dept_name AS deptName, d.status AS status, "
                        + "d.audit_status AS auditStatus, d.submit_time AS submitTime, "
                        + "d.inbound_status AS inboundStatus, d.inbound_rows AS inboundRows "
                        + "FROM rpt_task_data d JOIN rpt_task t ON t.id = d.task_id "
                        + "LEFT JOIN rpt_template tp ON tp.id = d.template_id "
                        + "LEFT JOIN sys_user u ON u.id = d.fill_user_id "
                        + "LEFT JOIN sys_dept dept ON dept.id = u.dept_id ORDER BY d.id DESC";
            case "deptInvolved":
                return "SELECT dept.id AS deptId, dept.dept_name AS deptName, "
                        + "(SELECT COUNT(*) FROM rpt_task t WHERE t.dept_id = dept.id) AS hostTaskCount, "
                        + "(SELECT COUNT(DISTINCT a.task_id) FROM rpt_task_assign a WHERE a.dept_id = dept.id) AS assignTaskCount, "
                        + "(SELECT COUNT(*) FROM rpt_task_data x JOIN sys_user u ON u.id = x.fill_user_id "
                        + " WHERE u.dept_id = dept.id AND x.status = 1) AS submittedCount "
                        + "FROM sys_dept dept ORDER BY dept.id";
            case "execTotal":
                return "SELECT dept.id AS deptId, dept.dept_name AS deptName, "
                        + "(SELECT COUNT(*) FROM rpt_task t WHERE t.dept_id = dept.id AND t.status IN (1,2,3)) AS dueTask, "
                        + "(SELECT COUNT(DISTINCT x.task_id) FROM rpt_task_data x JOIN rpt_task t ON t.id = x.task_id "
                        + " WHERE t.dept_id = dept.id AND x.status = 1) AS doneTask, "
                        + "(SELECT COUNT(DISTINCT x.task_id) FROM rpt_task_data x JOIN rpt_task t ON t.id = x.task_id "
                        + " WHERE t.dept_id = dept.id AND x.status = 1 AND (t.fill_deadline IS NULL OR x.submit_time <= t.fill_deadline)) AS ontimeTask, "
                        + "(SELECT COUNT(*) FROM rpt_task t WHERE t.dept_id = dept.id AND t.status IN (1,2) "
                        + " AND t.fill_deadline IS NOT NULL AND t.fill_deadline < NOW() "
                        + " AND NOT EXISTS (SELECT 1 FROM rpt_task_data x WHERE x.task_id = t.id AND x.status = 1)) AS overdueTask "
                        + "FROM sys_dept dept ORDER BY dept.id";
            case "fillDetail":
                return "SELECT t.id AS taskId, t.task_name AS taskName, dept.dept_name AS deptName, "
                        + "a.assign_type AS assignType, t.fill_deadline AS fillDeadline, "
                        + "CASE WHEN EXISTS (SELECT 1 FROM rpt_task_data x JOIN sys_user u ON u.id = x.fill_user_id "
                        + "  WHERE x.task_id = t.id AND u.dept_id = a.dept_id AND x.status = 1) THEN '已填' ELSE '未填' END AS fillState "
                        + "FROM rpt_task_assign a JOIN rpt_task t ON t.id = a.task_id "
                        + "LEFT JOIN sys_dept dept ON dept.id = a.dept_id ORDER BY t.id DESC, a.id";
            default: // deptAssign
                return "SELECT a.id AS id, a.task_id AS taskId, t.task_name AS taskName, dept.dept_name AS deptName, "
                        + "u.real_name AS assignUserName, a.assign_type AS assignType, a.status AS status, "
                        + "a.create_time AS createTime "
                        + "FROM rpt_task_assign a LEFT JOIN rpt_task t ON t.id = a.task_id "
                        + "LEFT JOIN sys_dept dept ON dept.id = a.dept_id "
                        + "LEFT JOIN sys_user u ON u.id = a.user_id ORDER BY a.id DESC";
        }
    }

    /** 状态码转中文、计算比率等后处理 */
    private void decorate(String type, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            switch (type) {
                case "hostTaskTotal":
                    row.put("statusText", taskStatus(row.get("status")));
                    break;
                case "fillTask":
                    row.put("statusText", dataStatus(row.get("status")));
                    row.put("auditText", auditStatus(row.get("auditStatus")));
                    row.put("inboundText", inboundStatus(row.get("inboundStatus")));
                    break;
                case "execTotal":
                    long due = toLong(row.get("dueTask"));
                    long done = toLong(row.get("doneTask"));
                    long ontime = toLong(row.get("ontimeTask"));
                    long overdue = toLong(row.get("overdueTask"));
                    row.put("completeRate", rate(done, due));
                    row.put("ontimeRate", rate(ontime, done));
                    row.put("overdueRate", rate(overdue, due));
                    break;
                case "deptAssign":
                    row.put("statusText", toLong(row.get("status")) == 1 ? "已完成" : "待处理");
                    break;
                default:
                    break;
            }
        }
    }

    private String taskStatus(Object status) {
        switch ((int) toLong(status)) {
            case 0: return "草稿";
            case 1: return "已发布";
            case 2: return "填报中";
            case 3: return "已完成";
            case 4: return "已关闭";
            default: return "未知";
        }
    }

    private String dataStatus(Object status) {
        return toLong(status) == 1 ? "已提交" : "草稿";
    }

    private String auditStatus(Object status) {
        switch ((int) toLong(status)) {
            case 1: return "已通过";
            case 2: return "已驳回";
            default: return "待审核";
        }
    }

    private String inboundStatus(Object status) {
        switch ((int) toLong(status)) {
            case 1: return "已入库";
            case 2: return "入库失败";
            default: return "未入库";
        }
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 视图类型的中文名（导出文件名用） */
    public String typeLabel(String type) {
        if (StrUtil.isBlank(type)) return "台账";
        switch (type) {
            case "hostTaskTotal": return "制定总分";
            case "fillTask": return "填报任务";
            case "deptInvolved": return "涉及部门";
            case "execTotal": return "执行总分";
            case "fillDetail": return "实填明细";
            case "deptAssign": return "下发部门";
            default: return type;
        }
    }
}
