package com.lightbot.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新用户请求
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
public class AdminUserUpdateRequest {

    @NotNull(message = "用户ID不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private String email;

    private String phone;

    private UserRole role;

    private UserStatus status;
}
