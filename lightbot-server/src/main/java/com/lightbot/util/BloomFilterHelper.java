package com.lightbot.util;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 布隆过滤器辅助类
 * <p>用于缓存穿透防护，启动时加载所有实体 ID 到布隆过滤器</p>
 * <p>查询前先用 mightExist() 快速判断 ID 是否可能存在，不存在则直接返回 null 避免穿透 DB</p>
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BloomFilterHelper {

    /** 各业务域的布隆过滤器，key = cacheName */
    private final ConcurrentHashMap<String, BloomFilter<Long>> filters = new ConcurrentHashMap<>();

    /** 预期插入量 */
    private static final long EXPECTED_INSERTIONS = 10_000L;
    /** 误判率 */
    private static final double FPP = 0.01;

    /**
     * 初始化指定业务域的布隆过滤器
     *
     * @param cacheName 缓存名称（如 "agent"、"knowledge"）
     * @param ids       该业务域所有实体 ID
     */
    public void init(String cacheName, Iterable<Long> ids) {
        BloomFilter<Long> filter = BloomFilter.create(Funnels.longFunnel(), EXPECTED_INSERTIONS, FPP);
        int count = 0;
        for (Long id : ids) {
            filter.put(id);
            count++;
        }
        filters.put(cacheName, filter);
        log.info("[BloomFilter] 初始化完成: cacheName={}, count={}", cacheName, count);
    }

    /**
     * 判断 ID 是否可能存在（false 一定不存在，true 可能存在）
     *
     * @param cacheName 缓存名称
     * @param id        实体 ID
     * @return true=可能存在，false=一定不存在
     */
    public boolean mightExist(String cacheName, Long id) {
        BloomFilter<Long> filter = filters.get(cacheName);
        if (filter == null) {
            // 未初始化时放行（降级为无布隆过滤器模式）
            return true;
        }
        return filter.mightContain(id);
    }

    /**
     * 向布隆过滤器新增一个 ID（新建实体时调用）
     *
     * @param cacheName 缓存名称
     * @param id        实体 ID
     */
    public void put(String cacheName, Long id) {
        BloomFilter<Long> filter = filters.get(cacheName);
        if (filter != null) {
            filter.put(id);
        }
    }

    /**
     * 检查指定业务域的布隆过滤器是否已初始化
     *
     * @param cacheName 缓存名称
     * @return true=已初始化
     */
    public boolean isInitialized(String cacheName) {
        return filters.containsKey(cacheName);
    }
}
