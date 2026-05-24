package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点处理器注册中心
 * <p>自动注册所有 NodeProcessor 实现，按 NodeType 索引</p>
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Component
public class NodeProcessorRegistry {

    private final Map<NodeType, NodeProcessor> processors = new HashMap<>();

    /**
     * 自动注册所有 NodeProcessor 实现
     *
     * @param processorList 所有 NodeProcessor 实现（Spring 自动注入）
     */
    @Autowired
    public void registerProcessors(List<NodeProcessor> processorList) {
        for (NodeProcessor processor : processorList) {
            processors.put(processor.getType(), processor);
            log.info("[NodeProcessorRegistry] 注册处理器: type={}, class={}",
                    processor.getType(), processor.getClass().getSimpleName());
        }
        log.info("[NodeProcessorRegistry] 已注册 {} 个节点处理器", processors.size());
    }

    /**
     * 根据节点类型获取处理器
     *
     * @param type 节点类型
     * @return 处理器实例
     */
    public NodeProcessor getProcessor(NodeType type) {
        NodeProcessor processor = processors.get(type);
        if (processor == null) {
            throw new IllegalArgumentException("未注册的节点类型: " + type);
        }
        return processor;
    }

    /**
     * 检查是否已注册指定类型的处理器
     *
     * @param type 节点类型
     * @return 是否已注册
     */
    public boolean hasProcessor(NodeType type) {
        return processors.containsKey(type);
    }
}