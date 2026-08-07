package com.lightbot.vo;
import com.lightbot.dto.*;
import com.lightbot.vo.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.entity.UserMemory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户长期记忆响应
 *
 * @author finch
 * @since 2026-07-09
 */
@Data
public class UserMemoryVO {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long apiKeyId;

    private String externalUserId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long agentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    private String memoryType;
    private String content;
    private List<String> keywords;
    private BigDecimal confidence;
    private String status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static UserMemoryVO from(UserMemory memory) {
        UserMemoryVO vo = new UserMemoryVO();
        vo.setId(memory.getId());
        vo.setApiKeyId(memory.getApiKeyId());
        vo.setExternalUserId(memory.getExternalUserId());
        vo.setAgentId(memory.getAgentId());
        vo.setSessionId(memory.getSessionId());
        vo.setMemoryType(memory.getMemoryType() != null ? memory.getMemoryType().getCode() : null);
        vo.setContent(memory.getContent());
        vo.setKeywords(parseKeywords(memory.getKeywords()));
        vo.setConfidence(memory.getConfidence());
        vo.setStatus(memory.getStatus() != null ? memory.getStatus().getCode() : null);
        vo.setLastUsedAt(memory.getLastUsedAt());
        vo.setCreateTime(memory.getCreateTime());
        vo.setUpdateTime(memory.getUpdateTime());
        return vo;
    }

    private static List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(keywordsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
