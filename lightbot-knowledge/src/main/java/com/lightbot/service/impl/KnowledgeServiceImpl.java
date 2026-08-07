package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.IngestDTO;
import com.lightbot.dto.KnowledgeSaveDTO;
import com.lightbot.dto.DifyKnowledgeConfigDTO;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import org.springframework.util.StringUtils;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.enums.KnowledgeType;
import com.lightbot.mapper.KnowledgeMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.DocumentService;
import com.lightbot.service.GraphService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.KnowledgeRetrievalService;
import com.lightbot.service.DifyDatasetClient;
import com.lightbot.service.QaPairService;
import com.lightbot.service.SystemConfigService;
import com.lightbot.util.JsonUtil;
import com.lightbot.util.LlmTraceContext;
import com.lightbot.util.MilvusUtil;
import com.lightbot.util.MindmapUtil;
import com.lightbot.util.DifySecretCipher;
import com.lightbot.vo.DifyConnectionTestVO;
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
import java.util.LinkedHashMap;
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
    private final QaPairService qaPairService;
    private final GraphService graphService;
    private final ModelFactory modelFactory;
    private final ObjectMapper objectMapper;
    private final MindmapUtil mindmapUtil;
    private final SystemConfigService systemConfigService;
    private final MilvusUtil milvusUtil;
    private final DifySecretCipher difySecretCipher;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final DifyDatasetClient difyDatasetClient;

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
    public Knowledge create(KnowledgeSaveDTO request) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.KNOWLEDGE_NAME_EXISTS);
        }

        // 2. DTO → Entity，仅设置用户可编辑字段
        Knowledge knowledge = new Knowledge();
        knowledge.setName(request.getName());
        knowledge.setDescription(request.getDescription());
        knowledge.setEmbeddingModel(request.getEmbeddingModel());
        knowledge.setType(request.getType());
        knowledge.setConfig(buildKnowledgeConfig(request, true, null));
        knowledge.setQueryParams(request.getQueryParams());
        // graphEnabled 前端新建时可不传；三元两侧类型需同为 Boolean，避免 false 触发拆箱 NPE
        knowledge.setGraphEnabled(request.getType() == KnowledgeType.DIFY
                ? Boolean.FALSE
                : (request.getGraphEnabled() != null ? request.getGraphEnabled() : Boolean.FALSE));

        // 3. 初始化内部字段
        knowledge.setUserId(userId);
        knowledge.setStatus(CommonStatus.ACTIVE);
        knowledge.setDocumentCount(0);
        knowledge.setChunkCount(0);
        knowledge.setTotalTokens(0L);
        save(knowledge);

        // 4. Dify Token 独立加密保存，避免通过知识库详情或日志泄露。
        if (knowledge.getType() == KnowledgeType.DIFY) {
            // 新建即校验真实连通性；失败时事务回滚，不留下不可用的外部知识库。
            knowledgeRetrievalService.retrieve(knowledge, "智元 connection test", 1, 0.0, Map.of());
        }

        // 5. 企业资产：不再写入成员表；userId 仅作创建人审计
        return sanitizeForResponse(knowledge);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Knowledge update(KnowledgeSaveDTO request) {
        // 1. 企业资产：登录建设者可维护（checkPermission 已放宽为存在性校验）
        checkPermission(request.getId(), KnowledgeRole.MANAGER);

        // 2. 校验存在性
        Knowledge existing = getById(request.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 名称变更时校验唯一性
        if (!existing.getName().equals(request.getName())) {
            long count = count(new LambdaQueryWrapper<Knowledge>().eq(Knowledge::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.KNOWLEDGE_NAME_EXISTS);
            }
        }

        // 4. 更新允许修改的字段
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        if (existing.getType() != KnowledgeType.DIFY) {
            existing.setEmbeddingModel(request.getEmbeddingModel());
            existing.setGraphEnabled(request.getGraphEnabled());
            existing.setConfig(request.getConfig());
        } else {
            existing.setEmbeddingModel(null);
            existing.setGraphEnabled(false);
            existing.setConfig(buildKnowledgeConfig(request, false, existing.getConfig()));
            knowledgeRetrievalService.retrieve(existing, "智元 connection test", 1, 0.0, Map.of());
        }
        updateById(existing);
        return sanitizeForResponse(existing);
    }

    @Override
    public Page<Knowledge> listMyKnowledge(int pageNum, int pageSize, String name) {
        // 企业资产：建设者可见全部启用中的知识库（不再按成员表过滤）
        Page<Knowledge> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Knowledge>()
                        .eq(Knowledge::getStatus, CommonStatus.ACTIVE)
                        .like(StringUtils.hasText(name), Knowledge::getName, name)
                        .orderByDesc(Knowledge::getCreateTime));
        page.setRecords(page.getRecords().stream().map(this::sanitizeForResponse).toList());
        return page;
    }

    @Override
    public List<Knowledge> listCreatedByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getUserId, userId)
                .orderByDesc(Knowledge::getCreateTime))
                .stream()
                .map(this::sanitizeForResponse)
                .toList();
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

        // 3. 级联删除所有子文档（一次 IN 删 chunk/embedding/document_versions/document + MinIO 文件，
        //    替代 N 次 deleteDocument 循环；图谱/QaPair 由步骤 4/5 按知识库整体级联）。
        //    失败直接抛：文档与知识库强耦合，吞异常会让主表已删而子表残留，违反 2.4 数据一致性目标。
        documentService.deleteByKnowledgeIdCascade(id);

        // 4. 级联删除问答对（含 Milvus 向量）——可选子系统，软失败不阻塞主流程
        safeRemove(() -> qaPairService.deleteByKnowledgeId(id), "QaPair");

        // 5. 级联删除图谱（Neo4j + DB）——可选子系统，软失败不阻塞主流程
        safeRemove(() -> graphService.deleteByKnowledgeIdInternal(id), "KnowledgeGraph");

        // 6. 逻辑删除知识库；Dify 密文随 knowledge.config 一起删除。
        removeById(id);

        // 7. 同时删除所有成员关系
        knowledgeMemberService.removeByKnowledgeId(id);
    }

    @Override
    public Knowledge getByIdWithPermission(Long id) {
        // 1. 权限校验：需要成员权限
        checkMember(id);

        // 2. 返回知识库详情
        return sanitizeForResponse(getById(id));
    }

    @Override
    public void updateStats(Long knowledgeId, int docDelta, int chunkDelta, long tokenDelta) {
        // 原子递增，避免并发竞态导致计数丢失
        baseMapper.incrementStats(knowledgeId, docDelta, chunkDelta, tokenDelta);
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
     * 校验知识库存在（企业资产：任意登录建设者可访问）
     */
    private void checkMember(Long knowledgeId) {
        if (getById(knowledgeId) == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
    }

    /**
     * 校验知识库写权限（企业资产：建设者共同维护，不再按成员角色阶梯限制）
     */
    private void checkPermission(Long knowledgeId, KnowledgeRole requiredRole) {
        checkMember(knowledgeId);
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
    public IngestDTO getDefaultIngestConfig(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        IngestDTO config = new IngestDTO();

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
    @CacheEvict(value = RedisCacheConfig.CACHE_KNOWLEDGE, key = "#knowledgeId")
    public void updateQueryParams(Long knowledgeId, Map<String, Object> params) {
        // 1. 权限校验：需要MANAGER及以上权限
        checkPermission(knowledgeId, KnowledgeRole.MANAGER);

        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 2. 按知识库类型归一化检索参数，避免 PG/Milvus 专属配置互相污染
        Map<String, Object> normalizedParams = normalizeQueryParams(knowledge, params);

        // 3. 序列化并保存
        try {
            knowledge.setQueryParams(objectMapper.writeValueAsString(normalizedParams));
        } catch (Exception e) {
            log.warn("[Knowledge] 检索配置序列化失败: knowledgeId={}", knowledgeId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        updateById(knowledge);
        log.info("[Knowledge] 检索配置已更新: knowledgeId={}", knowledgeId);
    }

    /**
     * 归一化检索配置：保存前按知识库类型过滤字段、补齐默认值并限制数值范围。
     */
    private Map<String, Object> normalizeQueryParams(Knowledge knowledge, Map<String, Object> params) {
        Map<String, Object> source = params != null ? params : Map.of();
        if (knowledge.getType() == KnowledgeType.DIFY) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("search_mode", normalizeSearchMode(source.get("search_mode")));
            normalized.put("final_top_k", intParam(source, "final_top_k", 5, 1, 20));
            normalized.put("score_threshold_enabled", boolParam(source, "score_threshold_enabled", true));
            normalized.put("similarity_threshold", doubleParam(source, "similarity_threshold", 0.0, 0.0, 1.0));
            return normalized;
        }
        boolean milvus = knowledge.getType() == KnowledgeType.MILVUS;
        Map<String, Object> normalized = new LinkedHashMap<>();

        normalized.put("search_mode", normalizeSearchMode(source.get("search_mode")));
        normalized.put("final_top_k", intParam(source, "final_top_k", milvus ? 10 : 5, 1, 100));
        normalized.put("similarity_threshold", doubleParam(source, "similarity_threshold", milvus ? 0.0 : 0.5, 0.0, 1.0));
        normalized.put("query_rewrite", boolParam(source, "query_rewrite", false));
        normalized.put("vector_weight", doubleParam(source, "vector_weight", 0.7, 0.0, 1.0));

        if (milvus) {
            normalized.put("bm25_weight", doubleParam(source, "bm25_weight", 0.3, 0.0, 1.0));
            normalized.put("bm25_top_k", intParam(source, "bm25_top_k", 30, 1, 200));
            normalized.put("bm25_drop_ratio_search", doubleParam(source, "bm25_drop_ratio_search", 0.0, 0.0, 1.0));
        } else {
            normalized.put("keyword_weight", doubleParam(source, "keyword_weight", 0.3, 0.0, 1.0));
        }

        normalized.put("use_reranker", boolParam(source, "use_reranker", false));
        normalized.put("reranker_model", stringParam(source, "reranker_model", ""));
        normalized.put("recall_top_k", intParam(source, "recall_top_k", 50, 1, 200));
        normalized.put("qa_enabled", boolParam(source, "qa_enabled", true));
        normalized.put("qa_top_k", intParam(source, "qa_top_k", 3, 1, 20));
        normalized.put("qa_threshold", doubleParam(source, "qa_threshold", 0.85, 0.0, 1.0));
        normalized.put("qa_priority", boolParam(source, "qa_priority", true));

        if (milvus) {
            normalized.put("use_graph_retrieval", boolParam(source, "use_graph_retrieval", false));
            normalized.put("graph_entity_top_k", intParam(source, "graph_entity_top_k", 10, 1, 100));
            normalized.put("graph_triple_top_k", intParam(source, "graph_triple_top_k", 10, 1, 100));
            normalized.put("graph_max_nodes", intParam(source, "graph_max_nodes", 100, 10, 500));
            normalized.put("graph_top_k", intParam(source, "graph_top_k", 5, 1, 50));
            normalized.put("graph_weight", doubleParam(source, "graph_weight", 0.3, 0.0, 1.0));
            normalized.put("ppr_damping", doubleParam(source, "ppr_damping", 0.85, 0.0, 1.0));
        }

        return normalized;
    }

    private String normalizeSearchMode(Object value) {
        if (value instanceof String s) {
            String mode = s.trim().toLowerCase();
            if ("vector".equals(mode) || "keyword".equals(mode) || "hybrid".equals(mode)) {
                return mode;
            }
        }
        return "vector";
    }

    private String stringParam(Map<String, Object> source, String key, String defaultValue) {
        Object value = source.get(key);
        return value instanceof String s ? s.trim() : defaultValue;
    }

    private boolean boolParam(Map<String, Object> source, String key, boolean defaultValue) {
        Object value = source.get(key);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return defaultValue;
    }

    private int intParam(Map<String, Object> source, String key, int defaultValue, int min, int max) {
        Object value = source.get(key);
        int parsed = value instanceof Number n ? n.intValue() : defaultValue;
        return Math.max(min, Math.min(max, parsed));
    }

    private double doubleParam(Map<String, Object> source, String key, double defaultValue, double min, double max) {
        Object value = source.get(key);
        double parsed = value instanceof Number n ? n.doubleValue() : defaultValue;
        return Math.max(min, Math.min(max, parsed));
    }

    @Override
    public boolean isMilvusAvailable() {
        return milvusUtil.isAvailable();
    }

    @Override
    public DifyConnectionTestVO testDifyConnection(Long knowledgeId) {
        // 1. 成员权限与类型校验
        checkMember(knowledgeId);
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
        if (knowledge.getType() != KnowledgeType.DIFY) {
            throw new BizException(ErrorCode.DIFY_CONFIG_INVALID);
        }

        // 2. 调用 Dify retrieve 接口；空结果同样表示连接和鉴权成功。
        knowledgeRetrievalService.retrieve(knowledge, "智元 connection test", 1, 0.0, Map.of());

        // 3. 只返回非敏感的连接结果。
        DifyConnectionTestVO result = new DifyConnectionTestVO();
        result.setConnected(true);
        result.setDatasetId(String.valueOf(parseJsonToMap(knowledge.getConfig()).get("datasetId")));
        return result;
    }

    @Override
    public DifyConnectionTestVO testDifyConnection(DifyKnowledgeConfigDTO config) {
        // 1. 草稿测试只校验并调用远端，不保存知识库或 Token。
        if (config == null || !StringUtils.hasText(config.getApiUrl())
                || !StringUtils.hasText(config.getDatasetId()) || !StringUtils.hasText(config.getToken())) {
            throw new BizException(ErrorCode.DIFY_CONFIG_INVALID);
        }

        // 2. 空结果仍代表地址、Dataset 与鉴权均有效。
        difyDatasetClient.retrieve(config.getApiUrl().trim(), config.getDatasetId().trim(), config.getToken().trim(),
                "智元 connection test", 1, 0.0, "vector");

        DifyConnectionTestVO result = new DifyConnectionTestVO();
        result.setConnected(true);
        result.setDatasetId(config.getDatasetId().trim());
        return result;
    }

    private String buildKnowledgeConfig(KnowledgeSaveDTO request, boolean tokenRequired, String currentConfig) {
        boolean difyKnowledge = request.getType() == KnowledgeType.DIFY
                || "dify".equals(parseJsonToMap(currentConfig).get("connector"));
        if (!difyKnowledge) {
            return StringUtils.hasText(request.getConfig()) ? request.getConfig() : "{}";
        }
        DifyKnowledgeConfigDTO difyConfig = request.getDifyConfig();
        if (difyConfig == null || !StringUtils.hasText(difyConfig.getApiUrl())
                || !StringUtils.hasText(difyConfig.getDatasetId())) {
            throw new BizException(ErrorCode.DIFY_CONFIG_INVALID);
        }
        String tokenCiphertext = StringUtils.hasText(difyConfig.getToken())
                ? difySecretCipher.encrypt(difyConfig.getToken().trim())
                : String.valueOf(parseJsonToMap(currentConfig).getOrDefault("tokenCiphertext", ""));
        if ((tokenRequired || !StringUtils.hasText(tokenCiphertext)) && !StringUtils.hasText(tokenCiphertext)) {
            throw new BizException(ErrorCode.DIFY_CONFIG_INVALID);
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("connector", "dify");
        config.put("apiUrl", difyConfig.getApiUrl().trim().replaceAll("/+$", ""));
        config.put("datasetId", difyConfig.getDatasetId().trim());
        config.put("tokenCiphertext", tokenCiphertext);
        config.put("tokenConfigured", true);
        config.put("readOnly", true);
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DIFY_CONFIG_INVALID, e);
        }
    }

    private Knowledge sanitizeForResponse(Knowledge knowledge) {
        if (knowledge == null || knowledge.getType() != KnowledgeType.DIFY) {
            return knowledge;
        }
        Knowledge response = new Knowledge();
        org.springframework.beans.BeanUtils.copyProperties(knowledge, response);
        Map<String, Object> config = new LinkedHashMap<>(parseJsonToMap(knowledge.getConfig()));
        config.remove("tokenCiphertext");
        config.put("tokenConfigured", true);
        try {
            response.setConfig(objectMapper.writeValueAsString(config));
        } catch (Exception e) {
            response.setConfig("{\"tokenConfigured\":true}");
        }
        return response;
    }

    private Map<String, Object> parseJsonToMap(String json) {
        return JsonUtil.parseJsonToMap(objectMapper, json);
    }

    private void safeRemove(Runnable action, String label) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[知识库删除] {}清理失败, 跳过: {}", label, e.getMessage());
        }
    }
}
