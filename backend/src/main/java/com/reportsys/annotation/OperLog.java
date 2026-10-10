package com.reportsys.annotation;

import java.lang.annotation.*;

/**
 * 操作审计注解（P2-G）：标注在 Controller 方法上，由 OperLogAspect 落 rpt_oper_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {
    /** 模块名，如 模板管理 / 数据填报 */
    String module();

    /** 操作名，如 物化建表 / Excel导入 */
    String action();
}
