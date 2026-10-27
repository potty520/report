package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 任务退回记录（R-03 分派退回 / R-04 填报退回 / R-05 处理动作）。 */
@Data
@TableName("rpt_task_return")
public class RptTaskReturn {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    /** 1分派退回 2填报退回 3部门退回 */
    private Integer returnType;
    private Long fromUserId;
    private Long toUserId;
    private Long fromDeptId;
    private Long toDeptId;
    private String reason;
    /** 0待处理 1已处理 */
    private Integer status;
    /** 1撤销 2变更部门 3重新提交 4重新分派 */
    private Integer handleType;
    private Long handleBy;
    private LocalDateTime handleTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String taskName;
    @TableField(exist = false)
    private String fromUserName;
    @TableField(exist = false)
    private String deptName;
}
