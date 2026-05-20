package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lightbot.entity.*;
import com.lightbot.enums.AgentStatus;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.mapper.*;
import com.lightbot.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard统计服务实现类
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AgentMapper agentMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final DocumentMapper documentMapper;
    private final ChunkMapper chunkMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final MessageMapper messageMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final ModelMapper modelMapper;

    @Override
    public Map<String, Object> getBasicStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("agentCount", agentMapper.selectCount(null));
        stats.put("knowledgeCount", knowledgeMapper.selectCount(null));
        stats.put("sessionCount", chatSessionMapper.selectCount(null));
        stats.put("messageCount", messageMapper.selectCount(null));
        stats.put("providerCount", modelProviderMapper.selectCount(null));
        stats.put("modelCount", modelMapper.selectCount(null));
        stats.put("documentCount", documentMapper.selectCount(null));
        stats.put("chunkCount", chunkMapper.selectCount(null));
        return stats;
    }

    @Override
    public Map<String, Object> getAgentStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. 总数
        stats.put("total", agentMapper.selectCount(null));

        // 2. 按状态分组计数
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (AgentStatus status : AgentStatus.values()) {
            Long count = agentMapper.selectCount(
                    new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, status));
            statusCounts.put(status.getCode(), count);
        }
        stats.put("statusCounts", statusCounts);

        // 3. 最近5个Agent
        List<Agent> recent = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>()
                        .orderByDesc(Agent::getCreateTime)
                        .last("LIMIT 5"));
        List<Map<String, Object>> recentList = recent.stream().map(a -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId().toString());
            item.put("name", a.getName());
            item.put("status", a.getStatus() != null ? a.getStatus().getCode() : "draft");
            item.put("createTime", a.getCreateTime());
            return item;
        }).collect(Collectors.toList());
        stats.put("recent", recentList);

        return stats;
    }

    @Override
    public Map<String, Object> getKnowledgeStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. 知识库总数
        stats.put("totalKnowledge", knowledgeMapper.selectCount(null));

        // 2. 文档总数
        stats.put("totalDocuments", documentMapper.selectCount(null));

        // 3. 分块总数
        stats.put("totalChunks", chunkMapper.selectCount(null));

        // 4. 文档按状态分组计数
        Map<String, Long> docStatusCounts = new LinkedHashMap<>();
        for (DocumentStatus status : DocumentStatus.values()) {
            Long count = documentMapper.selectCount(
                    new LambdaQueryWrapper<Document>().eq(Document::getStatus, status));
            docStatusCounts.put(status.getCode(), count);
        }
        stats.put("documentStatusCounts", docStatusCounts);

        return stats;
    }

    @Override
    public Map<String, Object> getChatStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. 会话总数
        stats.put("totalSessions", chatSessionMapper.selectCount(null));

        // 2. 消息总数
        stats.put("totalMessages", messageMapper.selectCount(null));

        // 3. 近7天消息趋势
        List<Map<String, Object>> trend = messageMapper.countMessagesPerDay(7);
        stats.put("messagesPerDay", trend);

        return stats;
    }
}
