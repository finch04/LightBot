package com.lightbot.dto;

import com.lightbot.enums.MentionResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 聊天请求中的 mention 项（用户 @ 提及的资源）
 *
 * @author finch
 * @since 2026-06-29
 */
@Data
@Schema(description = "聊天请求 mention 项")
public class ChatMentionDTO {

    @Schema(description = "资源类型", example = "knowledge")
    private MentionResourceType type;

    @Schema(description = "资源ID（字符串形式，避免雪花ID精度丢失）", example = "1234567890123456789")
    private String resourceId;

    @Schema(description = "资源展示名", example = "产品知识库")
    private String name;

    @Schema(description = "原始 token", example = "@knowledge:1234567890123456789")
    private String token;
}
