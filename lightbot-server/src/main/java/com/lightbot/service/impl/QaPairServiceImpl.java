package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.QaPairCreateDTO;
import com.lightbot.dto.QaPairSearchResultVO;
import com.lightbot.dto.QaPairUpdateDTO;
import com.lightbot.dto.QaPairVO;
import com.lightbot.entity.Knowledge;
import com.lightbot.entity.ModelProvider;
import com.lightbot.entity.QaPair;
import com.lightbot.entity.Task;
import com.lightbot.enums.ErrorCode;
import com.lightbot.enums.KnowledgeRole;
import com.lightbot.enums.QaPairSource;
import com.lightbot.enums.QaPairStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.mapper.EmbeddingMapper;
import com.lightbot.mapper.QaPairMapper;
import com.lightbot.service.KnowledgeMemberService;
import com.lightbot.service.KnowledgeService;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.QaPairService;
import com.lightbot.service.TaskService;
import com.lightbot.util.QaPairVectorizeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问答对服务实现类
 *
 * @author finch
 * @since 2026-05-29
 */
@Slf4j
@Service
public class QaPairServiceImpl extends ServiceImpl<QaPairMapper, QaPair>
        implements QaPairService {

    @Autowired
    private EmbeddingMapper embeddingMapper;

    @Autowired
    private KnowledgeMemberService permissionHelper;

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QaPairVectorizeUtil vectorizeHelper;

    @Autowired
    private ModelProviderService modelProviderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaPairVO create(Long knowledgeId, QaPairCreateDTO dto) {
        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        // 2. 保存问答对
        QaPair qaPair = new QaPair();
        qaPair.setKnowledgeId(knowledgeId);
        qaPair.setQuestion(dto.getQuestion());
        qaPair.setAnswer(dto.getAnswer());
        qaPair.setSource(QaPairSource.MANUAL);
        qaPair.setStatus(QaPairStatus.PENDING);
        qaPair.setTokenCount(estimateTokenCount(dto.getQuestion()));
        save(qaPair);

        // 3. 异步向量化
        asyncVectorize(qaPair.getId(), dto.getQuestion());

        log.info("[QaPair] 创建成功: id={}, knowledgeId={}", qaPair.getId(), knowledgeId);
        return toVO(qaPair);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaPairVO update(QaPairUpdateDTO dto) {
        QaPair existing = getById(dto.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(existing.getKnowledgeId(), KnowledgeRole.DEVELOPER);

        // 2. 更新字段
        boolean questionChanged = false;
        if (StringUtils.hasText(dto.getQuestion()) && !dto.getQuestion().equals(existing.getQuestion())) {
            existing.setQuestion(dto.getQuestion());
            existing.setTokenCount(estimateTokenCount(dto.getQuestion()));
            questionChanged = true;
        }
        if (StringUtils.hasText(dto.getAnswer())) {
            existing.setAnswer(dto.getAnswer());
        }
        updateById(existing);

        // 3. question 变更时重新向量化
        if (questionChanged) {
            // 删除旧向量
            embeddingMapper.deleteByQaPairId(existing.getId());
            // 异步重新向量化
            asyncVectorize(existing.getId(), existing.getQuestion());
            existing.setStatus(QaPairStatus.PENDING);
            updateById(existing);
        }

        log.info("[QaPair] 更新成功: id={}", existing.getId());
        return toVO(existing);
    }

    @Override
    public Page<QaPairVO> listByKnowledgeId(Long knowledgeId, int pageNum, int pageSize, String keyword) {
        // 1. 权限校验：MEMBER+
        permissionHelper.checkMember(knowledgeId);

        // 2. 构建查询条件
        LambdaQueryWrapper<QaPair> wrapper = new LambdaQueryWrapper<QaPair>()
                .eq(QaPair::getKnowledgeId, knowledgeId)
                .like(StringUtils.hasText(keyword), QaPair::getQuestion, keyword)
                .orderByDesc(QaPair::getCreateTime);

        Page<QaPair> page = page(new Page<>(pageNum, pageSize), wrapper);

        // 3. 转换为 VO
        Page<QaPairVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        QaPair qaPair = getById(id);
        if (qaPair == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(qaPair.getKnowledgeId(), KnowledgeRole.DEVELOPER);

        // 2. 删除关联向量
        embeddingMapper.deleteByQaPairId(id);

        // 3. 删除问答对
        removeById(id);

        log.info("[QaPair] 删除成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImport(Long knowledgeId, List<QaPairCreateDTO> items) {
        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);
        return doBatchImport(knowledgeId, items, QaPairSource.IMPORT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchImportInternal(Long knowledgeId, List<QaPairCreateDTO> items) {
        return doBatchImport(knowledgeId, items, QaPairSource.AI);
    }

    private int doBatchImport(Long knowledgeId, List<QaPairCreateDTO> items, QaPairSource source) {
        // 1. 批量保存
        List<QaPair> qaPairs = new ArrayList<>();
        for (QaPairCreateDTO item : items) {
            QaPair qaPair = new QaPair();
            qaPair.setKnowledgeId(knowledgeId);
            qaPair.setQuestion(item.getQuestion());
            qaPair.setAnswer(item.getAnswer());
            qaPair.setSource(source);
            qaPair.setStatus(QaPairStatus.PENDING);
            qaPair.setTokenCount(estimateTokenCount(item.getQuestion()));
            qaPairs.add(qaPair);
        }
        saveBatch(qaPairs);

        // 2. 异步批量向量化
        for (QaPair qaPair : qaPairs) {
            asyncVectorize(qaPair.getId(), qaPair.getQuestion());
        }

        log.info("[QaPair] 批量导入成功: knowledgeId={}, count={}, source={}", knowledgeId, qaPairs.size(), source);
        return qaPairs.size();
    }

    @Override
    public Task generateByAI(Long knowledgeId, Integer count, Long providerId, String modelId) {
        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(knowledgeId, KnowledgeRole.DEVELOPER);

        // 2. 校验知识库存在性
        Knowledge knowledge = knowledgeService.getById(knowledgeId);
        if (knowledge == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }

        // 3. 限制单次最多20条
        int actualCount = Math.min(count != null ? count : 10, 20);

        // 4. 创建异步任务
        try {
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("knowledgeId", knowledgeId);
            payloadMap.put("count", actualCount);
            if (providerId != null) {
                payloadMap.put("providerId", providerId);
                ModelProvider provider = modelProviderService.getById(providerId);
                if (provider != null) {
                    payloadMap.put("providerName", provider.getName());
                }
            }
            if (modelId != null && !modelId.isBlank()) {
                payloadMap.put("modelId", modelId);
            }
            String payload = objectMapper.writeValueAsString(payloadMap);
            Task task = taskService.createTask(
                    TaskType.QA_PAIR_GENERATE,
                    "问答对生成 - " + knowledge.getName(),
                    cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong(),
                    knowledgeId,
                    payload);
            log.info("[QaPair] AI生成任务已创建: knowledgeId={}, taskId={}", knowledgeId, task.getId());
            return task;
        } catch (Exception e) {
            log.error("[QaPair] 创建AI生成任务失败: knowledgeId={}", knowledgeId, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Override
    public void vectorize(Long qaPairId) {
        QaPair qaPair = getById(qaPairId);
        if (qaPair == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }

        // 1. 权限校验：DEVELOPER+
        permissionHelper.checkPermission(qaPair.getKnowledgeId(), KnowledgeRole.DEVELOPER);

        // 2. 仅允许对 pending 或 failed 状态的问答对触发向量化
        if (qaPair.getStatus() != QaPairStatus.PENDING && qaPair.getStatus() != QaPairStatus.FAILED) {
            return;
        }

        // 3. 删除旧向量（failed 状态可能有残留）
        embeddingMapper.deleteByQaPairId(qaPairId);

        // 4. 异步向量化
        asyncVectorize(qaPairId, qaPair.getQuestion());

        log.info("[QaPair] 手动触发向量化: qaPairId={}", qaPairId);
    }

    @Override
    public int batchVectorize(List<Long> qaPairIds) {
        int count = 0;
        for (Long qaPairId : qaPairIds) {
            try {
                vectorize(qaPairId);
                count++;
            } catch (Exception e) {
                log.warn("[QaPair] 批量向量化跳过: qaPairId={}, reason={}", qaPairId, e.getMessage());
            }
        }
        log.info("[QaPair] 批量向量化完成: total={}, success={}", qaPairIds.size(), count);
        return count;
    }

    @Override
    public List<QaPairSearchResultVO> searchSimilar(Long knowledgeId, float[] queryVector, int topK, double threshold) {
        String vectorStr = toVectorString(queryVector);
        List<Map<String, Object>> results = embeddingMapper.searchSimilarQaPairs(vectorStr, knowledgeId, topK, threshold);

        return results.stream().map(row -> {
            QaPairSearchResultVO vo = new QaPairSearchResultVO();
            Object id = row.get("id");
            vo.setId(id != null ? ((Number) id).longValue() : null);
            vo.setQuestion((String) row.get("question"));
            vo.setAnswer((String) row.get("answer"));
            Object score = row.get("score");
            vo.setScore(score != null ? ((Number) score).doubleValue() : null);
            return vo;
        }).toList();
    }

    /**
     * 触发向量化（独立事务执行，失败不影响外部事务）
     */
    private void asyncVectorize(Long qaPairId, String question) {
        try {
            vectorizeHelper.doVectorizeInNewTransaction(qaPairId, question);
        } catch (Exception e) {
            log.error("[QaPair] 向量化失败: qaPairId={}", qaPairId, e);
            vectorizeHelper.markFailed(qaPairId, e.getMessage());
        }
    }

    /**
     * 将 QaPair 实体转换为 VO
     */
    private QaPairVO toVO(QaPair qaPair) {
        QaPairVO vo = new QaPairVO();
        vo.setId(qaPair.getId());
        vo.setKnowledgeId(qaPair.getKnowledgeId());
        vo.setQuestion(qaPair.getQuestion());
        vo.setAnswer(qaPair.getAnswer());
        vo.setSource(qaPair.getSource() != null ? qaPair.getSource().getCode() : null);
        vo.setStatus(qaPair.getStatus() != null ? qaPair.getStatus().getDesc() : null);
        vo.setTokenCount(qaPair.getTokenCount());
        vo.setCreateTime(qaPair.getCreateTime());
        return vo;
    }

    /**
     * 简单估算 token 数量（中文约1.5字/token，英文约4字符/token）
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        int chineseCount = 0;
        int otherCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            } else {
                otherCount++;
            }
        }
        return (int) (chineseCount / 1.5 + otherCount / 4.0);
    }

    /**
     * 将float数组转换为pgvector可识别的字符串格式 "[0.1,0.2,...]"
     */
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
