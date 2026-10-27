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
    /** 无更新提交 0否 1是（R-02） */
    private Integer noUpdate;
    private String noUpdateReason;
    private LocalDateTime submitTime;
    private Integer auditStatus;
    private String auditComment;
    private Long auditUserId;
    private LocalDateTime auditTime;
    /** 落库状态 0未入库 1成功 2失败（P0-A） */
    private Integer inboundStatus;
    private LocalDateTime inboundTime;
    private Integer inboundRows;
    private String errorMsg;
    /** 部门审核（双通道第一级，P0-C） */
    private Integer deptAuditStatus;
    private Long deptAuditUserId;
    private LocalDateTime deptAuditTime;
    private String deptAuditComment;
    /** 任务方审核（双通道第二级，P0-C） */
    private Integer taskAuditStatus;
    private Long taskAuditUserId;
    private LocalDateTime taskAuditTime;
    private String taskAuditComment;
    /** 补正状态 0无 1进行中 2已完成 */
    private Integer correctStatus;
    private Integer correctWay;
    private String correctComment;
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
