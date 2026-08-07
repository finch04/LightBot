package com.lightbot.workflow;

import com.lightbot.enums.NodeType;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 节点超时与重试辅助类
 * <p>从 nodeData 中提取连接/响应超时与重试配置，包装节点执行逻辑</p>
 *
 * @author finch
 * @since 2026-06-26
 */
@Slf4j
public final class NodeTimeoutRetryHelper {

    /** 各节点类型默认超时（秒）：connect=连接超时，read=响应/执行超时 */
    private static final Map<NodeType, TimeoutProfile> DEFAULT_TIMEOUTS = Map.ofEntries(
            Map.entry(NodeType.LLM, new TimeoutProfile(10, 60)),
            Map.entry(NodeType.CLASSIFIER, new TimeoutProfile(10, 30)),
            Map.entry(NodeType.PARAMETER_EXTRACTOR, new TimeoutProfile(10, 30)),
            Map.entry(NodeType.RETRIEVAL, new TimeoutProfile(5, 15)),
            Map.entry(NodeType.TOOL, new TimeoutProfile(5, 20)),
            Map.entry(NodeType.API, new TimeoutProfile(5, 30)),
            Map.entry(NodeType.MCP, new TimeoutProfile(5, 30)),
            Map.entry(NodeType.SCRIPT, new TimeoutProfile(0, 10)),
            Map.entry(NodeType.LOOP, new TimeoutProfile(0, 120)),
            Map.entry(NodeType.BATCH, new TimeoutProfile(0, 120)),
            Map.entry(NodeType.APP_COMPONENT, new TimeoutProfile(0, 60)),
            Map.entry(NodeType.SUB_AGENT, new TimeoutProfile(0, 120)),
            Map.entry(NodeType.CONDITION, new TimeoutProfile(0, 3)),
            Map.entry(NodeType.VARIABLE, new TimeoutProfile(0, 3)),
            Map.entry(NodeType.VARIABLE_HANDLE, new TimeoutProfile(0, 5)),
            Map.entry(NodeType.CONFIRM, new TimeoutProfile(0, 3600))
    );

    private static final TimeoutProfile DEFAULT_FALLBACK = new TimeoutProfile(5, 30);

    /** 允许配置重试的节点类型 */
    private static final Set<NodeType> RETRY_CAPABLE = Set.of(
            NodeType.LLM,
            NodeType.CLASSIFIER,
            NodeType.PARAMETER_EXTRACTOR,
            NodeType.RETRIEVAL,
            NodeType.TOOL,
            NodeType.API,
            NodeType.MCP,
            NodeType.SCRIPT
    );

    /** 含首次执行在内的最大尝试次数上限 */
    private static final int MAX_ATTEMPTS = 2;
    private static final long DEFAULT_RETRY_DELAY_MS = 500;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    private NodeTimeoutRetryHelper() {
    }

    /**
     * 连接超时（秒）；无连接阶段的节点返回 0
     */
    public static int resolveConnectTimeoutSeconds(Map<String, Object> nodeData, NodeType nodeType) {
        Integer configured = readTimeoutConfigField(nodeData, "connectTimeout");
        if (configured != null) {
            return configured;
        }
        return profileOf(nodeType).connectSeconds();
    }

    /**
     * 响应/执行超时（秒），作为引擎层节点执行上限
     */
    public static int resolveReadTimeoutSeconds(Map<String, Object> nodeData, NodeType nodeType) {
        Integer configured = readTimeoutConfigField(nodeData, "readTimeout");
        if (configured != null) {
            return configured;
        }
        Integer legacy = readLegacyTimeout(nodeData);
        if (legacy != null) {
            return legacy;
        }
        return profileOf(nodeType).readSeconds();
    }

    /**
     * 兼容旧字段 {@code timeout}，等价于响应超时
     */
    public static int resolveTimeoutSeconds(Map<String, Object> nodeData, NodeType nodeType) {
        return resolveReadTimeoutSeconds(nodeData, nodeType);
    }

    /**
     * 包装节点执行：响应超时 + 重试
     */
    public static NodeExecutionResult executeWithTimeoutAndRetry(
            String nodeId, NodeType nodeType, Map<String, Object> nodeData,
            NodeExecutionCallable action) {
        return executeWithTimeoutAndRetry(nodeId, nodeType, nodeData, action, null);
    }

    /**
     * 包装节点执行：响应超时 + 重试，并通过 SSE 推送重试/失败事件
     */
    public static NodeExecutionResult executeWithTimeoutAndRetry(
            String nodeId, NodeType nodeType, Map<String, Object> nodeData,
            NodeExecutionCallable action, NodeResilienceEventContext eventContext) {

        int timeoutSec = resolveReadTimeoutSeconds(nodeData, nodeType);
        RetryConfig retryConfig = resolveRetryConfig(nodeData, nodeType);
        int maxAttempts = retryConfig.maxAttempts;

        Exception lastException = null;
        String lastReason = "execution_error";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                NodeExecutionResult result = CompletableFuture.supplyAsync(() -> {
                    try {
                        return action.execute();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).get(timeoutSec, TimeUnit.SECONDS);

                if (attempt > 1) {
                    log.info("[NodeTimeoutRetry] 节点重试成功: nodeId={}, attempt={}/{}", nodeId, attempt, maxAttempts);
                }
                return result;

            } catch (TimeoutException e) {
                lastReason = "read_timeout";
                lastException = new TimeoutException("节点执行超时（" + timeoutSec + "秒）");
                log.warn("[NodeTimeoutRetry] 节点执行超时: nodeId={}, readTimeout={}s, attempt={}/{}",
                        nodeId, timeoutSec, attempt, maxAttempts);
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                lastReason = NodeResilienceEventContext.classifyFailureReason(cause, false);
                lastException = cause instanceof Exception ex ? ex : new RuntimeException(cause);
                log.warn("[NodeTimeoutRetry] 节点执行失败: nodeId={}, error={}, attempt={}/{}",
                        nodeId, cause.getMessage(), attempt, maxAttempts);
            }

            if (attempt < maxAttempts) {
                if (eventContext != null) {
                    eventContext.emitRetry(lastReason, attempt, maxAttempts);
                }
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

        if (eventContext != null) {
            String detail = lastException != null ? lastException.getMessage() : null;
            eventContext.emitFailure(lastReason, maxAttempts, maxAttempts, detail);
        }

        if (lastException instanceof RuntimeException re) {
            throw re;
        }
        throw new RuntimeException(lastException);
    }

    /**
     * 从 nodeData 读取重试配置
     */
    @SuppressWarnings("unchecked")
    public static RetryConfig resolveRetryConfig(Map<String, Object> nodeData, NodeType nodeType) {
        if (!RETRY_CAPABLE.contains(nodeType)) {
            return RetryConfig.DISABLED;
        }
        if (nodeData == null) {
            return RetryConfig.DISABLED;
        }
        Object retryObj = nodeData.get("retryConfig");
        if (!(retryObj instanceof Map<?, ?> retryMap)) {
            return RetryConfig.DISABLED;
        }
        boolean enabled = Boolean.TRUE.equals(retryMap.get("enabled"));
        if (!enabled) {
            return RetryConfig.DISABLED;
        }
        int maxAttempts = 2;
        Object maxObj = retryMap.get("maxAttempts");
        if (maxObj instanceof Number n) {
            maxAttempts = Math.min(MAX_ATTEMPTS, Math.max(1, n.intValue()));
        }
        long delayMs = DEFAULT_RETRY_DELAY_MS;
        Object delayObj = retryMap.get("delayMs");
        if (delayObj instanceof Number n) {
            delayMs = Math.max(0, n.longValue());
        }
        return new RetryConfig(maxAttempts, delayMs, BackoffType.EXPONENTIAL);
    }

    private static TimeoutProfile profileOf(NodeType nodeType) {
        return DEFAULT_TIMEOUTS.getOrDefault(nodeType, DEFAULT_FALLBACK);
    }

    @SuppressWarnings("unchecked")
    private static Integer readTimeoutConfigField(Map<String, Object> nodeData, String field) {
        if (nodeData == null) {
            return null;
        }
        Object timeoutConfigObj = nodeData.get("timeoutConfig");
        if (timeoutConfigObj instanceof Map<?, ?> timeoutMap) {
            Object value = timeoutMap.get(field);
            Integer parsed = parsePositiveInt(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static Integer readLegacyTimeout(Map<String, Object> nodeData) {
        if (nodeData == null) {
            return null;
        }
        return parsePositiveInt(nodeData.get("timeout"));
    }

    private static Integer parsePositiveInt(Object value) {
        if (value instanceof Number n) {
            return Math.max(1, n.intValue());
        }
        if (value != null) {
            try {
                return Math.max(1, Integer.parseInt(value.toString()));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static long calculateDelay(long baseMs, int retryIndex, BackoffType backoffType) {
        if (backoffType == BackoffType.EXPONENTIAL) {
            return (long) (baseMs * Math.pow(BACKOFF_MULTIPLIER, retryIndex));
        }
        return baseMs;
    }

    @FunctionalInterface
    public interface NodeExecutionCallable {
        NodeExecutionResult execute() throws Exception;
    }

    public enum BackoffType {
        FIXED,
        EXPONENTIAL
    }

    public record RetryConfig(int maxAttempts, long delayMs, BackoffType backoffType) {
        public static final RetryConfig DISABLED = new RetryConfig(1, 0, BackoffType.EXPONENTIAL);
    }

    /** 节点默认超时画像 */
    record TimeoutProfile(int connectSeconds, int readSeconds) {
    }
}
