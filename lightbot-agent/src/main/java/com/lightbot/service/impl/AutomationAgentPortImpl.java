package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lightbot.common.BizException;
import com.lightbot.dto.ChatRequestDTO;
import com.lightbot.entity.Agent;
import com.lightbot.entity.ChatSession;
import com.lightbot.entity.Message;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.MessageRole;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.MessageService;
import com.lightbot.service.port.AutomationAgentPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 自动化任务 → 与 UI 相同的 chatStream 链路（对话 / 工作流）
 *
 * @author finch
 * @since 2026-07-26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAgentPortImpl implements AutomationAgentPort {

    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(25);

    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final ChatService chatService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Override
    public String requireAgentName(Long agentId, Long userId) {
        Agent agent = requireOwnedAgent(agentId, userId);
        return StringUtils.hasText(agent.getName()) ? agent.getName() : ("智能体 " + agentId);
    }

    @Override
    public AutomationAgentRunResult run(Long userId, Long agentId, String instruction, String sessionTitle) {
        requireOwnedAgent(agentId, userId);
        // 1. 新建自动化会话（source=automation，不进入个人调试列表）
        ChatSession session = chatSessionService.createAutomationSession(userId, agentId);
        if (StringUtils.hasText(sessionTitle)) {
            try {
                chatSessionService.updateTitle(session.getId(), sessionTitle.trim());
            } catch (Exception e) {
                log.warn("[Automation] 更新会话标题失败 sessionId={}", session.getId());
            }
        }
        // 2. 走 chatStream 全链路（含 WorkflowMiddleware），阻塞至完成
        ChatRequestDTO req = new ChatRequestDTO();
        req.setAgentId(agentId);
        req.setSessionId(session.getId());
        req.setMessage(instruction);
        req.setActorUserId(userId);
        try {
            chatService.chatStream(req).blockLast(RUN_TIMEOUT);
        } catch (Exception e) {
            log.error("[Automation] chatStream 失败 sessionId={}", session.getId(), e);
            throw new BizException(ErrorCode.AI_GENERATE_FAILED, e);
        }
        // 3. 从落库消息提取与 UI 同构的详情
        Message assistant = findLastAssistant(session.getId());
        if (assistant == null) {
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }
        String detailJson = buildDetailJson(assistant);
        String summary = StringUtils.hasText(assistant.getContent()) ? assistant.getContent() : "";
        return new AutomationAgentRunResult(session.getId(), summary, detailJson);
    }

    private Message findLastAssistant(Long sessionId) {
        List<Message> messages = messageService.listBySessionId(sessionId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message m = messages.get(i);
            if (m != null && m.getRole() == MessageRole.ASSISTANT) {
                return m;
            }
        }
        return null;
    }

    private String buildDetailJson(Message assistant) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("id", String.valueOf(assistant.getId()));
            node.put("role", "assistant");
            node.put("content", assistant.getContent() != null ? assistant.getContent() : "");
            if (StringUtils.hasText(assistant.getMetadata())) {
                node.set("metadata", objectMapper.readTree(assistant.getMetadata()));
            } else {
                node.putObject("metadata");
            }
            if (StringUtils.hasText(assistant.getToolEvents())) {
                node.set("toolEvents", objectMapper.readTree(assistant.getToolEvents()));
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("[Automation] 构建 detailJson 失败: {}", e.getMessage());
            try {
                ObjectNode fallback = objectMapper.createObjectNode();
                fallback.put("id", String.valueOf(assistant.getId()));
                fallback.put("role", "assistant");
                fallback.put("content", assistant.getContent() != null ? assistant.getContent() : "");
                return objectMapper.writeValueAsString(fallback);
            } catch (Exception ignored) {
                return "{\"role\":\"assistant\",\"content\":\"\"}";
            }
        }
    }

    private Agent requireOwnedAgent(Long agentId, Long userId) {
        // 企业资产：仅校验 Agent 存在；userId 用于会话归属，不限制 Agent 归属
        if (agentId == null) {
            throw new BizException(ErrorCode.AUTOMATION_AGENT_INVALID);
        }
        Agent agent = agentService.getById(agentId);
        if (agent == null) {
            throw new BizException(ErrorCode.AUTOMATION_AGENT_INVALID);
        }
        return agent;
    }
}
