package com.lightbot.workflow;

import com.lightbot.enums.NodeType;

/**
 * 工作流节点处理器接口
 * <p>所有节点处理器必须实现此接口，用于执行特定类型的节点逻辑</p>
 *
 * @author finch
 * @since 2026-05-24
 */
public interface NodeProcessor {

    /**
     * 获取节点类型
     *
     * @return 节点类型枚举
     */
    NodeType getType();

    /**
     * 执行节点逻辑
     *
     * @param context 执行上下文
     * @return 执行结果
     */
    NodeExecutionResult execute(NodeExecutionContext context);
}