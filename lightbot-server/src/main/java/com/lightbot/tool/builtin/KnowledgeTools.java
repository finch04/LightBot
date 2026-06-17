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
@SystemTool(displayName = "知识库操作工具集", description = "知识库相关操作：列出知识库、获取思维导图、查看文档原文", tags = {"知识库"})
@RequiredArgsConstructor
public class KnowledgeTools {

    private final AgentService agentService;
    private final KnowledgeService knowledgeService;
    private final DocumentService documentService;

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
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("当前智能体绑定的知识库（共 %d 个）：\n\n", knowledgeIds.size()));

        for (int i = 0; i < knowledgeIds.size(); i++) {
            Knowledge kb = knowledgeService.getById(knowledgeIds.get(i));
            if (kb == null) continue;
            sb.append(String.format("%d. %s\n", i + 1, kb.getName()));
            if (kb.getDescription() != null && !kb.getDescription().isBlank()) {
                sb.append("   描述：").append(kb.getDescription()).append("\n");
            }
            sb.append(String.format("   文档数：%d，分块数：%d，Token数：%d\n",
                    kb.getDocumentCount() != null ? kb.getDocumentCount() : 0,
                    kb.getChunkCount() != null ? kb.getChunkCount() : 0,
                    kb.getTotalTokens() != null ? kb.getTotalTokens() : 0));
            sb.append("\n");
        }

        return sb.toString();
    }

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
        Object mindmap = knowledgeService.getMindmap(kbId);
        if (mindmap == null) {
            return "知识库「" + kb.getName() + "」尚未生成思维导图。请先在知识库管理页面生成思维导图。";
        }

        return "知识库「" + kb.getName() + "」的思维导图：\n" + mindmap.toString();
    }

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

            return "文档「" + doc.getName() + "」原文内容：\n\n" + content;
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
