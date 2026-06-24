package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.IngestRequest;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import org.springframework.util.StringUtils;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.KnowledgeMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.DocumentService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.MilvusUtil;
import com.lightbot.util.MindmapUtil;
import com.lightbot.config.RedisCacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge>
        implements KnowledgeService {

    private final KnowledgeMemberService knowledgeMemberService;
    private final DocumentService documentService;
    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final MindmapUtil mindmapUtil;
    private final SystemConfigService systemConfigService;
    private final MilvusUtil milvusUtil;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_KNOWLEDGE, key = "#id", unless = "#result == null")
    public Knowledge getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_KNOWLEDGE, key = "#entity.id")
    public boolean updateById(Knowledge entity) {
        return super.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisCacheConfig.CACHE_KNOWLEDGE, allEntries = true)
    public Knowledge create(Knowledge knowledge) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getName, knowledge.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_NAME_EXISTS);
        }

        // 2. 初始化知识库字段
        knowledge.setUserId(userId);
        knowledge.setStatus(CommonStatus.ACTIVE);
        knowledge.setDocumentCount(0);
        knowledge.setChunkCount(0);
        knowledge.setTotalTokens(0L);
        save(knowledge);

        // 2. 创建者自动成为成员（CREATOR角色）
        KnowledgeMember member = new KnowledgeMember();
        member.setKnowledgeId(knowledge.getId());
        member.setUserId(userId);
        member.setRole(KnowledgeRole.CREATOR);
        knowledgeMemberService.save(member);

        return knowledge;
    }

    @Override
    public Knowledge update(Knowledge knowledge) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledge.getId(), KnowledgeRole.MANAGER);

        // 2. 校验存在性
        Knowledge existing = getById(knowledge.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 4. 名称变更时校验唯一性
        if (!existing.getName().equals(knowledge.getName())) {
            long count = count(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getName, knowledge.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.KNOWLEDGE_NAME_EXISTS);
            }
        }

        // 5. 更新允许修改的字段
        existing.setName(knowledge.getName());
        existing.setDescription(knowledge.getDescription());
        existing.setEmbeddingModel(knowledge.getEmbeddingModel());
        existing.setGraphEnabled(knowledge.getGraphEnabled());
        existing.setConfig(knowledge.getConfig());
        updateById(existing);
        return existing;
    }

    @Override
    public Page<Knowledge> listMyKnowledge(int pageNum, int pageSize, String name) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 查询用户加入的所有知识库ID
        List<Long> knowledgeIds = knowledgeMemberService.listKnowledgeIdsByUserId(userId);
        if (knowledgeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 2. 分页查询这些知识库
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Knowledge>()
                        .in(Knowledge::getId, knowledgeIds)
                        .eq(Knowledge::getStatus, CommonStatus.ACTIVE)
                        .like(StringUtils.hasText(name), Knowledge::getName, name)
                        .orderByDesc(Knowledge::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = RedisCacheConfig.CACHE_KNOWLEDGE, key = "#id")
    public void deleteById(Long id) {
        // 1. 权限校验：仅CREATOR可删除
        checkPermission(id, KnowledgeRole.CREATOR);

        // 2. 校验存在性
        Knowledge knowledge = getById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 级联删除所有子文档（含 MinIO 文件、向量、分片、版本快照）
        List<Document> documents = documentService.listByKnowledgeIdInternal(id);
        for (Document doc : documents) {
            try {
                documentService.deleteDocument(doc.getId());
            } catch (Exception e) {
                log.warn("[知识库删除] 级联删除文档失败: documentId={}, error={}", doc.getId(), e.getMessage());
            }
        }

        // 4. 逻辑删除知识库
        removeById(id);

        // 5. 同时删除所有成员关系
        knowledgeMemberService.removeByKnowledgeId(id);
    }

    @Override
    public Knowledge getByIdWithPermission(Long id) {
        // 1. 权限校验：需要成员权限
        checkMember(id);

        // 2. 返回知识库详情
        return getById(id);
    }

    @Override
    public void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta) {
        // 增量更新知识库统计数据
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        knowledge.setDocumentCount(knowledge.getDocumentCount() + docDelta);
        knowledge.setChunkCount(knowledge.getChunkCount() + chunkDelta);
        knowledge.setTotalTokens(knowledge.getTotalTokens() + tokenDelta);
        updateById(knowledge);
    }

    @Override
    public void refreshStats(Long knowledgeId) {
        // 1. 查询该知识库所有文档，全量重算统计
        List<Document> documents = documentService.listByKnowledgeId(knowledgeId);
        int docCount = documents.size();
        int chunkCount = 0;
        long tokenCount = 0;
        for (Document doc : documents) {
            chunkCount += doc.getChunkCount() != null ? doc.getChunkCount() : 0;
            tokenCount += doc.getTokenCount() != null ? doc.getTokenCount() : 0;
        }

        // 2. 更新知识库统计
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        knowledge.setDocumentCount(docCount);
        knowledge.setChunkCount(chunkCount);
        knowledge.setTotalTokens(tokenCount);
        updateById(knowledge);
        log.info("[知识库统计] 全量重算完成 knowledgeId=[{}], docCount={}, chunkCount={}, tokenCount={}",
                knowledgeId, docCount, chunkCount, tokenCount);
    }

    // ========== 思维导图 ==========

    @Override
    public Object generateMindmap(Long knowledgeId, Long providerId) {
        // 1. 校验知识库存在性
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }
        // 1.1 权限校验：需要DEVELOPER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 2. 获取已完成的文档列表（只取文件名和类型，不需要内容）
        List<Document> documents = documentService.listByKnowledgeId(knowledgeId).stream()
                .filter(doc -> doc.getStatus() == DocumentStatus.COMPLETED)
                .toList();
        if (documents.isEmpty()) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_DOCUMENT);
        }

        // 3. 委托MindmapUtil基于文件列表生成思维导图
        Object jsonObj = mindmapUtil.generateFromFiles(knowledge.getName(), documents, actualProviderId);

        // 4. 保存并返回
        try {
            knowledge.setMindmapData(objectMapper.writeValueAsString(jsonObj));
        } catch (Exception e) {
            log.warn("[Knowledge] 思维导图序列化失败: {}", e.getMessage());
        }
        updateById(knowledge);

        log.info("[Knowledge] 思维导图已生成: knowledgeId={}", knowledgeId);
        return jsonObj;
    }

    @Override
    public Object getMindmap(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }
        // 权限校验：需要成员权限
        checkMember(knowledgeId);
        return parseMindmapData(knowledge);
    }

    @Override
    public Object getMindmapForTool(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return null;
        }
        // 工具调用：权限已由工具层（Agent绑定关系）校验，此处不再重复校验
        return parseMindmapData(knowledge);
    }

    private Object parseMindmapData(Knowledge knowledge) {
        String data = knowledge.getMindmapData();
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(data, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[Knowledge] 思维导图JSON解析失败: {}", e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    // ========== 示例问题 ==========

    private static final String QUESTION_GEN_SYSTEM_PROMPT = """
            你是知识库测试专家。根据文档内容生成1个用户可能会问的具体问题。
            要求：10-100个字，具体明确，能通过知识库检索找到答案。问题长度严格不超过100字。
            只返回JSON格式，不要有其他内容：{"question": "..."}
            """;

    private static final int MAX_QUESTIONS = 10;

    @Override
    public void generateExampleQuestions(Long knowledgeId, Long documentId) {
        // 1. 读取知识库配置，检查是否开启自动生成
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return;
        }
        if (!isAutoGenerateEnabled(knowledge.getConfig())) {
            return;
        }

        // 2. 校验文档状态，入库失败时不生成示例问题
        Document doc = documentService.getById(documentId);
        if (doc == null || doc.getStatus() != DocumentStatus.COMPLETED) {
            return;
        }

        // 2.1 高重复文档跳过生成，避免产生重复示例问题
        if (doc.getDuplicateRate() != null) {
            double dupThreshold = parseDuplicateThreshold(knowledge.getConfig());
            if (doc.getDuplicateRate() >= dupThreshold) {
                log.info("[示例问题] 跳过生成，文档重复率 {} >= 阈值 {}, documentId={}",
                        String.format("%.1f%%", doc.getDuplicateRate() * 100),
                        String.format("%.0f%%", dupThreshold * 100), documentId);
                return;
            }
        }

        // 3. 读取文档 Markdown 内容（后台任务调用，跳过权限校验）
        String content = documentService.readDocumentContent(documentId);
        if (content == null || content.isBlank()) {
            log.warn("[示例问题] 文档内容为空, documentId={}", documentId);
            return;
        }

        // 4. 截取前3000字符
        String truncated = content.length() > 3000 ? content.substring(0, 3000) : content;
        String docName = doc.getName();

        // 5. 调用AI生成问题
        try {
            Long providerId = resolveProviderId(null);
            ChatModel chatModel = modelFactory.getChatModel(providerId);

            String userPrompt = String.format("文档名称：%s\n\n文档内容（前3000字）：\n%s", docName, truncated);
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(QUESTION_GEN_SYSTEM_PROMPT));
            messages.add(new UserMessage(userPrompt));

            ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(messages)));
            String reply = response.getResult().getOutput().getText();

            // 6. 解析JSON提取问题
            String question = parseQuestionFromJson(reply);
            if (question == null || question.isBlank()) {
                log.warn("[示例问题] AI返回格式异常, reply={}", reply);
                return;
            }

            // 7. 追加到知识库的 exampleQuestions
            appendQuestion(knowledge, question);
            log.info("[示例问题] 已生成: knowledgeId={}, documentId={}, question={}", knowledgeId, documentId, question);

        } catch (Exception e) {
            log.error("[示例问题] 生成失败: knowledgeId={}, documentId={}", knowledgeId, documentId, e);
        }
    }

    /**
     * 解析知识库配置，检查是否开启自动生成问题
     */
    private boolean isAutoGenerateEnabled(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return false;
        }
        try {
            var node = objectMapper.readTree(configJson);
            return node.has("autoGenerateQuestions") && node.get("autoGenerateQuestions").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private double parseDuplicateThreshold(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return 0.8;
        }
        try {
            var node = objectMapper.readTree(configJson);
            return node.has("duplicateThreshold") ? node.get("duplicateThreshold").asDouble(0.8) : 0.8;
        } catch (Exception e) {
            return 0.8;
        }
    }

    /**
     * 从AI回复中解析问题JSON
     */
    private String parseQuestionFromJson(String reply) {
        if (reply == null) return null;
        try {
            // 处理可能的markdown代码块包裹
            String json = reply.strip();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
            }
            var node = objectMapper.readTree(json.strip());
            return node.has("question") ? node.get("question").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将问题追加到知识库的 exampleQuestions 数组，限制最多10个
     */
    @SuppressWarnings("unchecked")
    private void appendQuestion(Knowledge knowledge, String question) {
        try {
            List<String> questions = new ArrayList<>();
            String existing = knowledge.getExampleQuestions();
            if (existing != null && !existing.isBlank() && !"[]".equals(existing)) {
                questions = objectMapper.readValue(existing, new TypeReference<>() {});
            }

            // 避免重复
            if (questions.contains(question)) {
                return;
            }

            questions.add(question);

            // 超出限制时移除最早的
            while (questions.size() > MAX_QUESTIONS) {
                questions.remove(0);
            }

            knowledge.setExampleQuestions(objectMapper.writeValueAsString(questions));
            updateById(knowledge);
        } catch (Exception e) {
            log.warn("[示例问题] 保存失败: {}", e.getMessage());
        }
    }

    /** 示例问题最大数量 */
    private static final int MAX_EXAMPLE_QUESTIONS = 10;

    @Override
    public List<String> getExampleQuestions(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            return List.of();
        }
        // 权限校验：需要成员权限
        checkMember(knowledgeId);
        return parseExampleQuestions(knowledge.getExampleQuestions());
    }

    @Override
    public void updateExampleQuestions(Long knowledgeId, List<String> questions) {
        // 权限校验：需要DEVELOPER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 最多保留10个，超出删最早的
        List<String> trimmed = new ArrayList<>(questions);
        while (trimmed.size() > MAX_EXAMPLE_QUESTIONS) {
            trimmed.remove(0);
        }

        try {
            knowledge.setExampleQuestions(objectMapper.writeValueAsString(trimmed));
        } catch (Exception e) {
            log.warn("[示例问题] 序列化失败: {}", e.getMessage());
            return;
        }
        updateById(knowledge);
        log.info("[示例问题] 更新成功: knowledgeId={}, count={}", knowledgeId, trimmed.size());
    }

    @Override
    public String generateOneExampleQuestion(Long knowledgeId) {
        // 权限校验：需要DEVELOPER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 读取第一个已完成文档的内容
        List<Document> docs = documentService.listByKnowledgeId(knowledgeId).stream()
                .filter(d -> d.getStatus() == DocumentStatus.COMPLETED)
                .toList();
        if (docs.isEmpty()) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_DOCUMENT);
        }

        Document doc = docs.get(0);
        String content = documentService.previewDocument(doc.getId());
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_DOCUMENT);
        }

        String truncated = content.length() > 3000 ? content.substring(0, 3000) : content;

        // 调用AI生成问题
        Long providerId = resolveProviderId(null);
        ChatModel chatModel = modelFactory.getChatModel(providerId);

        String userPrompt = String.format("文档名称：%s\n\n文档内容（前3000字）：\n%s", doc.getName(), truncated);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(QUESTION_GEN_SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));

        String reply;
        try {
            ChatResponse response = LlmTraceContext.callWithoutTrace(() -> chatModel.call(new Prompt(messages)));
            reply = response.getResult().getOutput().getText();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[示例问题] AI生成失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }

        String question = parseQuestionFromJson(reply);
        if (question == null || question.isBlank()) {
            log.warn("[示例问题] AI返回格式异常, reply={}", reply);
            throw new BizException(ErrorCode.AI_GENERATE_FAILED);
        }

        // 追加到列表（满10则删最早）
        appendQuestion(knowledge, question);
        log.info("[示例问题] AI生成成功: knowledgeId={}, question={}", knowledgeId, question);
        return question;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseExampleQuestions(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // ========== 权限校验 ==========

    /**
     * 校验当前用户是否为知识库成员
     */
    private void checkMember(Long knowledgeId) {
        long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole role = knowledgeMemberService.getMemberRole(knowledgeId, userId);
        if (role == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NO_PERMISSION);
        }
    }

    /**
     * 校验当前用户是否具有指定等级的角色
     */
    private void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        long userId = StpUtil.getLoginIdAsLong();
        if (!knowledgeMemberService.hasPermission(knowledgeId, userId, requiredRole)) {
            throw new BizException(ErrorCode.KNOWLEDGE_ROLE_INSUFFICIENT, requiredRole.getDesc());
        }
    }

    /**
     * 解析providerId，为空时使用默认提供商（第一个可用的）
     */
    private Long resolveProviderId(Long providerId) {
        // 1. 优先使用传入的 providerId
        if (providerId != null) {
            return providerId;
        }

        // 2. 其次使用系统默认AI配置
        var defaultConfig = systemConfigService.getDefaultAiConfig();
        if (defaultConfig.getProviderId() != null) {
            return defaultConfig.getProviderId();
        }

        // 3. 最后使用第一个可用的提供商
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        return providers.get(0);
    }

    @Override
    public IngestRequest getDefaultIngestConfig(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        IngestRequest config = new IngestRequest();

        // 从 config JSONB 中解析默认分块配置
        try {
            String configJson = knowledge.getConfig();
            if (configJson != null && !configJson.isBlank()) {
                var node = objectMapper.readTree(configJson);
                if (node.has("defaultChunkStrategy")) {
                    config.setChunkStrategy(node.get("defaultChunkStrategy").asText("general"));
                } else {
                    config.setChunkStrategy("general");
                }
                if (node.has("defaultChunkSize")) {
                    config.setChunkSize(node.get("defaultChunkSize").asInt(512));
                } else {
                    config.setChunkSize(512);
                }
                if (node.has("defaultChunkOverlap")) {
                    config.setChunkOverlap(node.get("defaultChunkOverlap").asInt(10));
                } else {
                    config.setChunkOverlap(10);
                }
                if (node.has("defaultChunkDelimiter")) {
                    config.setChunkDelimiter(node.get("defaultChunkDelimiter").asText(""));
                }
            } else {
                config.setChunkStrategy("general");
                config.setChunkSize(512);
                config.setChunkOverlap(10);
            }
        } catch (Exception e) {
            log.warn("[Knowledge] 解析默认分块配置失败, knowledgeId={}", knowledgeId, e);
            config.setChunkStrategy("general");
            config.setChunkSize(512);
            config.setChunkOverlap(10);
        }

        return config;
    }

    // ========== 检索配置 ==========

    @Override
    public Map<String, Object> getQueryParams(Long knowledgeId) {
        // 1. 权限校验：需要成员权限
        checkMember(knowledgeId);

        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 2. 解析 query_params JSONB
        Map<String, Object> params = parseJsonToMap(knowledge.getQueryParams());
        if (!params.isEmpty()) {
            return params;
        }

        // 3. 兼容旧数据：从 config 中提取 ragTopK / ragThreshold
        Map<String, Object> config = parseJsonToMap(knowledge.getConfig());
        Map<String, Object> fallback = new HashMap<>();
        if (config.containsKey("ragTopK")) {
            fallback.put("final_top_k", config.get("ragTopK"));
        }
        if (config.containsKey("ragThreshold")) {
            fallback.put("similarity_threshold", config.get("ragThreshold"));
        }
        return fallback;
    }

    @Override
    public void updateQueryParams(Long knowledgeId, Map<String, Object> params) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.MANAGER);

        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 2. 序列化并保存
        try {
            knowledge.setQueryParams(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            log.warn("[Knowledge] 检索配置序列化失败: knowledgeId={}", knowledgeId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        updateById(knowledge);
        log.info("[Knowledge] 检索配置已更新: knowledgeId={}", knowledgeId);
    }

    @Override
    public boolean isMilvusAvailable() {
        return milvusUtil.isAvailable();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
