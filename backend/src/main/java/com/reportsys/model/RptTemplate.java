package com.reportsys.model;

import java.util.List;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_template")
public class RptTemplate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String templateName;
    private String templateCode;
    private Integer fillingCycle;
    private Long deptId;
    private Long projectId;
    private String fileUrl;
    private String fileName;
    private String tableName;
    private Long datasourceId;
    private String description;
    private Integer status;
    private Integer auditStatus;
    private String auditComment;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String deptName;
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private String dsName;
    @TableField(exist = false)
    private String datasourceName;
    @TableField(exist = false)
    private String createByName;
    @TableField(exist = false)
    private List<RptTemplateField> fields;
}
