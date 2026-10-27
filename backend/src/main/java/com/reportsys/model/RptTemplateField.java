package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("rpt_template_field")
public class RptTemplateField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String fieldName;
    private String fieldCode;
    private String fieldType;
    /** 0普通字段 1附件字段（R-07） */
    private Integer fieldKind;
    private Integer fieldLength;
    /** 数值小数位（精度） */
    private Integer fieldScale;
    private Integer isRequired;
    private Integer isKey;
    private String excelColumn;
    private String dbColumn;
    private Integer sort;
}
