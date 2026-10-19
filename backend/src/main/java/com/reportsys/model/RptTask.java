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
    /** 父（主）任务 ID，NULL 表示主任务；自动生成的周期子任务会指向主任务（P0-C） */
    private Long parentTaskId;
    /** 自动生成批次（属期） */
    private String generateBatch;
    /** 部门审核状态（主任务汇总用，P0-C） */
    private Integer deptAuditStatus;
    private Long deptAuditUserId;
    private LocalDateTime deptAuditTime;
    /** 任务方审核状态 */
    private Integer taskAuditStatus;
    private Long taskAuditUserId;
    private LocalDateTime taskAuditTime;
    /** 补正状态 0无 1进行中 2已完成 */
    private Integer correctStatus;
    private Integer correctWay;
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
