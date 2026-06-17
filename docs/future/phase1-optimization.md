# LightBot Phase 1 架构优化方案

> 目标：在保持单体架构的前提下，完成核心性能优化和基础设施加固，使系统具备上线生产环境的能力。
> 后续再考虑微服务拆分。

---

## 目录

1. [数据库索引优化](#1-数据库索引优化) `待实施`
2. [向量检索索引调优](#2-向量检索索引调优) `待实施`
3. [Redis 缓存体系建设](#3-redis-缓存体系建设) `待实施`
4. [文件存储优化](#4-文件存储优化) `已完成`
5. [SSE 重连机制](#5-sse-重连机制) `已完成`
6. [大列表虚拟滚动](#6-大列表虚拟滚动) `已完成`
7. [附录：微服务拆分时的数据库规划](#附录微服务拆分时的数据库规划)

---

## 1. 数据库索引优化

### 需求分析

当前项目已有较完善的索引体系（init SQL 中定义了 70+ 个索引），但存在以下缺口：

**缺失的复合索引：**

| 表 | 查询场景 | 当前索引 | 问题 |
|---|---|---|---|
| `llm_trace` | 按 traceSource + 时间范围查询 | 单列索引 `idx_llm_trace_trace_source` + `idx_llm_trace_create_time` | 无法走复合索引，需要回表过滤 |
| `llm_trace` | 按 agentId + traceSource 查询 | 无 | Agent 维度统计慢 |
| `tool_calls` | 按时间范围查询 | 无时间索引 | 可观测页面工具调用列表慢 |
| `message` | 按 session_id + create_time DESC 分页 | `idx_message_create_time(session_id, create_time)` | 已有，但方向可能不对（ASC vs DESC） |
| `chat_session` | 按 user_id + agent_id 查询 | 仅 `idx_chat_session_user_id` | 用户按 Agent 筛选会话时缺复合索引 |
| `eval_experiment_result` | 按 experiment_id 查询 | `idx_eval_experiment_result_experiment_id` | 已有，OK |

**Trace 概览全量聚合问题：**

`LlmTraceServiceImpl.getOverview()` 将所有匹配记录加载到内存做 Java Stream 聚合。随着 trace 表数据增长（生产环境日均可达 10 万+），这会导致：
- 大量 DB I/O 和网络传输
- JVM 内存压力（每条约 10KB，10 万条 = 1GB）
- 查询耗时从毫秒退化到秒级

### 技术设计

**1.1 新增复合索引**

```sql
-- llm_trace 复合索引：可观测页面的主要查询模式
CREATE INDEX idx_llm_trace_source_time ON llm_trace (trace_source, create_time DESC);

-- llm_trace Agent 维度查询
CREATE INDEX idx_llm_trace_agent_source ON llm_trace (agent_id, trace_source);

-- tool_calls 时间索引
CREATE INDEX idx_tool_calls_created_at ON tool_calls (created_at DESC);

-- chat_session 复合索引：用户按 Agent 筛选
CREATE INDEX idx_chat_session_user_agent ON chat_session (user_id, agent_id);
```

**1.2 Trace 概览改为 SQL 聚合**

```java
// LlmTraceMapper.java 新增方法
@Select("""
    SELECT
        COUNT(*) AS total_count,
        COALESCE(SUM(total_tokens), 0) AS total_tokens,
        COALESCE(AVG(total_duration_ms), 0) AS avg_duration_ms,
        COALESCE(SUM(tool_call_count), 0) AS total_tool_calls
    FROM llm_trace
    WHERE trace_source = #{traceSource}
      AND create_time >= #{startTime}
""")
Map<String, Object> aggregateOverview(@Param("traceSource") String traceSource,
                                      @Param("startTime") LocalDateTime startTime);
```

```java
// LlmTraceServiceImpl.getOverview() 改造
@Override
public Map<String, Object> getOverview(String traceSource) {
    // 统计最近 7 天（避免全表扫描）
    LocalDateTime startTime = LocalDateTime.now().minusDays(7);
    String source = StringUtils.hasText(traceSource) ? traceSource : null;

    // 1. SQL 聚合替代内存聚合
    Map<String, Object> aggregate = baseMapper.aggregateOverview(
        source != null ? source : "chat", startTime);

    // 2. 成功/失败数仍用 count（有索引，很快）
    LambdaQueryWrapper<LlmTrace> base = new LambdaQueryWrapper<LlmTrace>()
        .ge(LlmTrace::getCreateTime, startTime);
    if (source != null) {
        base.eq(LlmTrace::getTraceSource, source);
    }
    long successCount = count(base.clone().eq(LlmTrace::getStatus, "completed"));
    long failedCount = count(base.clone().eq(LlmTrace::getStatus, "failed"));

    Map<String, Object> result = new HashMap<>(aggregate);
    result.put("successCount", successCount);
    result.put("failedCount", failedCount);
    return result;
}
```

### 优先级：P0
### 难度：低
### 工作量：2d

### 难点

- 复合索引需要在生产环境创建，大表建索引会锁表。PostgreSQL 支持 `CREATE INDEX CONCURRENTLY` 不阻塞读写
- Trace 概览改造需要兼容现有的前端展示逻辑（前端读取 `totalCount`、`totalTokens`、`avgDurationMs`、`totalToolCalls`）

---

## 2. 向量检索索引调优

### 需求分析

当前 embedding 表已创建了两个 HNSW 索引：

```sql
-- cosine 距离（m=16, ef_construction=64）
CREATE INDEX idx_embedding_vector ON embedding
    USING hnsw (vector vector_cosine_ops) WITH (m = 16, ef_construction = 64);

-- L2 距离（m=16, ef_construction=200）
CREATE INDEX idx_embedding_vector_hnsw ON embedding
    USING hnsw (vector vector_l2_ops) WITH (m = 16, ef_construction = 200);
```

**问题：**

1. **两个索引冗余**：cosine 和 L2 索引同时存在，每次 INSERT/UPDATE 都要维护两个索引，写入性能下降约 50%
2. **`ef_construction=64` 偏低**：cosine 索引的构建参数较低，召回率不如 L2 索引
3. **未设置 `ef_search` 查询参数**：pgvector 默认 `ef_search=40`，对于高召回率场景不够
4. **Milvus 每次检索前调用 `hasCollection()` RPC**：额外网络开销

### 技术设计

**2.1 统一为单一 HNSW 索引**

```sql
-- 删除冗余的 cosine 索引
DROP INDEX IF EXISTS idx_embedding_vector;

-- 将 L2 索引改为 cosine（RAG 场景 cosine 优于 L2）
DROP INDEX IF EXISTS idx_embedding_vector_hnsw;
CREATE INDEX idx_embedding_vector_hnsw ON embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
```

**2.2 查询时设置 ef_search**

```java
// EmbeddingMapper.java 新增方法
@Select("SET LOCAL hnsw.ef_search = 100")
void setHnswEfSearch();

// EmbeddingServiceImpl.searchPgvector() 中调用
@Override
public List<SearchResult> searchPgvector(Long knowledgeId, float[] queryVector, int topK) {
    // 在事务内设置 ef_search（SET LOCAL 仅对当前事务生效）
    baseMapper.setHnswEfSearch();
    return baseMapper.searchSimilar(knowledgeId, toVectorString(queryVector), topK);
}
```

**2.3 Milvus 集合检查缓存**

```java
// EmbeddingServiceImpl 中增加缓存
private final ConcurrentHashMap<String, Boolean> collectionExistsCache = new ConcurrentHashMap<>();

private boolean hasCollectionCached(String collectionName) {
    return collectionExistsCache.computeIfAbsent(collectionName,
        k -> milvusUtil.hasCollection(k));
}

// 集合创建/删除时清除缓存
public void invalidateCollectionCache(String collectionName) {
    collectionExistsCache.remove(collectionName);
}
```

### 优先级：P0
### 难度：低
### 工作量：1.5d

### 难点

- 删除索引和重建索引需要在低峰期执行，期间向量检索不可用
- `ef_search` 需要在事务内设置，需要确保 MyBatis 的事务管理正确传递
- 召回率 vs 延迟的权衡：`ef_search=100` 大约增加 10-20ms 延迟，但召回率从 ~90% 提升到 ~98%

---

## 3. Redis 缓存体系建设

### 需求分析

当前仅缓存了 Model 和 ModelProvider。以下高频读取数据每次请求都穿透到数据库：

| 缓存对象 | 读取场景 | 读取频率 | 数据大小 | 变更频率 |
|----------|---------|---------|---------|---------|
| Agent | 每次对话加载配置 | 极高 | ~2KB | 低 |
| Agent Version | 版本发布/预览 | 中 | ~5KB | 低 |
| Knowledge | RAG 检索时查配置 | 高 | ~1KB | 低 |
| Tool | Agent 加载工具列表 | 高 | ~1KB | 低 |
| MCP Server | Agent 加载 MCP | 中 | ~1KB | 低 |
| SubAgent | 委派时查配置 | 中 | ~1KB | 低 |
| Prompt 模板 | 使用时查模板 | 中 | ~3KB | 低 |
| SystemConfig | 多处读取 | 高 | ~0.5KB | 极低 |

这些数据的共同特征：**读多写少（读写比 100:1+）、数据量小、变更不频繁**。是缓存的理想场景。

### 技术设计

**3.1 引入 Spring Cache + RedisCacheManager**

```java
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> configMap = Map.of(
            "agent",        defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "agentVersion", defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "knowledge",    defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "tool",         defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "mcpServer",    defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "subagent",     defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "prompt",       defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "systemConfig", defaultConfig.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(configMap)
            .transactionAware()
            .build();
    }
}
```

**3.2 各 Service 加缓存注解**

```java
// AgentServiceImpl
@Cacheable(value = "agent", key = "#id")
public Agent getById(Long id) {
    return super.getById(id);
}

@CacheEvict(value = "agent", key = "#entity.id")
public boolean updateById(Agent entity) {
    return super.updateById(entity);
}

@CacheEvict(value = "agent", key = "#id")
public boolean removeById(Long id) {
    return super.removeById(id);
}

// KnowledgeServiceImpl
@Cacheable(value = "knowledge", key = "#id")
public Knowledge getById(Long id) { ... }

// ToolServiceImpl
@Cacheable(value = "tool", key = "#id")
public Tool getById(Long id) { ... }

// SubAgentServiceImpl
@Cacheable(value = "subagent", key = "#id")
public SubAgent getById(Long id) { ... }

// SystemConfigServiceImpl
@Cacheable(value = "systemConfig", key = "#key")
public String getValueByKey(String key) { ... }
```

**3.3 JSONB 字段序列化兼容**

Entity 中的 JSONB 字段（如 `Agent.config`）使用了 MyBatis 的 `JsonNodeTypeHandler`，存储为 String 类型。Spring Cache 的 `GenericJackson2JsonRedisSerializer` 可以直接序列化 String，无需额外处理。

但需要注意：`@Cacheable` 默认使用 JDK 序列化，需要在 `RedisCacheConfig` 中配置 `GenericJackson2JsonRedisSerializer` 作为 value 序列化器（已在上面的配置中包含）。

**3.4 缓存预热**

```java
@Component
@RequiredArgsConstructor
public class CacheWarmUpRunner implements ApplicationRunner {

    private final AgentMapper agentMapper;
    private final KnowledgeMapper knowledgeMapper;
    private final ToolMapper toolMapper;
    private final SystemConfigMapper systemConfigMapper;

    @Override
    public void run(ApplicationArguments args) {
        // 预热 Agent 缓存
        agentMapper.selectList(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getDeleted, 0))
            .forEach(agent -> cacheManager.getCache("agent").put(agent.getId(), agent));

        // 预热 Knowledge 缓存
        knowledgeMapper.selectList(new LambdaQueryWrapper<Knowledge>()
                .eq(Knowledge::getDeleted, 0))
            .forEach(k -> cacheManager.getCache("knowledge").put(k.getId(), k));

        // 预热 Tool 缓存
        toolMapper.selectList(new LambdaQueryWrapper<Tool>()
                .eq(Tool::getDeleted, 0))
            .forEach(t -> cacheManager.getCache("tool").put(t.getId(), t));

        // 预热 SystemConfig 缓存
        systemConfigMapper.selectList()
            .forEach(c -> cacheManager.getCache("systemConfig").put(c.getConfigKey(), c.getConfigValue()));

        log.info("[缓存预热] 完成");
    }
}
```

**3.5 缓存 Key 设计**

```
agent::123                    -> Agent 对象
knowledge::456                -> Knowledge 对象
tool::789                     -> Tool 对象
mcp_server::101               -> McpServer 对象
subagent::202                 -> SubAgent 对象
prompt::customer_service      -> Prompt 对象
systemConfig::chat.max_rounds -> 配置值
```

### 优先级：P0
### 难度：中
### 工作量：5d

### 难点

- **缓存一致性**：Agent 的更新操作分散在多个方法中（`updateById`、`updateAgentConfig`、`publishVersion` 等），需要确保所有写操作都清除缓存。遗漏会导致脏读
- **缓存穿透**：查询不存在的 ID 会穿透到 DB。解决方案：缓存空值（TTL 设短，如 1 分钟）
- **缓存击穿**：热点 Key（如默认 Agent）过期瞬间大量并发请求。解决方案：`sync = true` 让只有一个线程重建缓存
- **JSON 序列化**：`Agent.config` 是 JSONB 字段，存入 Redis 时需要注意序列化/反序列化的一致性
- **`@CacheEvict` 覆盖面**：需要审查所有写操作方法，确保缓存被正确清除。建议用 `@CacheEvict(allEntries = true)` 作为兜底策略

---

## 4. 文件存储优化

### 需求分析

当前 `MinioUtil` 存在以下问题：

| 问题 | 位置 | 影响 |
|------|------|------|
| `ensureBucket()` 每次上传都调用 | `MinioUtil:54,79` | 每次文件上传多一次 RPC（~10ms） |
| `downloadBytes()` 全量读入内存 | `MinioUtil:132` | 大文件 OOM 风险 |
| 无分片上传 | `MinioUtil:77` | 100MB 文件上传失败需全部重传 |
| 预签名 URL 7 天过期 | `MinioUtil:172` | 文档预览链接过期后不可用 |

### 技术设计

**4.1 ensureBucket 启动时调用一次**

```java
@Slf4j
@Component
public class MinioUtil {

    private final MinioClient minioClient;
    private final String bucketName;
    private volatile boolean bucketEnsured = false;

    @PostConstruct
    public void init() {
        ensureBucketOnce();
    }

    private void ensureBucketOnce() {
        if (bucketEnsured) return;
        synchronized (this) {
            if (bucketEnsured) return;
            try {
                boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build());
                if (!exists) {
                    minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build());
                }
                bucketEnsured = true;
            } catch (Exception e) {
                log.error("[MinIO] ensureBucket 失败", e);
            }
        }
    }

    // upload 方法移除 ensureBucket() 调用
    public String upload(InputStream stream, String objectName, String contentType) {
        // 不再调用 ensureBucket()
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(bucketName)
            .object(objectName)
            .stream(stream, -1, 10 * 1024 * 1024)  // 10MB part size，自动分片
            .contentType(contentType)
            .build());
        return objectName;
    }
}
```

**4.2 流式下载替代全量读取**

```java
// 改造前
public byte[] downloadBytes(String filePath) {
    try (InputStream in = download(filePath)) {
        return in.readAllBytes();  // 全量读入内存
    }
}

// 改造后：返回 InputStream，调用方流式处理
public InputStream downloadStream(String filePath) {
    return minioClient.getObject(
        GetObjectArgs.builder().bucket(bucketName).object(filePath).build());
}

// 保留 downloadBytes 用于小文件（<10MB），增加大小检查
public byte[] downloadBytes(String filePath) {
    try (InputStream in = download(filePath)) {
        // 先检查文件大小
        StatObjectResponse stat = minioClient.statObject(
            StatObjectArgs.builder().bucket(bucketName).object(filePath).build());
        if (stat.size() > 10 * 1024 * 1024) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE_FOR_MEMORY);
        }
        return in.readAllBytes();
    }
}
```

**4.3 MinIO 客户端连接池配置**

```java
@Bean
public MinioClient minioClient(MinioProperties properties) {
    return MinioClient.builder()
        .endpoint(properties.getEndpoint())
        .credentials(properties.getAccessKey(), properties.getSecretKey())
        .httpClient(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .connectionPool(new ConnectionPool(
                32,           // 最大连接数
                5, TimeUnit.MINUTES  // 空闲连接存活时间
            ))
            .build())
        .build();
}
```

### 优先级：P1
### 难度：低
### 工作量：2d

### 难点

- **downloadStream 调用方改造**：需要修改所有使用 `downloadBytes` 的地方为流式读取，涉及 `DocumentServiceImpl`、`DocumentEditController` 等
- **预签名 URL 过期**：当前 7 天过期对于文档预览场景基本够用，暂不改造

### 实施状态：已完成

**完成内容：**
- `MinioUtil.java`：`ensureBucket()` 改为 `@PostConstruct` + 双重检查锁，启动时仅调用一次
- `MinioUtil.java`：新增 `downloadStream()` 方法，返回 `InputStream` 供调用方流式处理
- `MinioUtil.java`：`downloadBytes()` 增加文件大小检查（>10MB 抛出 `FILE_TOO_LARGE_FOR_MEMORY`）
- `MinioUtil.java`：MinIO 客户端配置 OkHttp 连接池（32 连接、5 分钟空闲存活）
- `ErrorCode.java`：新增 `FILE_TOO_LARGE_FOR_MEMORY(80004)` 错误码
- 上传方法移除了每次调用 `ensureBucket()` 的开销，10MB part size 自动分片

---

## 5. SSE 重连机制

### 需求分析

当前项目有三处 SSE 使用：

| 场景 | 实现方式 | 重连机制 | 问题 |
|------|---------|---------|------|
| 对话流式输出 | `fetch` + ReadableStream | **无** | 网络中断后对话中断，用户需手动刷新 |
| 任务中心计数 | `EventSource` | 有（3s 固定重试） | 基本可用，但无退避策略 |
| 日志实时监控 | `EventSource` | **无** | 断线后需手动重连 |

**对话流式输出**是最关键的场景：用户正在对话时网络波动，会导致 AI 回复中断且无法恢复。

### 技术设计

**5.1 对话流式输出重连**

```javascript
// api/chat.js 改造

/**
 * 带自动重连的流式对话
 * @param {Object} data - 对话请求数据
 * @param {Object} callbacks - 回调函数
 * @param {AbortSignal} signal - 取消信号
 * @param {Object} options - 配置项
 */
export async function chatStream(data, callbacks, signal, options = {}) {
  const { maxRetries = 3, retryDelay = 2000 } = options
  const token = localStorage.getItem('token')
  let retries = 0
  let receivedLength = 0  // 已接收的内容长度，用于断点续传

  async function attempt() {
    const headers = {
      'Content-Type': 'application/json',
      Authorization: token || '',
    }

    // 如果是重连，携带已接收长度，让服务端跳过已发送内容
    if (retries > 0 && receivedLength > 0) {
      headers['X-Resume-Offset'] = String(receivedLength)
    }

    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers,
      body: JSON.stringify(data),
      signal,
    })

    if (!response.ok) {
      throw new Error(`流式请求失败: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    try {
      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        const chunk = decoder.decode(value, { stream: true })
        receivedLength += chunk.length
        buffer += chunk

        // 处理完整的 SSE 行
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          processSseLine(line, callbacks)
        }
      }
      // 处理残留
      if (buffer.trim()) processSseLine(buffer, callbacks)
      callbacks.onDone?.()
    } catch (err) {
      if (err.name === 'AbortError') return  // 用户主动取消
      throw err  // 网络错误，由外层重连
    }
  }

  while (retries <= maxRetries) {
    try {
      await attempt()
      return  // 成功完成
    } catch (err) {
      retries++
      if (retries > maxRetries || signal?.aborted) {
        callbacks.onError?.(err)
        return
      }
      // 指数退避
      const delay = retryDelay * Math.pow(2, retries - 1)
      callbacks.onReconnecting?.(retries, delay)
      await new Promise(resolve => setTimeout(resolve, delay))
    }
  }
}
```

**5.2 任务中心 SSE 退避策略改进**

```javascript
// MainLayout.vue 改造
let taskSSE = null
let sseRetries = 0
const SSE_MAX_RETRIES = 10
const SSE_BASE_DELAY = 3000

function connectTaskSSE() {
  if (taskSSE) return
  const userId = user.value?.id
  if (!userId) return

  taskSSE = new EventSource(`/api/tasks/stream?userId=${userId}`)

  taskSSE.addEventListener('count', (e) => {
    try {
      updateTaskCounts(JSON.parse(e.data))
    } catch { /* ignore */ }
  })

  taskSSE.onopen = () => {
    sseRetries = 0  // 连接成功，重置计数
  }

  taskSSE.onerror = () => {
    taskSSE?.close()
    taskSSE = null
    sseRetries++
    if (sseRetries <= SSE_MAX_RETRIES) {
      // 指数退避，最大 30s
      const delay = Math.min(SSE_BASE_DELAY * Math.pow(1.5, sseRetries - 1), 30000)
      setTimeout(connectTaskSSE, delay)
    }
  }
}
```

**5.3 日志监控 SSE 重连**

```javascript
// LogMonitor.vue 改造
let logRetries = 0

function connectSSE() {
  if (connecting.value) return
  connecting.value = true
  const token = localStorage.getItem('token')

  eventSource = new EventSource(`/api/logs/stream?token=${encodeURIComponent(token)}`)

  eventSource.onopen = () => {
    sseConnected.value = true
    connecting.value = false
    logRetries = 0
  }

  eventSource.onerror = () => {
    eventSource?.close()
    eventSource = null
    sseConnected.value = false
    connecting.value = false
    logRetries++
    if (logRetries <= 10) {
      const delay = Math.min(3000 * Math.pow(1.5, logRetries - 1), 30000)
      setTimeout(() => {
        if (!eventSource && !connecting.value) connectSSE()
      }, delay)
    }
  }
}
```

### 优先级：P0
### 难度：中
### 工作量：2d

### 难点

- **对话流式重连的断点续传**：当前服务端不支持从断点继续发送。实现完整断点续传需要服务端改造（记录已发送内容偏移量），工作量较大。**建议第一阶段只实现"自动重试新请求"**，而非断点续传
- **重连时的状态恢复**：对话重连后，前端需要正确拼接已接收和新接收的内容，避免重复或丢失
- **AbortController 管理**：重连时需要正确处理 `AbortController`，避免旧连接的回调干扰新连接

### 实施状态：已完成

**完成内容：**
- `chat.js`：`chatStream()` 增加自动重连机制，最多重试 3 次，指数退避（2s/4s/8s），用户主动取消（AbortError）不重试
- `MainLayout.vue`：任务中心 SSE 重连改为指数退避（3s base，1.5x 递增，最大 30s，最多 10 次），连接成功重置计数
- `LogMonitor.vue`：日志监控 SSE 重连改为指数退避（3s base，1.5x 递增，最大 30s，最多 10 次），手动断开时重置计数

---

## 6. 大列表虚拟滚动

### 需求分析

当前项目中以下页面存在大列表性能问题：

| 页面 | 列表组件 | 数据量 | 问题 |
|------|---------|--------|------|
| 可观测 Trace 列表 | `a-table` + 分页 | 每页 20 条 | 分页 OK，但翻页体验一般 |
| 工具调用列表 | `a-table` + 分页 | 每页 20 条 | 同上 |
| 对话消息列表 | 自定义 div 列表 | 数百条 | 长对话时 DOM 节点过多，滚动卡顿 |
| 知识库文档列表 | `a-table` + 分页 | 数百~数千 | 分页 OK |
| 工作流节点列表 | 自定义 div | 数十个 | 节点少，不是问题 |

**核心问题：对话消息列表**是唯一没有分页的长列表，随着对话轮次增多（50+ 轮对话 = 100+ 条消息 DOM 节点），滚动性能会明显下降。

### 技术设计

**方案：对 a-table 保持分页，对对话消息列表引入虚拟滚动**

**6.1 引入虚拟滚动库**

```bash
pnpm add @tanstack/vue-virtual
```

**6.2 对话消息列表虚拟滚动**

```vue
<!-- Chat.vue 消息列表改造 -->
<template>
  <div ref="scrollContainerRef" class="message-list" @scroll="handleScroll">
    <div :style="{ height: totalSize + 'px', position: 'relative' }">
      <div
        v-for="virtualRow in virtualRows"
        :key="virtualRow.key"
        :style="{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          transform: `translateY(${virtualRow.start}px)`,
        }"
      >
        <MessageItem :message="messages[virtualRow.index]" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { useVirtualizer } from '@tanstack/vue-virtual'

const scrollContainerRef = ref(null)

const virtualizer = useVirtualizer({
  count: messages.value.length,
  getScrollElement: () => scrollContainerRef.value,
  estimateSize: (index) => {
    // 根据消息类型估算高度
    const msg = messages.value[index]
    if (msg.role === 'user') return 60
    // AI 回复根据内容长度估算
    const lineCount = Math.ceil((msg.content?.length || 0) / 50)
    return Math.max(80, lineCount * 22 + 40)
  },
  overscan: 5,  // 预渲染 5 条
})

const virtualRows = computed(() => virtualizer.value.getVirtualItems())
const totalSize = computed(() => virtualizer.value.getTotalSize())
</script>
```

**6.3 消息高度动态测量**

由于 AI 回复消息高度不固定（含 Markdown 渲染、代码块、图片等），需要使用动态高度测量：

```javascript
const virtualizer = useVirtualizer({
  count: messages.value.length,
  getScrollElement: () => scrollContainerRef.value,
  estimateSize: () => 100,  // 初始估算
  overscan: 5,
})

// 消息渲染后，通过 ResizeObserver 测量实际高度
onMounted(() => {
  const observer = new ResizeObserver((entries) => {
    for (const entry of entries) {
      const index = Number(entry.target.dataset.index)
      if (!isNaN(index)) {
        virtualizer.value.measureElement(entry.target)
      }
    }
  })

  // 观察所有消息 DOM 节点
  watchEffect(() => {
    const items = scrollContainerRef.value?.querySelectorAll('[data-index]')
    items?.forEach(el => observer.observe(el))
  })
})
```

**6.4 自动滚动到底部**

```javascript
// 新消息到达时自动滚动到底部
watch(() => messages.value.length, (newLen, oldLen) => {
  if (newLen > oldLen) {
    nextTick(() => {
      virtualizer.value.scrollToIndex(newLen - 1, { align: 'end' })
    })
  }
})
```

### 优先级：P1
### 难度：中高
### 工作量：3d

### 难点

- **动态高度测量**：Markdown 渲染后的实际高度无法提前预知，需要 `ResizeObserver` 动态测量。初始估算不准会导致滚动条跳动
- **流式输出时的滚动**：AI 回复正在流式输出时，内容高度持续变化，需要频繁调用 `measureElement`，可能影响性能
- **图片/代码块等富媒体**：图片加载完成后高度会变化，需要监听 `load` 事件重新测量
- **与现有滚动逻辑兼容**：当前 Chat.vue 可能有"滚动到底部"按钮、"新消息提示"等逻辑，需要适配虚拟滚动

### 实施状态：已完成

**完成内容：**
- 安装 `@tanstack/vue-virtual@3.13.29`
- `Chat.vue`：消息列表改为虚拟滚动，使用 `useVirtualizer` + 动态高度测量（`measureElement` + ResizeObserver）
- `Chat.vue`：`estimateSize` 根据消息角色和内容长度动态估算高度（用户消息 60px，AI 消息按内容长度 80-600px）
- `Chat.vue`：新增 `isNearBottom` 状态检测（150px 阈值），用户滚动到上方时流式输出不自动滚动
- `Chat.vue`：`scrollToBottom()` 改为基于虚拟器的 `scrollToIndex`，仅在 `isNearBottom` 时触发
- `Chat.vue`：发送消息时强制 `isNearBottom = true`，确保用户消息发送后自动滚到底部
- `Chat.vue`：`loadHistory` 加载完成后自动滚到底部
- 虚拟列表容器使用绝对定位 + `translateY` 实现高性能渲染，仅渲染可视区域 + 5 条 overscan

---

## 附录：微服务拆分时的数据库规划

### 是否需要每个微服务独立数据库？

**是的，这是微服务架构的核心原则之一。**

微服务拆分的一个关键原则是 **Database per Service**（每个服务拥有自己的数据库）。原因：

1. **数据隔离**：服务 A 不能直接查询服务 B 的表，只能通过 API 访问。这保证了服务边界清晰
2. **独立演进**：服务 A 的表结构变更不应影响服务 B
3. **独立扩展**：chat 服务的数据库可能需要读写分离，而 rag 服务的数据库可能需要向量优化，需求不同
4. **故障隔离**：一个服务的数据库故障不会影响其他服务

### 推荐的数据库拆分方案

```
Phase 1（当前）：共享 lightbot 库
┌─────────────────────────────────────────────┐
│              lightbot (PostgreSQL)           │
│  38 张表全部在一个库中                        │
└─────────────────────────────────────────────┘

Phase 3（微服务化后）：按服务拆库
┌──────────────────┐  ┌──────────────────┐
│ lightbot_chat    │  │ lightbot_agent   │
│ chat_session     │  │ agent            │
│ message          │  │ agent_version    │
│                  │  │ subagent         │
└──────────────────┘  └──────────────────┘

┌──────────────────┐  ┌──────────────────┐
│ lightbot_rag     │  │ lightbot_tool    │
│ knowledge        │  │ tool             │
│ document         │  │ mcp_server       │
│ chunk            │  │ skill            │
│ embedding        │  │ tool_call        │
│ qa_pair          │  │                  │
└──────────────────┘  └──────────────────┘

┌──────────────────┐  ┌──────────────────┐
│ lightbot_obs     │  │ lightbot_eval    │
│ llm_trace        │  │ eval_* (10张表)  │
│                  │  │                  │
└──────────────────┘  └──────────────────┘

┌──────────────────┐
│ lightbot_user    │  ← 用户/认证独立库
│ users            │
│ system_config    │
└──────────────────┘
```

### 拆分时的数据迁移策略

1. **Phase 1 阶段**：在代码层面先按服务域划分 Package（当前已是这样），但共享同一个数据库
2. **Phase 2 阶段**：为每个服务域创建独立的 Schema（PostgreSQL Schema），代码中通过 `@TableName("chat.chat_session")` 指定 schema
3. **Phase 3 阶段**：将 Schema 拆分为独立数据库，服务间通过 API 调用

```sql
-- Phase 2: Schema 隔离（同一个库内）
CREATE SCHEMA chat;
CREATE SCHEMA agent;
CREATE SCHEMA rag;
CREATE SCHEMA tool;
CREATE SCHEMA obs;
CREATE SCHEMA eval;

-- 移动表到对应 schema
ALTER TABLE chat_session SET SCHEMA chat;
ALTER TABLE message SET SCHEMA chat;
ALTER TABLE agent SET SCHEMA agent;
-- ...
```

这种渐进式拆分方式风险最低：先用 Schema 隔离验证边界是否正确，再物理拆库。

### 跨服务查询的处理

拆库后，以下跨域查询需要改为 API 调用：

| 当前查询 | 拆分后处理 |
|---------|-----------|
| chat 服务查 Agent 配置 | Feign 调用 agent 服务 |
| RAG 服务查 Knowledge 配置 | Feign 调用 rag 服务 |
| Trace 服务查 Agent 名称 | Trace 表冗余存储 agent_name（已有） |
| Dashboard 跨表统计 | 独立的聚合查询服务，或各服务提供统计 API |

**关键原则：数据冗余优于跨服务 JOIN。** 在 Trace 表中冗余 `agent_name`、`model` 等字段，避免为了一个名称去调用其他服务。
