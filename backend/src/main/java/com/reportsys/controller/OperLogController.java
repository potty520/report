package com.reportsys.controller;

import cn.hutool.core.util.StrUtil;
import com.reportsys.common.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作审计日志查询（P2-G）。
 */
@RestController
@RequestMapping("/operLog")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OperLogController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public Result<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String module,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) Integer status) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (StrUtil.isNotBlank(module)) {
            where.append(" AND module LIKE ? ");
            args.add("%" + module + "%");
        }
        if (StrUtil.isNotBlank(action)) {
            where.append(" AND action LIKE ? ");
            args.add("%" + action + "%");
        }
        if (userId != null) {
            where.append(" AND user_id = ? ");
            args.add(userId);
        }
        if (status != null) {
            where.append(" AND status = ? ");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rpt_oper_log" + where,
                Long.class, args.toArray());
        int offset = Math.max(0, (page - 1) * pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, user_id AS userId, username, module, action, target, ip, cost_ms AS costMs, "
                        + "status, error_msg AS errorMsg, create_time AS createTime FROM rpt_oper_log"
                        + where + " ORDER BY id DESC LIMIT " + pageSize + " OFFSET " + offset,
                args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("list", rows);
        return Result.success(result);
    }
}
