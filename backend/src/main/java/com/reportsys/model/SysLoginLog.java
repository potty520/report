package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_login_log")
public class SysLoginLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String location;
    private String browser;
    private String os;
    @JsonProperty("createTime")
    private LocalDateTime loginTime;
    private Integer status;
    private String msg;
}
