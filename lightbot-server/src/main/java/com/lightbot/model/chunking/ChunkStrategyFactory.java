package com.lightbot.model.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分块策略工厂
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class ChunkStrategyFactory {

    private final Map<String, ChunkStrategy> strategyMap;

    public ChunkStrategyFactory(List<ChunkStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(ChunkStrategy::getType, Function.identity()));
        log.info("[ChunkStrategyFactory] 已注册分块策略: {}", strategyMap.keySet());
    }

    /**
     * 获取分块策略
     *
     * @param type 策略类型（general / book / separator）
     * @return 分块策略实例
     */
    public ChunkStrategy getStrategy(String type) {
        ChunkStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            log.warn("[ChunkStrategyFactory] 未知策略类型: {}，使用默认 general", type);
            return strategyMap.get("general");
        }
        return strategy;
    }
}
