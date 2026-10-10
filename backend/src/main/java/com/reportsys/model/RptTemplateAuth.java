package com.reportsys.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板两级授权（对应原版 rel_model_user / rel_model_table）。
 *
 * 语义：模板一旦存在授权记录，即变为「仅授权可见」——
 * 只有 auth_type=DEPT 且命中该用户所属部门，或 auth_type=USER 且命中该用户的记录才能看到/使用；
 * 没有任何授权记录的模板对所有具备模板权限的角色开放（向后兼容）。
 */
@Data
@TableName("rpt_template_auth")
public class RptTemplateAuth {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long templateId;

    /** DEPT=部门授权 USER=用户授权 */
    private String authType;

    /** 部门ID或用户ID */
    private Long targetId;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String targetName;
}
