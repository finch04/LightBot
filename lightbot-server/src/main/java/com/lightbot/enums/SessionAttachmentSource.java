package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 会话附件来源
 */
@Getter
@RequiredArgsConstructor
public enum SessionAttachmentSource {

    USER_UPLOAD("user_upload"),
    AI_IMAGE("ai_image"),
    AI_SANDBOX("ai_sandbox"),
    AI_DELIVER("ai_deliver");

    @EnumValue
    @JsonValue
    private final String code;
}
