package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_task_data")
public class RptTaskData {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long templateId;
    private Long deptId;
    private Long fillUserId;
    private String dataJson;
    private String fileUrl;
    private String fileName;
    private Integer status;
    private LocalDateTime submitTime;
    private Integer auditStatus;
    private String auditComment;
    private Long auditUserId;
    private LocalDateTime auditTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String fillRealName;
    @TableField(exist = false)
    private String taskName;
    @TableField(exist = false)
    private String templateName;
    @TableField(exist = false)
    private LocalDateTime updateTime;
    @TableField(exist = false)
    private Integer fillingCycle;
    @TableField(exist = false)
    private String auditUserName;
    @TableField(exist = false)
    private String fillUserName;
    @TableField(exist = false)
    private String belongTime;
}
