package com.lightbot.workflow.processor;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.McpServer;
import com.lightbot.enums.NodeType;
import com.lightbot.service.McpClientService;
import com.lightbot.service.McpServerService;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import com.lightbot.workflow.WorkflowPromptUtils;
import com.lightbot.workflow.WorkflowVariableUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 工具节点：按 MCP Server 名称和工具名调用远程工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private final McpServerService mcpServerService;
    private final McpClientService mcpClientService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.MCP;
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        Long serverId = WorkflowNodeDataUtils.parseLongId(nodeData.get("mcpServerId"));
        McpServer server = null;
        if (serverId != null) {
            server = mcpServerService.getById(serverId);
        }
        if (server == null) {
            String serverName = WorkflowNodeDataUtils.parseString(nodeData.get("mcpServerName"));
            if (serverName == null) {
                serverName = WorkflowNodeDataUtils.parseString(nodeData.get("serverName"));
            }
            if (serverName != null && !serverName.isBlank()) {
                server = mcpServerService.getOne(new LambdaQueryWrapper<McpServer>()
                        .eq(McpServer::getName, serverName)
                        .last("LIMIT 1"));
            }
        }
        if (server == null) {
            throw new IllegalArgumentException("MCP 节点未找到对应服务，请配置 mcpServerName 或 mcpServerId");
        }

        String toolName = WorkflowNodeDataUtils.parseString(nodeData.get("toolName"));
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("MCP 节点未配置 toolName");
        }

        Map<String, Object> toolParams = buildToolParams(nodeData, context.getVariables());
        String argsJson;
        try {
            argsJson = objectMapper.writeValueAsString(toolParams);
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP 参数序列化失败: " + e.getMessage(), e);
        }

        final McpServer mcpServer = server;
        List<ToolCallback> callbacks = mcpClientService.getAllToolCallbacks(mcpServer.getId());
        ToolCallback target = callbacks.stream()
                .filter(cb -> toolName.equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "MCP 服务 [" + mcpServer.getName() + "] 中未找到工具: " + toolName));

        log.info("[McpNodeProcessor] 调用 MCP 工具: server={}, tool={}, args={}",
                mcpServer.getName(), toolName, argsJson);

        ToolContext toolContext = new ToolContext(Map.of(
                "agentId", context.getAgent().getId(),
                "requestId", "wf-" + context.getCurrentNodeId()));
        String result;
        try {
            result = target.call(argsJson, toolContext);
        } catch (Exception e) {
            throw new IllegalArgumentException("MCP 工具执行失败: " + e.getMessage(), e);
        }

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("output", result);
        outputs.put("mcpResult", result);

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(result)
                .build();
    }

  @SuppressWarnings("unchecked")
    private Map<String, Object> buildToolParams(Map<String, Object> nodeData, Map<String, Object> variables) {
        Map<String, Object> params = new HashMap<>();
        Object inputParamsRaw = nodeData.get("inputParams");
        if (inputParamsRaw == null) {
            inputParamsRaw = nodeData.get("input_params");
        }
        if (inputParamsRaw instanceof String str && !str.isBlank() && !"{}".equals(str.trim())) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(str, new TypeReference<Map<String, Object>>() {});
                parsed.forEach((k, v) -> params.put(k, renderParamValue(v, variables)));
                return params;
            } catch (Exception e) {
                log.warn("[McpNodeProcessor] 解析 inputParams JSON 失败: {}", e.getMessage());
            }
        }
        if (inputParamsRaw instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> row)) {
                    continue;
                }
                String key = row.get("key") != null ? row.get("key").toString() : null;
                if (key == null || key.isBlank()) {
                    continue;
                }
                params.put(key, renderParamValue(row.get("value"), variables));
            }
        }
        if (params.isEmpty() && variables != null) {
            params.put("query", variables.get("query"));
            params.put("input", variables.getOrDefault("input", variables.get("query")));
        }
        return params;
    }

    private Object renderParamValue(Object value, Map<String, Object> variables) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            Object resolved = WorkflowVariableUtils.resolveValue(str, variables);
            return resolved != null ? resolved : WorkflowPromptUtils.render(str, variables);
        }
        return value;
    }
}
