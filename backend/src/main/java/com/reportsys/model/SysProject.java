package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_project")
public class SysProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deptId;
    private Long parentId;
    private String projectName;
    private String projectCode;
    private String description;
    private Integer status;
    private Integer sort;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<SysProject> children;
    @TableField(exist = false)
    private String deptName;
}
