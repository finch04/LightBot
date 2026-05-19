package com.lightbot.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lightbot.common.BizException;
import com.lightbot.entity.Chunk;
import com.lightbot.entity.Document;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.KnowledgeMember;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.mapper.ChunkMapper;
import com.lightbot.mapper.KnowledgeMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.service.DocumentService;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    private final ChunkMapper chunkMapper;
    private final ModelFactory modelFactory;

    private static final String MINDMAP_SYSTEM_PROMPT = "你是一个思维导图生成助手，只输出JSON，不要任何其他文字。";

    private static final String MINDMAP_USER_TEMPLATE = """
            请根据以下知识库内容，生成一个思维导图的 JSON 结构。
            要求：
            1. 根节点的 content 为知识库名称
            2. children 数组包含子节点，每个子节点有 content 和可选的 children
            3. 层级不超过 3 层，每个节点的内容简洁明了
            4. 可以使用emoji图标增强可读性
            5. 只输出 JSON，不要任何其他文字

            知识库名称：{name}
            知识库描述：{description}

            知识库内容摘要：
            {content}

            输出格式示例：
            {{"content": "知识库名称", "children": [{{"content": "主题1", "children": [{{"content": "子主题1"}}]}}, {{"content": "主题2"}}]}}
            """;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Knowledge create(Knowledge knowledge) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 初始化知识库字段
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

        // 3. 更新允许修改的字段
        existing.setName(knowledge.getName());
        existing.setDescription(knowledge.getDescription());
        existing.setEmbeddingModel(knowledge.getEmbeddingModel());
        existing.setChunkSize(knowledge.getChunkSize());
        existing.setChunkOverlap(knowledge.getChunkOverlap());
        existing.setConfig(knowledge.getConfig());
        updateById(existing);
        return existing;
    }

    @Override
    public Page<Knowledge> listMyKnowledge(int pageNum, int pageSize) {
        long userId = StpUtil.getLoginIdAsLong();

        // 1. 查询用户加入的所有知识库ID
        List<Long> knowledgeIds = knowledgeMemberService.listKnowledgeIdsByUserId(userId);
        if (knowledgeIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 2. 分页查询这些知识库
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Knowledge>()
                        .in(Knowledge::getId, knowledgeIds)
                        .eq(Knowledge::getStatus, CommonStatus.ACTIVE)
                        .orderByDesc(Knowledge::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        // 1. 权限校验：仅CREATOR可删除
        checkPermission(id, KnowledgeRole.CREATOR);

        // 2. 校验存在性
        Knowledge knowledge = getById(id);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 逻辑删除知识库
        removeById(id);

        // 4. 同时删除所有成员关系
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

    // ========== 思维导图 ==========

    @Override
    public String generateMindmap(Long knowledgeId, Long providerId) {
        // 1. 校验知识库存在性
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }

        // 1.1 解析providerId（为空时使用默认提供商）
        Long actualProviderId = resolveProviderId(providerId);

        // 2. 收集知识库内容摘要
        String contentSummary = collectContentSummary(knowledgeId);

        // 3. 构建提示词
        String userMessage = MINDMAP_USER_TEMPLATE
                .replace("{name}", knowledge.getName())
                .replace("{description}", knowledge.getDescription() != null ? knowledge.getDescription() : "暂无描述")
                .replace("{content}", contentSummary);

        // 4. 通过 ModelFactory 获取 ChatModel 并调用
        ChatModel chatModel = modelFactory.getChatModel(actualProviderId);
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(MINDMAP_SYSTEM_PROMPT));
        messages.add(new UserMessage(userMessage));

        ChatResponse response = chatModel.call(new Prompt(messages));
        String json = response.getResult().getOutput().getText().trim();

        // 5. 清理AI返回的JSON（去除可能的markdown代码块标记）
        json = cleanJsonResponse(json);

        // 6. 保存到知识库
        knowledge.setMindmapData(json);
        updateById(knowledge);

        log.info("[Knowledge] 思维导图已生成: knowledgeId={}", knowledgeId);
        return json;
    }

    @Override
    public String getMindmap(Long knowledgeId) {
        Knowledge knowledge = getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.RAG_KNOWLEDGE_NOT_FOUND);
        }
        return knowledge.getMindmapData();
    }

    /**
     * 收集知识库内容摘要：遍历文档，每个文档取前3个chunk，总长度限制3000字符
     */
    private String collectContentSummary(Long knowledgeId) {
        List<Document> documents = documentService.listByKnowledgeId(knowledgeId);
        if (documents.isEmpty()) {
            return "暂无文档内容";
        }

        StringBuilder summary = new StringBuilder();
        for (Document doc : documents) {
            if (summary.length() > 3000) {
                break;
            }
            summary.append("【文档：").append(doc.getName()).append("】\n");

            // 取前3个chunk
            List<Chunk> chunks = chunkMapper.selectList(
                    new LambdaQueryWrapper<Chunk>()
                            .eq(Chunk::getDocumentId, doc.getId())
                            .orderByAsc(Chunk::getChunkIndex)
                            .last("LIMIT 3"));

            for (Chunk chunk : chunks) {
                if (summary.length() > 3000) {
                    break;
                }
                // 每个chunk截取前500字符
                String content = chunk.getContent();
                if (content != null) {
                    summary.append(content, 0, Math.min(content.length(), 500)).append("\n");
                }
            }
            summary.append("\n");
        }

        return summary.length() > 3000 ? summary.substring(0, 3000) : summary.toString();
    }

    /**
     * 清理AI返回的JSON（去除markdown代码块标记）
     */
    private String cleanJsonResponse(String json) {
        if (json == null) {
            return null;
        }
        json = json.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        return json.trim();
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
        if (providerId != null) {
            return providerId;
        }
        var providers = modelFactory.getAvailableProviderIds();
        if (providers.isEmpty()) {
            throw new BizException(ErrorCode.MODEL_PROVIDER_NOT_FOUND);
        }
        return providers.get(0);
    }
}
