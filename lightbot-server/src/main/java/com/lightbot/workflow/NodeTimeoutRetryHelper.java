package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 节点超时与重试辅助类
 * <p>从 nodeData 中提取超时/重试配置，包装节点执行逻辑</p>
 *
 * @author finch
 * @since 2026-06-26
 */
@Slf4j
public final class NodeTimeoutRetryHelper {

    /** 各节点类型默认超时（秒） */
    private static final Map<NodeType, Integer> DEFAULT_TIMEOUT_SECONDS = Map.ofEntries(
            Map.entry(NodeType.LLM, 120),
            Map.entry(NodeType.API, 60),
            Map.entry(NodeType.TOOL, 30),
            Map.entry(NodeType.MCP, 60),
            Map.entry(NodeType.SCRIPT, 15),
            Map.entry(NodeType.RETRIEVAL, 30),
            Map.entry(NodeType.CLASSIFIER, 60),
            Map.entry(NodeType.CONDITION, 5),
            Map.entry(NodeType.VARIABLE, 5),
            Map.entry(NodeType.LOOP, 300),
            Map.entry(NodeType.BATCH, 300),
            Map.entry(NodeType.PARAMETER_EXTRACTOR, 30)
    );

    private static final int DEFAULT_TIMEOUT_FALLBACK = 30;
    private static final int MAX_RETRY_COUNT = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private NodeTimeoutRetryHelper() {
    }

    /**
     * 从 nodeData 读取超时配置，未配置则使用节点类型默认值
     */
    public static int resolveTimeoutSeconds(Map<String, Object> nodeData, NodeType nodeType) {
        if (nodeData != null) {
            Object timeout = nodeData.get("timeout");
            if (timeout instanceof Number n) {
                return Math.max(1, n.intValue());
            }
            if (timeout != null) {
                try {
                    return Math.max(1, Integer.parseInt(timeout.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return DEFAULT_TIMEOUT_SECONDS.getOrDefault(nodeType, DEFAULT_TIMEOUT_FALLBACK);
    }

    /**
     * 包装节点执行：超时 + 重试
     *
     * @param nodeId    节点 ID（用于日志）
     * @param nodeType  节点类型
     * @param nodeData  节点配置
     * @param action    执行逻辑
     * @return 执行结果
     */
    public static NodeExecutionResult executeWithTimeoutAndRetry(
            String nodeId, NodeType nodeType, Map<String, Object> nodeData,
            NodeExecutionCallable action) {

        int timeoutSec = resolveTimeoutSeconds(nodeData, nodeType);
        RetryConfig retryConfig = resolveRetryConfig(nodeData);
        int maxAttempts = 1 + retryConfig.maxRetryCount;

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // 带超时的执行
                final int currentAttempt = attempt;
                NodeExecutionResult result = CompletableFuture.supplyAsync(() -> {
                    try {
                        return action.execute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).get(timeoutSec, TimeUnit.SECONDS);

                // 成功则返回
                if (attempt > 1) {
                    log.info("[NodeTimeoutRetry] 节点重试成功: nodeId={}, attempt={}/{}", nodeId, attempt, maxAttempts);
                }
                return result;

            } catch (TimeoutException e) {
                lastException = new TimeoutException("节点执行超时（" + timeoutSec + "秒）");
                log.warn("[NodeTimeoutRetry] 节点执行超时: nodeId={}, timeout={}s, attempt={}/{}",
                        nodeId, timeoutSec, attempt, maxAttempts);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastException = cause instanceof Exception ex ? ex : new RuntimeException(cause);
                log.warn("[NodeTimeoutRetry] 节点执行失败: nodeId={}, error={}, attempt={}/{}",
                        nodeId, cause.getMessage(), attempt, maxAttempts);
            }

            // 还有重试机会则等待
            if (attempt < maxAttempts) {
                long delay = calculateDelay(retryConfig.delayMs, attempt - 1, retryConfig.backoffType);
                log.info("[NodeTimeoutRetry] 等待重试: nodeId={}, delay={}ms", nodeId, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("节点执行被中断", ie);
                }
            }
        }

        // 所有重试均失败
        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(lastException);
    }

    /**
     * 从 nodeData 读取重试配置
     */
    @SuppressWarnings("unchecked")
    public static RetryConfig resolveRetryConfig(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return RetryConfig.DEFAULT;
        }
        Object retryObj = nodeData.get("retryConfig");
        if (!(retryObj instanceof Map<?, ?> retryMap)) {
            return RetryConfig.DEFAULT;
        }
        boolean enabled = Boolean.TRUE.equals(retryMap.get("enabled"));
        if (!enabled) {
            return RetryConfig.DEFAULT;
        }
        int maxAttempts = 3;
        Object maxObj = retryMap.get("maxAttempts");
        if (maxObj instanceof Number n) {
            maxAttempts = Math.min(MAX_RETRY_COUNT, Math.max(1, n.intValue()));
        }
        long delayMs = DEFAULT_RETRY_DELAY_MS;
        Object delayObj = retryMap.get("delayMs");
        if (delayObj instanceof Number n) {
            delayMs = Math.max(0, n.longValue());
        }
        return new RetryConfig(maxAttempts - 1, delayMs, BackoffType.EXPONENTIAL);
    }

    private static long calculateDelay(long baseMs, int retryIndex, BackoffType backoffType) {
        if (backoffType == BackoffType.EXPONENTIAL) {
            return (long) (baseMs * Math.pow(BACKOFF_MULTIPLIER, retryIndex));
        }
        return baseMs;
    }

    /**
     * 节点执行回调
     */
    @FunctionalInterface
    public interface NodeExecutionCallable {
        NodeExecutionResult execute() throws Exception;
    }

    /**
     * 退避策略
     */
    public enum BackoffType {
        FIXED,
        EXPONENTIAL
    }

    /**
     * 重试配置
     */
    public record RetryConfig(int maxRetryCount, long delayMs, BackoffType backoffType) {
        public static final RetryConfig DEFAULT = new RetryConfig(0, 0, BackoffType.EXPONENTIAL);
    }
}
