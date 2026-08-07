package com.lightbot.dto;

import com.lightbot.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员创建用户请求
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
public class AdminUserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度3-32")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度6-64")
    private String password;

    private String nickname;

    private String email;

    private String phone;

    /** 角色，默认普通用户 */
    private UserRole role;
}
