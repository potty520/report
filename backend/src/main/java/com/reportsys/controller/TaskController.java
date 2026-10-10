package com.reportsys.controller;

import com.reportsys.common.PageResult;
import com.reportsys.common.Result;
import com.reportsys.model.RptTask;
import com.reportsys.model.RptTaskAssign;
import com.reportsys.service.RptTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.reportsys.annotation.OperLog;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/task")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN')")
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
        } catch (AccessDeniedException e) {
            throw e;
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
            rptTaskService.updateTask(task);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    /** 兼容旧前端：PUT /xxx/{id}（id 在路径里）；委托同类 update，保留权限与校验 */
    @PutMapping("/{id}")
    public Result<Void> updateById(@PathVariable Long id, @RequestBody RptTask task) {
        task.setId(id);
        return update(task);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            rptTaskService.deleteById(id);
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
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
        } catch (AccessDeniedException e) {
            throw e;
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
        } catch (AccessDeniedException e) {
            throw e;
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
            boolean hasUsers = userIds != null && !userIds.isEmpty();
            boolean hasDepts = deptIds != null && !deptIds.isEmpty();
            if (!hasUsers && !hasDepts) {
                return Result.error("请至少选择一个分派对象（userIds 或 deptIds）");
            }
            boolean audit = "AUDIT".equals(assignType);
            if (!"FILL".equals(assignType) && !audit) {
                return Result.error("无效的分派类型，应为 FILL 或 AUDIT");
            }
            if (hasUsers) {
                rptTaskService.assignTask(id, audit ? null : userIds, audit ? userIds : null);
            } else {
                rptTaskService.assignTaskByDepts(id, deptIds, audit);
            }
            return Result.success();
        } catch (AccessDeniedException e) {
            throw e;
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
     * 手动触发生成当前属期的部门子任务（P0-C；定时任务默认每天 00:05 自动执行）
     */
    @PostMapping("/generate-periodic")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperLog(module = "任务管理", action = "生成周期任务")
    public Result<Map<String, Object>> generatePeriodic(@RequestParam(required = false) Long parentTaskId) {
        try {
            return Result.success(rptTaskService.generatePeriodicTasks(parentTaskId));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 主任务的子任务列表（P0-C）
     */
    @GetMapping("/{id}/children")
    public Result<List<RptTask>> children(@PathVariable Long id) {
        try {
            return Result.success(rptTaskService.listChildren(id));
        } catch (RuntimeException e) {
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
