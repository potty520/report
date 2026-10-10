package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统计/提醒规则配置（对应原版 statistics_rule_config）。
 *
 * 规则以 rule_code 唯一标识，由业务代码按 code 读取；
 * 缺行或表未迁移时按「默认不改变现有行为」的值处理（graceDays=0、remindDays=0、enabled=false）。
 */
@Data
@TableName("rpt_statistics_rule")
public class RptStatisticsRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则编码，如 OVERDUE_REMIND / TODO_REMIND / CORRECT_REMIND */
    private String ruleCode;

    private String ruleName;

    /** 超期宽限天数：截止时间 + 该天数之后才算超期 */
    private Integer graceDays;

    /** 提前提醒天数：到期前多少天开始提醒（0=不提前提醒） */
    private Integer remindDays;

    /** 是否启用 */
    private Integer enabled;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
