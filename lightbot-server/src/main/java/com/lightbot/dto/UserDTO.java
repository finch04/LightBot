package com.lightbot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.constant.ConfigKeys;
import com.lightbot.entity.User;
import com.lightbot.enums.UserRole;
import com.lightbot.enums.UserStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应DTO
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
public class UserDTO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String avatar;
    private String phone;
    private UserRole role;
    private UserStatus status;
    private LocalDateTime createTime;
    private LocalDateTime lastLoginAt;
    private Boolean firstLogin;
    private String avatarFrame;
    private Integer level;

    public static UserDTO from(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole());
        dto.setStatus(user.getStatus());
        dto.setCreateTime(user.getCreateTime());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setAvatarFrame(extractConfigString(user.getConfig(), ConfigKeys.User.AVATAR_FRAME));
        dto.setLevel(extractConfigInt(user.getConfig(), ConfigKeys.User.LEVEL));
        return dto;
    }

    private static String extractConfigString(String config, String key) {
        if (config == null || config.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(config);
            JsonNode val = node.get(key);
            return (val != null && !val.isNull()) ? val.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer extractConfigInt(String config, String key) {
        if (config == null || config.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(config);
            JsonNode val = node.get(key);
            return (val != null && !val.isNull()) ? val.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
