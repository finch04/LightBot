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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内置工具 — 文档内容定位
 * <p>在知识库文档中按关键词或正则表达式定位匹配内容，返回匹配行及上下文</p>
 *
 * @author finch
 * @since 2026-06-17
 */
@Slf4j
@Component("findInDocumentTool")
@SystemTool(displayName = "文档内容定位", description = "在知识库文档中按关键词或正则表达式定位匹配内容", type = "knowledge", tags = {"知识库"},
        outputExample = "{\"mode\":\"search\",\"query\":\"配置文件\",\"total_matches\":2,\"documents\":[{\"document_id\":1234567890,\"document_name\":\"系统配置指南\",\"match_count\":2,\"matches\":[{\"line_num\":15,\"context_lines\":[{\"line_num\":14,\"text\":\"# 系统配置\",\"matched\":false},{\"line_num\":15,\"text\":\"配置文件路径: /etc/app/config.yaml\",\"matched\":true},{\"line_num\":16,\"text\":\"# 详细说明\",\"matched\":false}]}]}]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"mode\":{\"type\":\"string\",\"description\":\"查询模式：search=关键词搜索，open=原文翻页\"},\"query\":{\"type\":\"string\",\"description\":\"搜索的关键词（search模式）\"},\"total_matches\":{\"type\":\"integer\",\"description\":\"总匹配处数（search模式）\"},\"documents\":{\"type\":\"array\",\"description\":\"按文档分组的匹配结果（search模式）\",\"items\":{\"type\":\"object\",\"properties\":{\"document_id\":{\"type\":\"integer\",\"description\":\"文档ID\"},\"document_name\":{\"type\":\"string\",\"description\":\"文档文件名\"},\"match_count\":{\"type\":\"integer\",\"description\":\"该文档的匹配处数\"},\"matches\":{\"type\":\"array\",\"description\":\"匹配位置列表\",\"items\":{\"type\":\"object\",\"properties\":{\"line_num\":{\"type\":\"integer\",\"description\":\"匹配行号\"},\"context_lines\":{\"type\":\"array\",\"description\":\"上下文行\",\"items\":{\"type\":\"object\",\"properties\":{\"line_num\":{\"type\":\"integer\",\"description\":\"行号\"},\"text\":{\"type\":\"string\",\"description\":\"行内容\"},\"matched\":{\"type\":\"boolean\",\"description\":\"是否为匹配行\"}}}}}}}}}},\"document_id\":{\"type\":\"integer\",\"description\":\"文档ID（open模式）\"},\"document_name\":{\"type\":\"string\",\"description\":\"文档文件名（open模式）\"},\"total_lines\":{\"type\":\"integer\",\"description\":\"文档总行数（open模式）\"},\"start_line\":{\"type\":\"integer\",\"description\":\"本次读取的起始行号（open模式）\"},\"end_line\":{\"type\":\"integer\",\"description\":\"本次读取的结束行号（open模式）\"},\"has_more\":{\"type\":\"boolean\",\"description\":\"是否还有后续内容（open模式）\"},\"next_offset\":{\"type\":\"integer\",\"description\":\"下次读取的起始行号（open模式）\"},\"content\":{\"type\":\"string\",\"description\":\"文档原文内容（open模式）\"}}}")
@RequiredArgsConstructor
public class FindInDocumentTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    @Tool(name = "find_in_document",
          description = "在知识库文档中按关键词或正则表达式搜索，返回匹配的行及上下文。"
                + "当 query 为空时，按 offset 翻页读取文档原文（open模式）。"
                + "典型流程：query_knowledge 返回 document_id → find_in_document(documentId, query) 定位关键词"
                + "或 find_in_document(documentId) 顺序翻页阅读原文。")
    public String findInDocument(
            @ToolParam(description = "搜索关键词或正则表达式（为空时进入原文翻页模式）")
            @ToolParamMeta(example = "配置文件", required = false) String query,
            @ToolParam(description = "文档ID（可选，不传则搜索整个知识库的所有文档）")
            @ToolParamMeta(example = "1234567890", required = false) String documentId,
            @ToolParam(description = "知识库ID（不指定文档时必填）")
            @ToolParamMeta(example = "1234567890", required = false) String knowledgeId,
            @ToolParam(description = "上下文行数（匹配行前后各显示几行，默认2）")
            @ToolParamMeta(example = "2", required = false) Integer contextLines,
            @ToolParam(description = "原文模式：从第几行开始读取（默认0）")
            @ToolParamMeta(example = "0", required = false) Integer offset,
            @ToolParam(description = "原文模式：读取字符数（默认2000，最大8000）")
            @ToolParamMeta(example = "2000", required = false) Integer windowSize,
            ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:find_in_document] 搜索: query={}, documentId={}, knowledgeId={}, agentId={}",
                query, documentId, knowledgeId, agentId);

        // 原文翻页模式：query 为空且提供了 documentId
        if ((query == null || query.isBlank()) && documentId != null && !documentId.isBlank()) {
            return openDocumentMode(documentId, offset, windowSize, agentId);
        }

        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空。";
        }

        int ctxLines = contextLines != null ? Math.max(0, Math.min(contextLines, 5)) : 2;

        // 确定要搜索的文档列表
        List<Document> documents = new ArrayList<>();
        Long docId = parseLong(documentId);
        Long kbId = parseLong(knowledgeId);

        if (docId != null) {
            // 搜索单个文档
            Document doc = documentService.getById(docId);
            if (doc == null) return "文档不存在: " + documentId;
            if (agentId != null && !isKnowledgeBound(agentId, doc.getKnowledgeId())) {
                return "该文档所在知识库未绑定到当前智能体，无权访问。";
            }
            documents.add(doc);
        } else if (kbId != null) {
            // 搜索整个知识库
            if (agentId != null && !isKnowledgeBound(agentId, kbId)) {
                return "该知识库未绑定到当前智能体，无权访问。";
            }
            Knowledge kb = knowledgeService.getById(kbId);
            if (kb == null) return "知识库不存在: " + knowledgeId;
            documents = documentService.listByKnowledgeIdInternal(kbId);
            if (documents.isEmpty()) return "知识库「" + kb.getName() + "」中没有文档。";
        } else {
            return "请指定文档ID或知识库ID。";
        }

        // 编译正则
        Pattern pattern;
        try {
            pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
        } catch (Exception e) {
            // 非正则，作为普通关键词
            pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE);
        }

        ToolEventEmitter.emit("正在 " + documents.size() + " 个文档中搜索「" + query + "」...");

        int totalMatches = 0;
        List<Map<String, Object>> docResults = new ArrayList<>();

        for (Document doc : documents) {
            try {
                String content = documentService.readDocumentContent(doc.getId());
                if (content == null || content.isBlank()) continue;

                String[] lines = content.split("\n");
                List<int[]> matchIndices = new ArrayList<>();

                for (int i = 0; i < lines.length; i++) {
                    Matcher m = pattern.matcher(lines[i]);
                    if (m.find()) {
                        matchIndices.add(new int[]{i});
                    }
                }

                if (matchIndices.isEmpty()) continue;

                totalMatches += matchIndices.size();

                // 构建该文档的匹配结果
                Map<String, Object> docResult = new LinkedHashMap<>();
                docResult.put("document_id", doc.getId());
                docResult.put("document_name", doc.getName());
                docResult.put("match_count", matchIndices.size());

                List<Map<String, Object>> matchList = new ArrayList<>();
                for (int[] match : matchIndices) {
                    int lineIdx = match[0];
                    int start = Math.max(0, lineIdx - ctxLines);
                    int end = Math.min(lines.length - 1, lineIdx + ctxLines);

                    Map<String, Object> matchItem = new LinkedHashMap<>();
                    matchItem.put("line_num", lineIdx + 1);

                    List<Map<String, Object>> ctxLineItems = new ArrayList<>();
                    for (int j = start; j <= end; j++) {
                        Map<String, Object> lineItem = new LinkedHashMap<>();
                        lineItem.put("line_num", j + 1);
                        lineItem.put("text", lines[j]);
                        lineItem.put("matched", j == lineIdx);
                        ctxLineItems.add(lineItem);
                    }
                    matchItem.put("context_lines", ctxLineItems);
                    matchList.add(matchItem);
                }
                docResult.put("matches", matchList);
                docResults.add(docResult);
            } catch (Exception e) {
                log.warn("[Tool:find_in_document] 读取文档失败: docId={}, error={}", doc.getId(), e.getMessage());
            }
        }

        if (totalMatches == 0) {
            return "未在文档中找到与「" + query + "」匹配的内容。";
        }

        // 构建 JSON 返回
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", "search");
        output.put("query", query);
        output.put("total_matches", totalMatches);
        output.put("documents", docResults);

        log.info("[Tool:find_in_document] 搜索完成: query={}, totalMatches={}", query, totalMatches);
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

    private boolean isKnowledgeBound(Long agentId, Long knowledgeId) {
        List<Long> knowledgeIds = agentService.getKnowledgeIds(agentId);
        return knowledgeIds.contains(knowledgeId);
    }

    /**
     * 原文翻页模式：按 offset + windowSize 读取文档内容
     */
    private String openDocumentMode(String documentId, Integer offset, Integer windowSize, Long agentId) {
        Long docId = parseLong(documentId);
        if (docId == null) return "请提供有效的文档ID。";

        int startLine = offset != null ? Math.max(0, offset) : 0;
        int charLimit = windowSize != null ? Math.min(Math.max(100, windowSize), 8000) : 2000;

        Document doc = documentService.getById(docId);
        if (doc == null) return "文档不存在: " + documentId;

        if (agentId != null && !isKnowledgeBound(agentId, doc.getKnowledgeId())) {
            return "该文档所在知识库未绑定到当前智能体，无权访问。";
        }

        ToolEventEmitter.emit("正在读取文档「" + doc.getName() + "」...");
        String content = documentService.readDocumentContent(docId);
        if (content == null || content.isBlank()) {
            return "文档「" + doc.getName() + "」内容为空或尚未处理完成。";
        }

        String[] lines = content.split("\n", -1);
        int totalLines = lines.length;

        if (startLine >= totalLines) {
            return String.format("文档「%s」共 %d 行，offset=%d 已超出范围。请使用 0 到 %d 之间的值。",
                    doc.getName(), totalLines, startLine, totalLines - 1);
        }

        StringBuilder window = new StringBuilder();
        int endLine = startLine;
        for (int i = startLine; i < totalLines && window.length() < charLimit; i++) {
            window.append(lines[i]).append("\n");
            endLine = i;
        }

        boolean hasMore = endLine + 1 < totalLines;

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", "open");
        output.put("document_id", docId);
        output.put("document_name", doc.getName());
        output.put("total_lines", totalLines);
        output.put("start_line", startLine + 1);
        output.put("end_line", endLine + 1);
        output.put("has_more", hasMore);
        output.put("next_offset", hasMore ? endLine + 1 : null);
        output.put("content", window.toString());

        log.info("[Tool:find_in_document] 原文读取: docId={}, lines={}-{}", docId, startLine + 1, endLine + 1);
        try {
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            return "原文读取完成但序列化失败: " + e.getMessage();
        }
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
