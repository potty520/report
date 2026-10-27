package com.reportsys.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** R-10 填报部门标记 / R-15 项目发布与引用校验。 */
@Service
public class OrgFeatureService {

    @Autowired
    private JdbcTemplate jdbc;

    /** 批量设置填报部门（先清零再置 1） */
    @Transactional
    public void setFillDept(List<Long> deptIds) {
        jdbc.update("UPDATE sys_dept SET is_fill_dept = 0");
        if (deptIds != null) {
            for (Long id : deptIds) {
                jdbc.update("UPDATE sys_dept SET is_fill_dept = 1 WHERE id = ?", id);
            }
        }
    }

    public List<Map<String, Object>> listFillDept() {
        return jdbc.queryForList(
                "SELECT id, dept_name AS deptName, parent_id AS parentId, is_fill_dept AS isFillDept "
                        + "FROM sys_dept WHERE is_fill_dept = 1 ORDER BY sort, id");
    }

    /** R-15：发布（仅停用状态可发布） */
    public void publishProject(Long id) {
        Map<String, Object> p = loadProject(id);
        int status = p.get("status") == null ? 1 : ((Number) p.get("status")).intValue();
        if (status == 1) throw new IllegalStateException("项目已是启用状态");
        jdbc.update("UPDATE sys_project SET status = 1 WHERE id = ?", id);
    }

    /** R-15：关闭前校验引用 */
    public void closeProject(Long id) {
        loadProject(id);
        Long templates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rpt_template WHERE project_id = ?", Long.class, id);
        Long tasks = jdbc.queryForObject("SELECT COUNT(*) FROM rpt_task WHERE project_id = ?", Long.class, id);
        if ((templates != null && templates > 0) || (tasks != null && tasks > 0)) {
            throw new IllegalStateException("项目已被模板(" + templates + ")/任务(" + tasks + ")引用，不能关闭");
        }
        jdbc.update("UPDATE sys_project SET status = 0 WHERE id = ?", id);
    }

    /** R-15：删除前校验子项目与引用 */
    public void deleteProject(Long id) {
        loadProject(id);
        Long children = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_project WHERE parent_id = ?", Long.class, id);
        if (children != null && children > 0) throw new IllegalStateException("该文件夹下还有项目，不能删除");
        Long templates = jdbc.queryForObject(
                "SELECT COUNT(*) FROM rpt_template WHERE project_id = ?", Long.class, id);
        if (templates != null && templates > 0) throw new IllegalStateException("项目已被模板引用，不能删除");
        jdbc.update("DELETE FROM sys_project WHERE id = ?", id);
    }

    private Map<String, Object> loadProject(Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_name, status, parent_id FROM sys_project WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalStateException("项目不存在");
        return rows.get(0);
    }
}
