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
    private Integer fieldLength;
    private Integer isRequired;
    private Integer isKey;
    private String excelColumn;
    private String dbColumn;
    private Integer sort;
}
