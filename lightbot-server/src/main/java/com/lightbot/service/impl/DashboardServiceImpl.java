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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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
        long total = agentMapper.selectCount(null);
        stats.put("total", total);

        // 2. 按状态分组计数（返回带中文label的列表）
        List<Map<String, Object>> statusList = new ArrayList<>();
        for (AgentStatus status : AgentStatus.values()) {
            Long count = agentMapper.selectCount(
                    new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, status));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", status.getCode());
            item.put("label", status.getDesc());  // 中文描述
            item.put("count", count);
            statusList.add(item);
        }
        stats.put("statusList", statusList);
        // 保留旧字段兼容
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        statusList.forEach(item -> statusCounts.put((String) item.get("code"), ((Number) item.get("count")).longValue()));
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
            AgentStatus status = a.getStatus();
            item.put("status", status != null ? status.getCode() : "draft");
            item.put("statusLabel", status != null ? status.getDesc() : "草稿");
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

        // 5. 最近 3 个文档
        List<Document> recentDocs = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .orderByDesc(Document::getCreateTime)
                        .last("LIMIT 3"));
        List<Map<String, Object>> recentDocList = recentDocs.stream().map(d -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", d.getId().toString());
            item.put("name", d.getName());
            item.put("knowledgeId", d.getKnowledgeId() != null ? d.getKnowledgeId().toString() : null);
            item.put("createTime", d.getCreateTime());
            return item;
        }).collect(Collectors.toList());
        stats.put("recentDocuments", recentDocList);

        return stats;
    }

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_TREND_DAYS = 90;

    @Override
    public Map<String, Object> getChatStats(Integer days, String startDate, String endDate) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 1. 会话总数
        stats.put("totalSessions", chatSessionMapper.selectCount(null));

        // 2. 消息总数
        stats.put("totalMessages", messageMapper.selectCount(null));

        // 3. 消息趋势
        LocalDate rangeStart;
        LocalDate rangeEnd;
        if (startDate != null && !startDate.isBlank() && endDate != null && !endDate.isBlank()) {
            rangeStart = parseDate(startDate);
            rangeEnd = parseDate(endDate);
            if (rangeEnd.isBefore(rangeStart)) {
                LocalDate tmp = rangeStart;
                rangeStart = rangeEnd;
                rangeEnd = tmp;
            }
        } else {
            int trendDays = days != null && days > 0 ? Math.min(days, MAX_TREND_DAYS) : 7;
            rangeEnd = LocalDate.now();
            rangeStart = rangeEnd.minusDays(trendDays - 1L);
            stats.put("trendDays", trendDays);
        }

        long span = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
        if (span > MAX_TREND_DAYS) {
            rangeStart = rangeEnd.minusDays(MAX_TREND_DAYS - 1L);
        }

        List<Map<String, Object>> raw = messageMapper.countMessagesPerDayRange(
                rangeStart.format(DATE_FMT), rangeEnd.format(DATE_FMT));
        stats.put("messagesPerDay", fillMissingDays(raw, rangeStart, rangeEnd));
        stats.put("trendStartDate", rangeStart.format(DATE_FMT));
        stats.put("trendEndDate", rangeEnd.format(DATE_FMT));

        return stats;
    }

    private LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式无效，应为 yyyy-MM-dd: " + dateStr);
        }
    }

    private List<Map<String, Object>> fillMissingDays(List<Map<String, Object>> trend,
                                                      LocalDate start, LocalDate end) {
        Map<String, Long> countMap = new HashMap<>();
        if (trend != null) {
            for (Map<String, Object> row : trend) {
                String date = String.valueOf(row.get("date"));
                Object countObj = row.get("count");
                long count = countObj instanceof Number n ? n.longValue() : 0L;
                countMap.put(date, count);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.format(DATE_FMT);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", key);
            item.put("count", countMap.getOrDefault(key, 0L));
            result.add(item);
        }
        return result;
    }
}
