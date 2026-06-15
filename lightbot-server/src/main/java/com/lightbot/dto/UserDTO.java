package com.lightbot.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    private Boolean firstLogin;
    private String avatarFrame;

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
        dto.setAvatarFrame(extractAvatarFrame(user.getConfig()));
        return dto;
    }

    private static String extractAvatarFrame(String config) {
        if (config == null || config.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(config);
            JsonNode frame = node.get("avatarFrame");
            return (frame != null && !frame.isNull()) ? frame.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
