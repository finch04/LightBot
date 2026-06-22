package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.service.AgentService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.DocumentService;
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
 * 内置工具 — 文档名称搜索
 * <p>在智能体绑定的知识库中按文件名模糊搜索，返回匹配的文档列表（含文档ID）。
 * 典型场景：向量检索未命中但用户提及特定文件/人名/主题时，按标题定位文档。</p>
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Component("searchDocumentsTool")
@SystemTool(displayName = "文档名称搜索", description = "在智能体绑定的知识库中按文件名模糊搜索，返回匹配的文档列表", type = "knowledge", tags = {"知识库"},
        outputExample = "{\"total\":2,\"documents\":[{\"document_id\":1234567890,\"document_name\":\"张三的简历\",\"knowledge_name\":\"HR文档库\"},{\"document_id\":1234567891,\"document_name\":\"张三的绩效评估\",\"knowledge_name\":\"HR文档库\"}]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"total\":{\"type\":\"integer\",\"description\":\"匹配文档总数\"},\"documents\":{\"type\":\"array\",\"description\":\"匹配的文档列表\",\"items\":{\"type\":\"object\",\"properties\":{\"document_id\":{\"type\":\"integer\",\"description\":\"文档ID，可用于 find_in_document 查看详情\"},\"document_name\":{\"type\":\"string\",\"description\":\"文档文件名\"},\"knowledge_name\":{\"type\":\"string\",\"description\":\"所属知识库名称\"}}}}}}")
@RequiredArgsConstructor
public class SearchDocumentsTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @Tool(name = "search_documents",
          description = "在智能体绑定的知识库中按文件名模糊搜索，返回匹配的文档列表（含文档ID）。"
                + "当向量检索未命中但用户提及特定文件/人名/主题时使用。"
                + "返回结果中的文档ID可用于 open_document 查看文档详情。")
    public String searchDocuments(
            @ToolParam(description = "搜索关键词（匹配文件名）")
            @ToolParamMeta(example = "张三", required = true) String keyword,
            ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:search_documents] 搜索: keyword={}, agentId={}", keyword, agentId);

        if (keyword == null || keyword.isBlank()) {
            return "搜索关键词不能为空。";
        }

        if (agentId == null) {
            return "无法确定当前智能体，文档搜索已跳过。";
        }

        // 1. 获取 Agent 绑定的知识库
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        if (knowledgeIds.isEmpty()) {
            return "该智能体未绑定任何知识库，无法搜索。";
        }

        ToolEventEmitter.emit("正在搜索文件名包含「" + keyword + "」的文档...");

        // 2. 遍历知识库，按文件名模糊匹配
        String lowerKeyword = keyword.toLowerCase();
        List<MatchedDoc> matchedDocs = new ArrayList<>();

        for (Long knowledgeId : knowledgeIds) {
            Knowledge knowledge = knowledgeService.getById(knowledgeId);
            if (knowledge == null) continue;

            List<Document> documents = documentService.listByKnowledgeIdInternal(knowledgeId);
            for (Document doc : documents) {
                if (doc.getName() != null && doc.getName().toLowerCase().contains(lowerKeyword)) {
                    matchedDocs.add(new MatchedDoc(doc.getId(), doc.getName(), knowledge.getName()));
                }
            }
        }

        if (matchedDocs.isEmpty()) {
            log.info("[Tool:search_documents] 未找到匹配: keyword={}, knowledgeIds={}", keyword, knowledgeIds);
            return "未在知识库中找到文件名包含「" + keyword + "」的文档。";
        }

        // 3. 构建 JSON 返回
        List<Map<String, Object>> docList = new ArrayList<>();
        for (MatchedDoc md : matchedDocs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("document_id", md.id);
            item.put("document_name", md.name);
            item.put("knowledge_name", md.knowledgeName);
            docList.add(item);
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("total", matchedDocs.size());
        output.put("documents", docList);

        log.info("[Tool:search_documents] 搜索完成: keyword={}, matched={}", keyword, matchedDocs.size());
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "搜索完成但序列化失败: " + e.getMessage();
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

    private record MatchedDoc(Long id, String name, String knowledgeName) {}
}
