package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.ChatSession;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.SessionStatus;
import com.lightbot.mapper.ChatSessionMapper;
import com.lightbot.service.AgentService;
import com.lightbot.service.ChatSessionService;
import com.lightbot.service.LlmTraceService;
import com.lightbot.service.MessageService;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.RedisUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话会话服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession>
        implements ChatSessionService {

    private final AgentService agentService;
    private final MessageService messageService;
    private final LlmTraceService llmTraceService;
    private final RedisUtil redisUtil;
    private final MinioUtil minioUtil;
    @Qualifier("lightBotExecutor")
    private final ThreadPoolTaskExecutor lightBotExecutor;

    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "lightbot:session:";
    private static final String LIST_CACHE_PREFIX = "lightbot:session:list:";
    private static final String LIST_VERSION_PREFIX = "lightbot:session:list:ver:";
    private static final long CACHE_TTL_SECONDS = 1800; // 30min
    private static final long LIST_CACHE_TTL_SECONDS = 60; // 60s

    private String cacheKey(Long sessionId) {
        return CACHE_PREFIX + sessionId;
    }

    private String listCacheKey(Long userId, int pageNum, int pageSize) {
        // 版本号嵌入 key，evict 时递增版本即可使旧 key 自然失效
        long ver = getListVersion(userId);
        return LIST_CACHE_PREFIX + userId + ":" + ver + ":" + pageNum + ":" + pageSize;
    }

    private String listVersionKey(Long userId) {
        return LIST_VERSION_PREFIX + userId;
    }

    /** 获取当前列表缓存版本号 */
    private long getListVersion(Long userId) {
        String ver = redisUtil.get(listVersionKey(userId));
        return ver != null ? Long.parseLong(ver) : 0L;
    }

    /** 写操作后递增版本号，使旧 key 自然过期 */
    private void evictListCache(Long userId) {
        redisUtil.increment(listVersionKey(userId));
    }

    /** 写操作后清除 session 详情缓存 */
    private void evictSessionCache(Long sessionId) {
        redisUtil.delete(cacheKey(sessionId));
    }

    @Override
    public ChatSession getById(java.io.Serializable id) {
        // 优先读缓存
        String json = redisUtil.get(cacheKey(Long.parseLong(id.toString())));
        if (json != null) {
            try {
                return objectMapper.readValue(json, ChatSession.class);
            } catch (Exception e) {
                log.warn("[Session] 反序列化缓存失败: id={}", id);
            }
        }
        ChatSession session = super.getById(id);
        if (session != null) {
            try {
                redisUtil.set(cacheKey(session.getId()), objectMapper.writeValueAsString(session), CACHE_TTL_SECONDS);
            } catch (Exception e) {
                log.warn("[Session] 写入缓存失败: id={}", id);
            }
        }
        return session;
    }

    @Override
    public ChatSession createSession(Long userId, Long agentId) {
        // 1. agentId为空时查询用户的默认Agent
        Long finalAgentId = agentId;
        if (finalAgentId == null) {
            var defaultAgent = agentService.getDefaultAgent(userId);
            if (defaultAgent != null) {
                finalAgentId = defaultAgent.getId();
            }
        }

        // 3. 创建会话，初始化统计数据
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setAgentId(finalAgentId);
        session.setTitle(ChatSession.DEFAULT_TITLE);
        session.setStatus(SessionStatus.ACTIVE);
        session.setMessageCount(0);
        session.setTotalTokens(0L);
        session.setPinned(false);
        save(session);
        // 新建会话后清除列表缓存
        evictListCache(userId);
        return session;
    }

    @Override
    public Page<ChatSession> listMySessions(Long userId, int pageNum, int pageSize) {
        // 优先读列表缓存
        String listKey = listCacheKey(userId, pageNum, pageSize);
        String cached = redisUtil.get(listKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } catch (Exception e) {
                log.warn("[Session] 列表缓存反序列化失败: userId={}", userId);
            }
        }
        Page<ChatSession> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, SessionStatus.ACTIVE)
                        .orderByDesc(ChatSession::getPinned)
                        .orderByDesc(ChatSession::getLastMessageAt));
        try {
            redisUtil.set(listKey, objectMapper.writeValueAsString(page), LIST_CACHE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("[Session] 列表缓存写入失败: userId={}", userId);
        }
        return page;
    }

    @Override
    public String getTitle(Long sessionId) {
        // 直接查DB，跳过缓存，用于轻量轮询
        ChatSession session = baseMapper.selectById(sessionId);
        return session != null ? session.getTitle() : null;
    }

    @Override
    public void updateTitle(Long sessionId, String title) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setTitle(title);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void archiveSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setStatus(SessionStatus.ARCHIVED);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void updateStats(Long sessionId, int tokenCount) {
        // 原子累加消息数、token数，避免并发竞态导致数据丢失
        baseMapper.incrementStats(sessionId, 1, tokenCount);
        // 失效列表缓存（列表排序依赖 lastMessageAt）
        ChatSession session = getById(sessionId);
        if (session != null) {
            evictListCache(session.getUserId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        // 1. 物理删除会话下的所有消息
        messageService.deleteBySessionId(sessionId);
        // 2. 物理删除会话下的所有调用链记录
        llmTraceService.deleteBySessionId(sessionId);
        // 3. 物理删除会话
        removeById(sessionId);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
        // 4. MinIO 文件清理放在事务提交后，避免事务回滚后文件已删除
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                lightBotExecutor.execute(() -> {
                    try {
                        minioUtil.deleteByPrefix("sessions/" + sessionId + "/");
                    } catch (Exception e) {
                        log.warn("[Session] 清理工作区文件失败, sessionId={}", sessionId, e);
                    }
                });
            }
        });
    }

    @Override
    public void togglePin(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        session.setPinned(Boolean.TRUE.equals(session.getPinned()) ? false : true);
        updateById(session);
        evictSessionCache(sessionId);
        evictListCache(session.getUserId());
    }

    @Override
    public void updateSessionAgent(Long sessionId, Long agentId, Long agentVersionId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        boolean agentChanged = agentId != null && !agentId.equals(session.getAgentId());
        boolean versionChanged = !java.util.Objects.equals(agentVersionId, session.getAgentVersionId());
        if (!agentChanged && !versionChanged) {
            return;
        }
        if (agentChanged) {
            session.setAgentId(agentId);
        }
        session.setAgentVersionId(agentVersionId);
        updateById(session);
        evictSessionCache(sessionId);
    }

    @Override
    public Page<ChatSession> listMySessions(Long userId, int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .eq(ChatSession::getStatus, SessionStatus.ACTIVE)
                .like(keyword != null && !keyword.isBlank(), ChatSession::getTitle, keyword)
                .orderByDesc(ChatSession::getPinned)
                .orderByDesc(ChatSession::getLastMessageAt);
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSessions(Long userId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 1. 批量物理删除消息
        for (Long id : ids) {
            messageService.deleteBySessionId(id);
        }
        // 2. 批量物理删除调用链记录
        for (Long id : ids) {
            llmTraceService.deleteBySessionId(id);
        }
        // 3. 批量物理删除会话
        removeByIds(ids);
        // 4. 清除缓存
        for (Long id : ids) {
            evictSessionCache(id);
        }
        evictListCache(userId);
        // 5. MinIO 文件清理放在事务提交后
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                lightBotExecutor.execute(() -> {
                    for (Long id : ids) {
                        try {
                            minioUtil.deleteByPrefix("sessions/" + id + "/");
                        } catch (Exception e) {
                            log.warn("[Session] 清理工作区文件失败, sessionId={}", id, e);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void deleteByAgentId(Long agentId) {
        List<ChatSession> sessions = list(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getAgentId, agentId));
        if (sessions.isEmpty()) {
            return;
        }
        for (ChatSession session : sessions) {
            try {
                deleteSession(session.getId());
            } catch (Exception e) {
                log.warn("[ChatSession] 级联删除会话失败: sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
        log.info("[ChatSession] 批量删除: agentId={}, count={}", agentId, sessions.size());
    }

    @Override
    public String exportSession(Long userId, Long sessionId, String format) {
        // 1. 校验会话归属
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        if (!userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }

        // 2. 获取全部消息（按时间正序）
        List<com.lightbot.entity.Message> messages = messageService.listBySessionId(sessionId);

        // 3. 按格式导出
        if ("json".equalsIgnoreCase(format)) {
            return exportAsJson(session, messages);
        }
        return exportAsMarkdown(session, messages);
    }

    private String exportAsJson(ChatSession session, List<com.lightbot.entity.Message> messages) {
        try {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("title", session.getTitle());
            result.put("createTime", session.getCreateTime());
            result.put("totalTokens", session.getTotalTokens());
            result.put("messageCount", messages.size());

            java.util.List<java.util.Map<String, Object>> msgList = new java.util.ArrayList<>();
            for (com.lightbot.entity.Message msg : messages) {
                java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("role", msg.getRole() != null ? msg.getRole().getCode() : "unknown");
                item.put("content", msg.getContent());
                item.put("createTime", msg.getCreateTime());
                if (msg.getMetadata() != null && !msg.getMetadata().isBlank()) {
                    item.put("metadata", objectMapper.readTree(msg.getMetadata()));
                }
                msgList.add(item);
            }
            result.put("messages", msgList);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("[ChatSession] JSON导出失败: sessionId={}", session.getId(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String exportAsMarkdown(ChatSession session, List<com.lightbot.entity.Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session.getTitle()).append("\n\n");
        sb.append("> 导出时间：").append(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("> 消息数：").append(messages.size()).append("\n\n");
        sb.append("---\n\n");

        for (com.lightbot.entity.Message msg : messages) {
            String role = msg.getRole() != null ? msg.getRole().getCode() : "unknown";
            String time = msg.getCreateTime() != null
                    ? msg.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                    : "";
            if ("user".equals(role)) {
                sb.append("### 👤 用户 ").append(time).append("\n\n");
            } else if ("assistant".equals(role)) {
                sb.append("### 🤖 助手 ").append(time).append("\n\n");
            } else {
                sb.append("### ").append(role).append(" ").append(time).append("\n\n");
            }

            // 消息内容
            if (msg.getContent() != null && !msg.getContent().isBlank()) {
                sb.append(msg.getContent()).append("\n\n");
            }

            // RAG 引用
            if (msg.getMetadata() != null && msg.getMetadata().contains("ragReferences")) {
                try {
                    var metaNode = objectMapper.readTree(msg.getMetadata());
                    if (metaNode.has("ragReferences") && metaNode.get("ragReferences").isArray()) {
                        sb.append("**RAG 引用：**\n\n");
                        for (var ref : metaNode.get("ragReferences")) {
                            String docName = ref.has("documentName") ? ref.get("documentName").asText() : "";
                            String preview = ref.has("contentPreview") ? ref.get("contentPreview").asText() : "";
                            double score = ref.has("score") ? ref.get("score").asDouble() : 0;
                            sb.append("- 📄 ").append(docName).append(" (相似度: ").append(String.format("%.2f", score)).append(")\n");
                            if (!preview.isBlank()) {
                                sb.append("  > ").append(preview.length() > 200 ? preview.substring(0, 200) + "..." : preview).append("\n");
                            }
                        }
                        sb.append("\n");
                    }
                } catch (Exception ignored) {
                }
            }

            // 工具调用摘要
            if (msg.getMetadata() != null && msg.getMetadata().contains("toolEvents")) {
                try {
                    var metaNode = objectMapper.readTree(msg.getMetadata());
                    if (metaNode.has("toolEvents") && metaNode.get("toolEvents").isArray()) {
                        java.util.Set<String> toolNames = new java.util.LinkedHashSet<>();
                        for (var evt : metaNode.get("toolEvents")) {
                            if (evt.has("toolName")) toolNames.add(evt.get("toolName").asText());
                        }
                        if (!toolNames.isEmpty()) {
                            sb.append("**工具调用：** ").append(String.join(", ", toolNames)).append("\n\n");
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            sb.append("---\n\n");
        }
        return sb.toString();
    }

    // ==================== 会话附件管理 ====================

    @Override
    public void appendSessionAttachments(Long sessionId, List<com.lightbot.dto.ChatAttachmentDTO> attachments, String source) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        ChatSession session = getById(sessionId);
        if (session == null) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(
                    session.getContext() != null && !session.getContext().isEmpty()
                            ? session.getContext() : "{}");
            com.fasterxml.jackson.databind.node.ArrayNode existingAtts;
            if (root.has("attachments") && root.get("attachments").isArray()) {
                existingAtts = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("attachments");
            } else {
                existingAtts = objectMapper.createArrayNode();
            }
            // 按 objectKey 去重
            java.util.Set<String> existingKeys = new java.util.HashSet<>();
            for (com.fasterxml.jackson.databind.JsonNode att : existingAtts) {
                if (att.has("objectKey")) {
                    existingKeys.add(att.get("objectKey").asText());
                }
            }
            com.fasterxml.jackson.databind.node.ObjectNode wrapped = (com.fasterxml.jackson.databind.node.ObjectNode) root;
            for (com.lightbot.dto.ChatAttachmentDTO att : attachments) {
                if (att.getObjectKey() != null && !existingKeys.contains(att.getObjectKey())) {
                    com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.valueToTree(att);
                    node.put("source", source);
                    node.put("createdAt", java.time.LocalDateTime.now().toString());
                    existingAtts.add(node);
                    existingKeys.add(att.getObjectKey());
                }
            }
            wrapped.set("attachments", existingAtts);
            session.setContext(objectMapper.writeValueAsString(wrapped));
            updateById(session);
            evictSessionCache(sessionId);
        } catch (Exception e) {
            log.warn("[ChatSession] 追加附件到会话上下文失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public List<com.lightbot.dto.SessionAttachmentVO> getSessionAttachments(Long sessionId) {
        List<com.lightbot.dto.SessionAttachmentVO> result = new ArrayList<>();
        ChatSession session = getById(sessionId);
        if (session == null) {
            return result;
        }
        // 1. 从 session.context.attachments 读取已持久化的附件
        if (session.getContext() != null && !session.getContext().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(session.getContext());
                if (root.has("attachments") && root.get("attachments").isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root.get("attachments")) {
                        result.add(objectMapper.convertValue(node, com.lightbot.dto.SessionAttachmentVO.class));
                    }
                }
            } catch (Exception e) {
                log.warn("[ChatSession] 解析会话附件失败: sessionId={}", sessionId, e);
            }
        }
        // 2. 收集 AI 生成文件（从最近消息的 toolEvents 中提取）
        List<com.lightbot.dto.SessionAttachmentVO> aiFiles = collectAiGeneratedFiles(sessionId);
        java.util.Set<String> existingKeys = result.stream()
                .map(com.lightbot.dto.SessionAttachmentVO::getObjectKey)
                .filter(k -> k != null)
                .collect(java.util.stream.Collectors.toSet());
        for (com.lightbot.dto.SessionAttachmentVO ai : aiFiles) {
            if (ai.getObjectKey() == null || !existingKeys.contains(ai.getObjectKey())) {
                result.add(ai);
            }
        }
        // 3. 刷新 previewUrl
        refreshAttachmentPreviews(result);
        return result;
    }

    @Override
    public void removeSessionAttachment(Long sessionId, String attachmentId) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(
                    session.getContext() != null && !session.getContext().isEmpty()
                            ? session.getContext() : "{}");
            if (!root.has("attachments") || !root.get("attachments").isArray()) {
                return;
            }
            com.fasterxml.jackson.databind.node.ArrayNode arr = (com.fasterxml.jackson.databind.node.ArrayNode) root.get("attachments");
            for (int i = 0; i < arr.size(); i++) {
                if (attachmentId.equals(arr.get(i).path("id").asText())) {
                    arr.remove(i);
                    break;
                }
            }
            session.setContext(objectMapper.writeValueAsString(root));
            updateById(session);
            evictSessionCache(sessionId);
        } catch (Exception e) {
            log.warn("[ChatSession] 移除会话附件失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 从最近消息的 toolEvents 中提取 AI 生成文件
     */
    private List<com.lightbot.dto.SessionAttachmentVO> collectAiGeneratedFiles(Long sessionId) {
        List<com.lightbot.dto.SessionAttachmentVO> files = new ArrayList<>();
        List<com.lightbot.entity.Message> messages = messageService.listBySessionId(sessionId);
        int startIdx = Math.max(0, messages.size() - 50);
        for (int i = startIdx; i < messages.size(); i++) {
            com.lightbot.entity.Message msg = messages.get(i);
            if (msg.getRole() != com.lightbot.enums.MessageRole.ASSISTANT) {
                continue;
            }
            if (msg.getMetadata() == null || msg.getMetadata().isBlank()) {
                continue;
            }
            try {
                com.fasterxml.jackson.databind.JsonNode meta = objectMapper.readTree(msg.getMetadata());
                com.fasterxml.jackson.databind.JsonNode toolEvents = meta.get("toolEvents");
                if (toolEvents == null || !toolEvents.isArray()) {
                    continue;
                }
                for (com.fasterxml.jackson.databind.JsonNode event : toolEvents) {
                    String toolName = event.path("toolName").asText("");
                    if (!"subagent_result".equals(toolName) && !"deliver_file".equals(toolName)) {
                        continue;
                    }
                    String result = event.path("result").asText(null);
                    if (result == null || result.isBlank()) {
                        continue;
                    }
                    extractDeliveredFiles(result, files);
                }
            } catch (Exception ignored) {
            }
        }
        return files;
    }

    /**
     * 从 subagent_result / deliver_file 结果中提取文件信息
     */
    private void extractDeliveredFiles(String result, List<com.lightbot.dto.SessionAttachmentVO> files) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(result);
            com.fasterxml.jackson.databind.JsonNode fileList = root.has("files") ? root.get("files") : root;
            if (fileList.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode f : fileList) {
                    com.lightbot.dto.SessionAttachmentVO vo = new com.lightbot.dto.SessionAttachmentVO();
                    vo.setId(f.has("id") ? f.get("id").asText() : java.util.UUID.randomUUID().toString());
                    vo.setFileName(f.has("fileName") ? f.get("fileName").asText() :
                            f.has("name") ? f.get("name").asText() : "未命名文件");
                    vo.setType(f.has("type") ? f.get("type").asText() : "document");
                    vo.setMimeType(f.has("mimeType") ? f.get("mimeType").asText() : null);
                    vo.setObjectKey(f.has("objectKey") ? f.get("objectKey").asText() : null);
                    if (f.has("previewUrl")) {
                        vo.setPreviewUrl(f.get("previewUrl").asText());
                    }
                    vo.setSource("ai_generated");
                    vo.setCreatedAt(f.has("createdAt") ? f.get("createdAt").asText() :
                            java.time.LocalDateTime.now().toString());
                    files.add(vo);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 刷新附件列表中的预览 URL
     */
    private void refreshAttachmentPreviews(List<com.lightbot.dto.SessionAttachmentVO> attachments) {
        for (com.lightbot.dto.SessionAttachmentVO att : attachments) {
            if (att.getObjectKey() != null && !att.getObjectKey().isBlank()) {
                try {
                    att.setPreviewUrl(minioUtil.getPresignedUrl(att.getObjectKey()));
                } catch (Exception e) {
                    log.debug("[ChatSession] 刷新预览URL失败: objectKey={}", att.getObjectKey());
                }
            }
        }
    }
}
