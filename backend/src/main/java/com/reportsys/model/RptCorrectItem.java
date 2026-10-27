package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 补正明细项（R-14：新增 / 修改 / 删除，保留原值）。 */
@Data
@TableName("rpt_correct_item")
public class RptCorrectItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long correctId;
    private Long taskId;
    private Long taskDataId;
    /** 1新增 2修改 3删除 */
    private Integer opType;
    private String rowKey;
    private String beforeJson;
    private String afterJson;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
