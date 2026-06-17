package com.lightbot.tool.builtin;

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
@SystemTool(displayName = "文档内容定位", description = "在知识库文档中按关键词或正则表达式定位匹配内容", tags = {"知识库"})
@RequiredArgsConstructor
public class FindInDocumentTool {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;

    @Tool(name = "find_in_document",
          description = "在知识库文档中按关键词或正则表达式搜索，返回匹配的行及上下文。当用户需要在文档中查找特定内容、定位关键词时调用此工具。")
    public String findInDocument(
            @ToolParam(description = "搜索关键词或正则表达式")
            @ToolParamMeta(example = "配置文件") String query,
            @ToolParam(description = "文档ID（可选，不传则搜索整个知识库的所有文档）")
            @ToolParamMeta(example = "1234567890", required = false) String documentId,
            @ToolParam(description = "知识库ID（不指定文档时必填）")
            @ToolParamMeta(example = "1234567890", required = false) String knowledgeId,
            @ToolParam(description = "上下文行数（匹配行前后各显示几行，默认2）")
            @ToolParamMeta(example = "2", required = false) Integer contextLines,
            ToolContext context) {
        Long agentId = resolveAgentId(context);
        log.info("[Tool:find_in_document] 搜索: query={}, documentId={}, knowledgeId={}, agentId={}",
                query, documentId, knowledgeId, agentId);

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
            documents = documentService.listByKnowledgeId(kbId);
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

        StringBuilder sb = new StringBuilder();
        int totalMatches = 0;

        for (Document doc : documents) {
            try {
                String content = documentService.readDocumentContent(doc.getId());
                if (content == null || content.isBlank()) continue;

                String[] lines = content.split("\n");
                List<int[]> matches = new ArrayList<>();

                for (int i = 0; i < lines.length; i++) {
                    Matcher m = pattern.matcher(lines[i]);
                    if (m.find()) {
                        matches.add(new int[]{i});
                    }
                }

                if (matches.isEmpty()) continue;

                sb.append(String.format("=== 文档「%s」（%d 处匹配）===\n\n", doc.getName(), matches.size()));
                totalMatches += matches.size();

                for (int[] match : matches) {
                    int lineIdx = match[0];
                    int start = Math.max(0, lineIdx - ctxLines);
                    int end = Math.min(lines.length - 1, lineIdx + ctxLines);

                    sb.append(String.format("第 %d 行:\n", lineIdx + 1));
                    for (int j = start; j <= end; j++) {
                        String prefix = j == lineIdx ? ">> " : "   ";
                        sb.append(prefix).append(lines[j]).append("\n");
                    }
                    sb.append("\n");
                }
            } catch (Exception e) {
                log.warn("[Tool:find_in_document] 读取文档失败: docId={}, error={}", doc.getId(), e.getMessage());
            }
        }

        if (totalMatches == 0) {
            return "未在文档中找到与「" + query + "」匹配的内容。";
        }

        String result = sb.toString();
        // 限制返回长度
        if (result.length() > 10000) {
            result = result.substring(0, 10000) + "\n\n...（结果过长，仅显示前 10000 字符，共 " + totalMatches + " 处匹配）";
        }

        return result;
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
