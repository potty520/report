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
}
