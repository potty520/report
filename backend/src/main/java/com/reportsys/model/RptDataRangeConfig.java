package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rpt_data_range_config")
public class RptDataRangeConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configName;
    private Long templateId;
    private String sqlText;
    private Long datasourceId;
    private String remark;
    private Integer status;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
