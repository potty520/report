package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 模板展示信息配置（R-01：无 / 关联模板 / 关联表）。 */
@Data
@TableName("rpt_template_display_config")
public class RptTemplateDisplayConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    /** 0无 1关联模板 2关联表 */
    private Integer configType;
    private Long refTemplateId;
    private Long refDatasourceId;
    private String refTableName;
    /** 展示信息项 JSON 数组 */
    private String refColumns;
    /** 0未检测 1通过 2失败 */
    private Integer linkChecked;
    private LocalDateTime linkCheckTime;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
