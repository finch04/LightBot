package com.lightbot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lightbot.entity.EvalRagBenchmark;
import com.lightbot.entity.EvalRagBenchmarkItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RAG 评估基准服务接口
 *
 * @author finch
 * @since 2026-05-28
 */
public interface EvalRagBenchmarkService extends IService<EvalRagBenchmark> {

    /**
     * 查询知识库的评估基准列表
     */
    List<EvalRagBenchmark> listByKnowledgeId(Long knowledgeId);

    /**
     * 获取基准详情（含分页题目）
     */
    Page<EvalRagBenchmarkItem> getBenchmarkDetail(Long benchmarkId, int pageNum, int pageSize);

    /**
     * 创建空基准记录（用于异步任务）
     */
    EvalRagBenchmark createEmptyBenchmark(Long knowledgeId, String name, String description);

    /**
     * AI 生成基准题目并填充到已有基准记录（异步任务调用）
     */
    void generateBenchmarkItems(Long benchmarkId, Long knowledgeId, Integer count,
                                Long providerId, String modelId, Integer neighborCount);

    /**
     * 上传 JSONL 评估基准
     */
    EvalRagBenchmark uploadBenchmark(Long knowledgeId, String name, String description, MultipartFile file);

    /**
     * 删除评估基准
     */
    void deleteBenchmark(Long knowledgeId, Long benchmarkId);

    /**
     * 下载基准为 JSONL
     */
    String downloadBenchmarkAsJsonl(Long benchmarkId);

    /**
     * 获取基准的所有题目
     */
    List<EvalRagBenchmarkItem> listItemsByBenchmarkId(Long benchmarkId);
}
