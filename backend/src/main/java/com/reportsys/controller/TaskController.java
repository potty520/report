package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTask;
import com.reportsys.model.RptTaskAssign;
import com.reportsys.service.RptTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private RptTaskService rptTaskService;

    /**
     * 分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<RptTask>> listPage(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String taskName,
                                                 @RequestParam(required = false) Integer status,
                                                 @RequestParam(required = false) String belongTime,
                                                 @RequestParam(required = false) Long deptId,
                                                 @RequestParam(required = false) Long projectId) {
        try {
            PageResult<RptTask> result = rptTaskService.getTaskList(page, pageSize, taskName, status,
                    belongTime, deptId, projectId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 制定任务
     */
    @PostMapping
    public Result<Void> create(@RequestBody RptTask task) {
        try {
            Long userId = getCurrentUserId();
            if (userId != null) {
                task.setCreateBy(userId);
            }
            rptTaskService.createTask(task);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新任务
     */
    @PutMapping
    public Result<Void> update(@RequestBody RptTask task) {
        try {
            RptTask exist = rptTaskService.getById(task.getId());
            if (exist == null) {
                return Result.error("任务不存在");
            }
            // 保留原创建信息
            task.setCreateBy(exist.getCreateBy());
            task.setCreateTime(exist.getCreateTime());
            // 重新创建(Service的createTask)
            rptTaskService.createTask(task);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptTaskService.deleteById(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 发布任务
     */
    @PutMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        try {
            rptTaskService.publishTask(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 关闭任务
     */
    @PutMapping("/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        try {
            rptTaskService.updateStatus(id, 4);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 分派任务
     */
    @PostMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id,
                                @RequestParam(required = false) List<Long> deptIds,
                                @RequestParam(required = false) List<Long> userIds,
                                @RequestParam String assignType) {
        try {
            if ("FILL".equals(assignType)) {
                rptTaskService.assignTask(id, userIds, null);
            } else if ("AUDIT".equals(assignType)) {
                rptTaskService.assignTask(id, null, userIds);
            } else {
                return Result.error("无效的分派类型，应为 FILL 或 AUDIT");
            }
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查看分派情况
     */
    @GetMapping("/{id}/assigns")
    public Result<List<RptTaskAssign>> getAssigns(@PathVariable Long id,
                                                    @RequestParam(required = false) String assignType) {
        try {
            List<RptTaskAssign> assigns = rptTaskService.getAssigns(id, assignType);
            return Result.success(assigns);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 任务统计
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        try {
            Map<String, Object> stats = rptTaskService.getTaskStatistics();
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long) {
                return (Long) authentication.getPrincipal();
        }
        return null;
    }
}
