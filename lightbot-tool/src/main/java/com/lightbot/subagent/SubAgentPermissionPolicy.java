package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.Tool;
import com.lightbot.enums.CommonStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SubAgent 工具权限策略。
 *
 * <p>当前先做服务端兜底：禁用工具不可执行；工具 config 可通过
 * {@code subagentAllowed=false} 显式禁止 SubAgent 使用，通过
 * {@code subagentDangerous=true} 标记危险工具，除非同时声明
 * {@code subagentDangerAllowed=true}。</p>
 *
 * @author finch
 * @since 2026-07-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentPermissionPolicy {

    /** 父会话专属工具：产物只能由主 Agent 汇总后向用户交付。 */
    private static final Set<String> PARENT_SESSION_TOOL_NAMES = Set.of(
            "present_artifacts",
            "present_business_page"
    );

    private final ObjectMapper objectMapper;

    /**
     * 过滤 SubAgent 可执行工具。
     *
     * @param subAgent 子智能体
     * @param tools    绑定工具列表
     * @return 允许执行的工具 ID 列表
     */
    public List<Long> filterExecutableToolIds(SubAgent subAgent, List<Tool> tools) {
        List<Long> allowed = new ArrayList<>();
        if (tools == null || tools.isEmpty()) {
            return allowed;
        }
        for (Tool tool : tools) {
            if (tool == null || tool.getId() == null) {
                continue;
            }
            if (tool.getStatus() != null && tool.getStatus() != CommonStatus.ACTIVE) {
                log.warn("[SubAgentPolicy] 拦截禁用工具: subAgent={}, tool={}", subAgent.getName(), tool.getName());
                continue;
            }
            if (PARENT_SESSION_TOOL_NAMES.contains(tool.getName())) {
                log.warn("[SubAgentPolicy] parent-session tool denied: subAgent={}, tool={}",
                        subAgent.getName(), tool.getName());
                continue;
            }
            Map<String, Object> config = parseConfig(tool.getConfig());
            if (Boolean.FALSE.equals(config.get("subagentAllowed"))) {
                log.warn("[SubAgentPolicy] 工具禁止 SubAgent 使用: subAgent={}, tool={}", subAgent.getName(), tool.getName());
                continue;
            }
            if (Boolean.TRUE.equals(config.get("subagentDangerous"))
                    && !Boolean.TRUE.equals(config.get("subagentDangerAllowed"))) {
                log.warn("[SubAgentPolicy] 拦截危险工具: subAgent={}, tool={}", subAgent.getName(), tool.getName());
                continue;
            }
            allowed.add(tool.getId());
        }
        return allowed;
    }

    private Map<String, Object> parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(config, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[SubAgentPolicy] 解析工具 config 失败: {}", e.getMessage());
            return Map.of();
        }
    }
}
