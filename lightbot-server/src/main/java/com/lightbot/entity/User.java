package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("users")
@Schema(description = "用户表")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    private Long id;

    @TableField("username")
    @Schema(description = "用户名")
    private String username;

    @TableField("email")
    @Schema(description = "邮箱")
    private String email;

    @TableField("password")
    @Schema(description = "密码")
    private String password;

    @TableField("nickname")
    @Schema(description = "昵称")
    private String nickname;

    @TableField("avatar")
    @Schema(description = "头像URL")
    private String avatar;

    @TableField("phone")
    @Schema(description = "手机号")
    private String phone;

    @TableField("role")
    @Schema(description = "角色")
    private UserRole role;

    @TableField("status")
    @Schema(description = "状态")
    private UserStatus status;

    @TableField("last_login_at")
    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginAt;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "扩展配置")
    private String config;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
