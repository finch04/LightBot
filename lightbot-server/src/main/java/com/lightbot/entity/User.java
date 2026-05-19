package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("user")
public class User {

    /** 主键ID，雪花算法生成 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户名，唯一，用于登录 */
    private String username;

    /** 邮箱，唯一 */
    private String email;

    /** 密码，BCrypt加密存储 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 角色: admin-管理员, user-普通用户 */
    private UserRole role;

    /** 状态: active-正常, disabled-禁用 */
    private UserStatus status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 扩展配置(JSON) */
    private String config;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除: 0-未删除 1-已删除 */
    @TableLogic
    private Integer deleted;
}
