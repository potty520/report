package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("rpt_datasource")
public class RptDatasource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dsName;
    private String dsType;
    private String ip;
    private Integer port;
    private String dbName;
    private String username;
    private String password;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
