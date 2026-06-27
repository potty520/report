package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_task")
public class RptTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskName;
    private Long templateId;
    private Long deptId;
    private Long projectId;
    private String belongTime;
    private Integer fillingCycle;
    private Integer status;
    private LocalDateTime fillDeadline;
    private LocalDateTime auditDeadline;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String templateName;
    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private Integer fillCount;
    @TableField(exist = false)
    private Integer totalCount;
    @TableField(exist = false)
    private String createByName;
    @TableField(exist = false)
    private Integer auditStatus;
}
