package com.reportsys.model;

import java.util.List;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_dept")
public class SysDept {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String deptName;
    private String deptCode;
    private String leader;
    private String phone;
    private Integer sort;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SysDept> children;

    @TableField(exist = false)
    private List<SysProject> projects;
}
