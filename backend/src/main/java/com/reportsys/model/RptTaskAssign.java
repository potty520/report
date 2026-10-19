package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_task_assign")
public class RptTaskAssign {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long deptId;
    private Long userId;
    private String assignType;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String realName;
    @TableField(exist = false)
    private String taskName;
}
