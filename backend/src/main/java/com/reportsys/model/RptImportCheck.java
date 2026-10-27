package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 导入校验记录（R-09：报表核验 / 数据导入预校验）。 */
@Data
@TableName("rpt_import_check")
public class RptImportCheck {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskDataId;
    private Long taskId;
    /** 1报表核验 2导入预校验 */
    private Integer checkStage;
    private Integer totalRows;
    private Integer passRows;
    private Integer failRows;
    /** 0未通过 1通过 */
    private Integer passed;
    private String detailJson;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
