package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.DocumentService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具 — 知识库操作工具集
 * <p>提供列出知识库、获取思维导图、打开文档原文等能力</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component("knowledgeTools")
@SystemTool(displayName = "知识库操作工具集", description = "知识库相关操作：列出知识库、获取思维导图、查看文档原文", type = "knowledge", tags = {"知识库"})
@RequiredArgsConstructor
public class KnowledgeTools {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @SystemTool(displayName = "列出知识库",
            outputExample = "{\"total\":2,\"knowledge_bases\":[{\"id\":1234567890,\"name\":\"产品文档库\",\"description\":\"产品相关文档\",\"document_count\":15,\"chunk_count\":320,\"total_tokens\":128000},{\"id\":1234567891,\"name\":\"FAQ库\",\"description\":\"常见问题\",\"document_count\":5,\"chunk_count\":80,\"total_tokens\":32000}]}",
            outputSchema = "{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"integer\",\"description\":\"知识库总数\"},\"knowledge_bases\":{\"type\":\"array\",\"description\":\"知识库列表\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"知识库ID\"},\"name\":{\"type\":\"string\",\"description\":\"知识库名称\"},\"description\":{\"type\":\"string\",\"description\":\"知识库描述\"},\"document_count\":{\"type\":\"integer\",\"description\":\"文档数量\"},\"chunk_count\":{\"type\":\"integer\",\"description\":\"文档片段数量\"},\"total_tokens\":{\"type\":\"integer\",\"description\":\"总Token数\"}}}}}}")
    @Tool(name = "list_knowledge_bases",
          description = "列出当前智能体绑定的所有知识库。当用户询问有哪些知识库、知识库列表时调用此工具。")
    public String listKnowledgeBases(ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:list_knowledge_bases] 列出知识库: agentId={}", agentId);

        if (agentId == null) {
            return "无法确定当前智能体，请从对话页选择 Agent 后重试。";
        }

        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return "该智能体未绑定任何知识库。";
        }

        ToolEventEmitter.emit("正在获取知识库列表...");

        // 批量查询知识库（1 次 SQL）
        Map<Long, Knowledge> knowledgeMap = knowledgeService.listByIds(knowledgeIds).stream()
                .collect(java.util.stream.Collectors.toMap(Knowledge::getId, k -> k));

        List<Map<String, Object>> kbList = new ArrayList<>();
        for (Long kbId : knowledgeIds) {
            Knowledge kb = knowledgeMap.get(kbId);
            if (kb == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", kb.getId());
            item.put("name", kb.getName());
            item.put("description", kb.getDescription());
            item.put("document_count", kb.getDocumentCount() != null ? kb.getDocumentCount() : 0);
            item.put("chunk_count", kb.getChunkCount() != null ? kb.getChunkCount() : 0);
            item.put("total_tokens", kb.getTotalTokens() != null ? kb.getTotalTokens() : 0);
            kbList.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("knowledge_bases", kbList);
        output.put("total", kbList.size());
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "序列化失败: " + e.getMessage();
        }
    }

    @SystemTool(displayName = "获取知识库思维导图",
            outputExample = "{\"knowledge_id\":1234567890,\"knowledge_name\":\"产品文档库\",\"mindmap\":{\"name\":\"产品文档库\",\"children\":[{\"name\":\"安装部署\",\"children\":[{\"name\":\"环境要求\"},{\"name\":\"安装步骤\"},{\"name\":\"配置说明\"}]},{\"name\":\"功能说明\",\"children\":[{\"name\":\"用户管理\"},{\"name\":\"权限配置\"}]}]}}",
            outputSchema = "{\"type\":\"object\",\"properties\":{\"knowledge_id\":{\"type\":\"integer\",\"description\":\"知识库ID\"},\"knowledge_name\":{\"type\":\"string\",\"description\":\"知识库名称\"},\"mindmap\":{\"type\":\"object\",\"description\":\"思维导图树形结构\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"节点名称\"},\"children\":{\"type\":\"array\",\"description\":\"子节点列表\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"子节点名称\"},\"children\":{\"type\":\"array\",\"description\":\"嵌套子节点\",\"items\":{\"type\":\"object\"}}}}}}}}}")
    @Tool(name = "get_mindmap",
          description = "获取指定知识库的思维导图。当用户想了解知识库的知识结构、目录概览时调用此工具。")
    public String getMindmap(
            @ToolParam(description = "知识库ID")
            @ToolParamMeta(example = "1234567890") String knowledgeId,
            ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:get_mindmap] 获取思维导图: knowledgeId={}, agentId={}", knowledgeId, agentId);

        Long kbId = parseLong(knowledgeId);
        if (kbId == null) {
            return "知识库ID格式不正确: " + knowledgeId;
        }

        // 校验权限
        if (agentId != null && !isKnowledgeBound(agentId, kbId)) {
            return "该知识库未绑定到当前智能体，无权访问。";
        }

        Knowledge kb = knowledgeService.getById(kbId);
        if (kb == null) {
            return "知识库不存在: " + knowledgeId;
        }

        ToolEventEmitter.emit("正在获取知识库「" + kb.getName() + "」的思维导图...");
        Object mindmap = knowledgeService.getMindmapForTool(kbId);
        if (mindmap == null) {
            return "知识库「" + kb.getName() + "」尚未生成思维导图。请先在知识库管理页面生成思维导图。";
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("knowledge_id", kbId.toString());
        output.put("knowledge_name", kb.getName());
        output.put("mindmap", mindmap);
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "序列化失败: " + e.getMessage();
        }
    }

    @SystemTool(displayName = "查看知识库文档原文",
            outputExample = "{\"document_id\":1234567890,\"document_name\":\"产品说明书.pdf\",\"content\":\"# 产品简介\\n\\n本产品是...\\n\\n## 功能特性\\n\\n1. 支持多模型...\"}",
            outputSchema = "{\"type\":\"object\",\"properties\":{\"document_id\":{\"type\":\"integer\",\"description\":\"文档ID\"},\"document_name\":{\"type\":\"string\",\"description\":\"文档文件名\"},\"content\":{\"type\":\"string\",\"description\":\"文档原文内容（超过8000字符时截断）\"}}}")
    @Tool(name = "open_kb_document",
          description = "打开知识库中的指定文档，查看文档原文内容。当用户想查看某个文档的详细内容时调用此工具。")
    public String openKbDocument(
            @ToolParam(description = "文档ID")
            @ToolParamMeta(example = "1234567890") String documentId,
            ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:open_kb_document] 打开文档: documentId={}, agentId={}", documentId, agentId);

        Long docId = parseLong(documentId);
        if (docId == null) {
            return "文档ID格式不正确: " + documentId;
        }

        Document doc = documentService.getById(docId);
        if (doc == null) {
            return "文档不存在: " + documentId;
        }

        // 校验权限
        if (agentId != null && !isKnowledgeBound(agentId, doc.getKnowledgeId())) {
            return "该文档所在知识库未绑定到当前智能体，无权访问。";
        }

        ToolEventEmitter.emit("正在读取文档「" + doc.getName() + "」...");
        try {
            String content = documentService.readDocumentContent(docId);
            if (content == null || content.isBlank()) {
                return "文档「" + doc.getName() + "」内容为空或尚未完成解析。";
            }

            // 限制返回长度，避免 token 溢出
            int maxLen = 8000;
            if (content.length() > maxLen) {
                content = content.substring(0, maxLen) + "\n\n...（内容过长，仅显示前 " + maxLen + " 字符）";
            }

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("document_id", docId.toString());
            output.put("document_name", doc.getName());
            output.put("content", content);
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.warn("[Tool:open_kb_document] 读取文档失败: documentId={}, error={}", docId, e.getMessage());
            return "读取文档失败: " + e.getMessage();
        }
    }

    private Long resolveAgentId(ToolContext context) {
        if (context == null || context.getContext() == null) return null;
        Object agentIdObj = context.getContext().get("agentId");
        if (agentIdObj instanceof Number num) {
            long id = num.longValue();
            return id > 0 ? id : null;
        }
        if (agentIdObj instanceof String str && !str.isBlank()) {
            try {
                long id = Long.parseLong(str.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private boolean isKnowledgeBound(Long agentId, Long knowledgeId) {
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        return knowledgeIds.contains(knowledgeId);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
