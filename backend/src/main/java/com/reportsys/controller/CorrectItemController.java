package com.reportsys.controller;

import com.reportsys.common.Result;
import com.reportsys.service.CorrectItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** R-14 线上补正明细（行级新增 / 修改 / 删除，保留原值）。 */
@RestController
@RequestMapping("/correct")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','DEPT_ADMIN','FILL_USER')")
public class CorrectItemController {

    @Autowired
    private CorrectItemService correctItemService;

    @GetMapping("/{correctId}/items")
    public Result<List<Map<String, Object>>> items(@PathVariable Long correctId) {
        return Result.success(correctItemService.list(correctId));
    }

    @PostMapping("/{correctId}/item")
    public Result<Void> add(@PathVariable Long correctId, @RequestBody Map<String, Object> body) {
        try {
            Integer opType = body.get("opType") == null ? null : Integer.valueOf(String.valueOf(body.get("opType")));
            correctItemService.add(correctId, opType,
                    body.get("rowKey") == null ? null : String.valueOf(body.get("rowKey")),
                    body.get("beforeJson") == null ? null : String.valueOf(body.get("beforeJson")),
                    body.get("afterJson") == null ? null : String.valueOf(body.get("afterJson")),
                    currentUserId());
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/item/{itemId}")
    public Result<Void> delete(@PathVariable Long itemId) {
        correctItemService.delete(itemId);
        return Result.success();
    }

    @GetMapping("/{correctId}/summary")
    public Result<Map<String, Object>> summary(@PathVariable Long correctId) {
        return Result.success(correctItemService.summary(correctId));
    }

    private Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Long) return (Long) a.getPrincipal();
        return null;
    }
}
