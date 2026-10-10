package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private String menuCode;
    private String menuType;
    private String path;
    private String component;
    private String permission;
    private String icon;
    private Integer sort;
    private Integer status;
    private Integer visible;

    @TableField(exist = false)
    private List<SysMenu> children;
}
