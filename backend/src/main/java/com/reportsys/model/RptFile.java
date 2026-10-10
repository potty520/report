package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("rpt_file")
public class RptFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long dataId;
    private Long templateId;
    private String fileName;
    private String storePath;
    private Long fileSize;
    private String fileType;
    private String contentType;
    private String md5;
    private Long uploadBy;
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String uploadByName;
}
