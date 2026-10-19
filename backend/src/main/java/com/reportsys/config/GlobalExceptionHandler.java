package com.reportsys.config;

import com.reportsys.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 安全加固：无权限统一返回 HTTP 403（此前会被下面的 Exception 分支吞成 200/系统异常） */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<?>> handleAccessDenied(AccessDeniedException e) {
        String msg = (e.getMessage() == null || e.getMessage().isEmpty()) ? "无权限执行该操作" : e.getMessage();
        log.warn("拒绝访问: {}", msg);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.error(403, msg));
    }

    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return Result.error(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<?> handleDuplicateKeyException(DuplicateKeyException e) {
        log.error("唯一键冲突: {}", e.getMessage());
        return Result.error("数据已存在，请勿重复提交");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);
        // 缺陷修复(U06)：底层异常原文（SQL/驱动/堆栈片段）不回显给调用方
        return Result.error(Result.sanitizeMessage(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error("系统异常，请联系管理员");
    }
}
