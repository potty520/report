package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    /** TODO 待办 / RETURN 退回 / OVERDUE 超期 / REMIND 催办 / SYSTEM 系统 */
    private String msgType;
    private Long bizTaskId;
    private Long dataId;
    private Integer readFlag;
    private LocalDateTime readTime;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
