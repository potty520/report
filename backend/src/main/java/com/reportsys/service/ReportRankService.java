package com.reportsys.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R-12 手册四类统计：任务制定统计 / 部门完成排行 / 填报任务统计 / 部门下发排行。 */
@Service
public class ReportRankService {

    @Autowired
    private JdbcTemplate jdbc;

    public Map<String, Object> taskMake(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskCount", count("SELECT COUNT(*) FROM rpt_task WHERE create_by = ?", userId));
        m.put("subTaskCount", count("SELECT COUNT(*) FROM rpt_task WHERE parent_task_id IS NOT NULL"));
        m.put("deptCount", count("SELECT COUNT(DISTINCT dept_id) FROM rpt_task WHERE create_by = ?", userId));
        m.put("doneCount", count("SELECT COUNT(*) FROM rpt_task t WHERE t.create_by = ? AND t.status = 3", userId));
        m.put("todoCount", count("SELECT COUNT(*) FROM rpt_task t WHERE t.create_by = ? AND t.status IN (1,2)", userId));
        m.put("overdueCount", count("SELECT COUNT(*) FROM rpt_task t WHERE t.create_by = ? AND t.status IN (1,2) "
                + "AND t.fill_deadline IS NOT NULL AND t.fill_deadline < NOW()", userId));
        return m;
    }

    public List<Map<String, Object>> deptFinishRank() {
        return jdbc.queryForList(
                "SELECT d.dept_name AS deptName, COUNT(t.id) AS total, "
                        + "SUM(CASE WHEN t.status = 3 THEN 1 ELSE 0 END) AS finished "
                        + "FROM rpt_task t LEFT JOIN sys_dept d ON d.id = t.dept_id "
                        + "GROUP BY t.dept_id, d.dept_name ORDER BY finished DESC, total DESC LIMIT 20");
    }

    public Map<String, Object> fillTask(Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("execCount", count("SELECT COUNT(DISTINCT t.id) FROM rpt_task t JOIN rpt_task_assign a "
                + "ON a.task_id = t.id AND a.user_id = ?", userId));
        m.put("deptCount", count("SELECT COUNT(DISTINCT t.dept_id) FROM rpt_task t JOIN rpt_task_assign a "
                + "ON a.task_id = t.id AND a.user_id = ?", userId));
        m.put("doneCount", count("SELECT COUNT(DISTINCT d.task_id) FROM rpt_task_data d WHERE d.fill_user_id = ? "
                + "AND d.status = 1", userId));
        m.put("todoCount", count("SELECT COUNT(DISTINCT t.id) FROM rpt_task t JOIN rpt_task_assign a "
                + "ON a.task_id = t.id AND a.user_id = ? WHERE t.status IN (1,2) AND NOT EXISTS ("
                + "SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id AND d.fill_user_id = ? AND d.status = 1)",
                userId, userId));
        m.put("overdueCount", count("SELECT COUNT(DISTINCT t.id) FROM rpt_task t JOIN rpt_task_assign a "
                + "ON a.task_id = t.id AND a.user_id = ? WHERE t.status IN (1,2) AND t.fill_deadline < NOW() "
                + "AND NOT EXISTS (SELECT 1 FROM rpt_task_data d WHERE d.task_id = t.id AND d.fill_user_id = ? "
                + "AND d.status = 1)", userId, userId));
        return m;
    }

    public List<Map<String, Object>> deptIssueRank() {
        return jdbc.queryForList(
                "SELECT d.dept_name AS deptName, COUNT(a.id) AS issued, "
                        + "SUM(CASE WHEN t.status = 3 THEN 1 ELSE 0 END) AS finished "
                        + "FROM rpt_task_assign a LEFT JOIN rpt_task t ON t.id = a.task_id "
                        + "LEFT JOIN sys_dept d ON d.id = a.dept_id WHERE a.assign_type = 'FILL' "
                        + "GROUP BY a.dept_id, d.dept_name ORDER BY issued DESC LIMIT 20");
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
