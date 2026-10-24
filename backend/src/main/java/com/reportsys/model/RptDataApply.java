package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_data_apply")
public class RptDataApply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long applicantId;
    private Long applicantDeptId;
    /** 申请标题（前端表单字段，早期只存 reason 导致标题丢失） */
    private String title;
    /** 数据范围说明 */
    private String dataScope;
    private String reason;
    private Integer status;
    private Long auditorId;
    private String auditComment;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private LocalDateTime auditTime;

    @TableField(exist = false)
    private String taskName;
    @TableField(exist = false)
    private String applicantName;
    @TableField(exist = false)
    private String applicantDeptName;
    @TableField(exist = false)
    private String auditorName;

    // ---- 兼容前端既有字段名（前端用 applyTime/approverName/approveTime/approveRemark） ----
    @TableField(exist = false)
    private String applyTime;
    @TableField(exist = false)
    private String approverName;
    @TableField(exist = false)
    private String approveTime;
    @TableField(exist = false)
    private String approveRemark;
}
