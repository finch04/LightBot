# LightBot 生产环境架构优化方案

> 本文档梳理 LightBot 项目从单体开发环境推向企业级生产环境所需的架构优化点。
> 每项包含需求分析、涉及功能、技术设计、工作量评估和难点分析。

---

## 目录

1. [Redis 缓存体系建设](#1-redis-缓存体系建设)
2. [数据库连接池与读写分离](#2-数据库连接池与读写分离)
3. [消息队列解耦长任务](#3-消息队列解耦长任务)
4. [微服务拆分](#4-微服务拆分)
5. [API 网关与限流](#5-api-网关与限流)
6. [向量数据库索引优化](#6-向量数据库索引优化)
7. [文档处理流水线优化](#7-文档处理流水线优化)
8. [工作流引擎并行执行](#8-工作流引擎并行执行)
9. [可观测性体系增强](#9-可观测性体系增强)
10. [多租户与权限体系](#10-多租户与权限体系)
11. [配置管理与环境隔离](#11-配置管理与环境隔离)
12. [数据库迁移工具](#12-数据库迁移工具)
13. [缓存一致性与热点防护](#13-缓存一致性与热点防护)
14. [文件存储优化](#14-文件存储优化)
15. [LLM 调用治理](#15-llm-调用治理)
16. [会话与消息存储优化](#16-会话与消息存储优化)
17. [前端优化](#17-前端优化)
18. [安全加固](#18-安全加固)

---

## 1. Redis 缓存体系建设

### 需求分析

当前项目仅将 Redis 用于 Sa-Token 会话存储、任务队列和模型/提供商缓存。智能体配置、知识库配置、Prompt 模板、系统配置等高频读取数据每次请求都穿透到数据库。在生产环境中，这些配置数据读多写少（读写比约 100:1），是天然的缓存场景。

### 涉及功能

| 缓存对象 | 当前读取方式 | 读取频率 | 缓存收益 |
|----------|-------------|---------|---------|
| Agent 配置 | 每次 chat 请求查 DB | 每次对话 | 高 |
| Agent Version 快照 | 每次发布/预览查 DB | 中频 | 中 |
| Knowledge 配置 | RAG 检索时查 DB | 每次 RAG | 高 |
| Prompt 模板 | 使用时查 DB | 中频 | 中 |
| SystemConfig | 多处读取 | 高频 | 高 |
| SubAgent 配置 | 委派时查 DB | 中频 | 中 |
| Tool 定义 | Agent 加载时查 DB | 每次对话 | 高 |
| Model/ModelProvider | 已缓存 | - | 已完成 |

### 技术设计

**方案：引入 Spring Cache + RedisCacheManager 统一缓存抽象**

```
现有: Controller -> Service -> Mapper -> DB
优化: Controller -> Service -> @Cacheable -> Redis (miss) -> Mapper -> DB
```

**1. 缓存配置类**

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

        // 按业务设置不同 TTL
        Map<String, RedisCacheConfiguration> configMap = Map.of(
            "agent",       defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "knowledge",   defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "prompt",      defaultConfig.entryTtl(Duration.ofMinutes(30)),
            "systemConfig",defaultConfig.entryTtl(Duration.ofHours(1)),
            "tool",        defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "subagent",    defaultConfig.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(configMap)
            .transactionAware()
            .build();
    }
}
```

**2. Service 层加注解**

```java
// Agent 查询
@Cacheable(value = "agent", key = "#id")
public Agent getById(Long id) { ... }

// 清除缓存
@CacheEvict(value = "agent", key = "#id")
public void updateAgent(Agent agent) { ... }

// 批量清除（如删除时）
@CacheEvict(value = "agent", allEntries = true)
public void deleteAgent(Long id) { ... }
```

**3. 缓存预热**

在现有 `CacheWarmUpRunner` 基础上扩展，启动时预热 Agent、Knowledge、SystemConfig 等高频数据。

### 工作量评估

| 项目 | 工时 |
|------|------|
| RedisCacheConfig 配置类 | 0.5d |
| Agent/Knowledge/Prompt/Tool/SubAgent/SystemService 加注解 | 2d |
| 缓存失效策略（写操作清缓存） | 1d |
| 缓存预热扩展 | 0.5d |
| 单元测试 + 集成测试 | 1d |
| **合计** | **5d** |

### 难点

- **缓存一致性**：多处写操作（Agent CRUD、版本发布、配置变更）都需要正确清除缓存，遗漏会导致脏读
- **缓存穿透**：查询不存在的 ID 会穿透到 DB，需要布隆过滤器或空值缓存
- **JSON 序列化**：Entity 中的 JSONB 字段（config、spans 等）序列化/反序列化需要兼容现有 TypeHandler
- **分布式锁**：缓存重建期间需要防止缓存击穿（多线程同时 rebuild）

---

## 2. 数据库连接池与读写分离

### 需求分析

当前使用 Spring Boot 默认的 HikariCP 连接池，未显式配置连接池参数。生产环境中，当并发对话请求增多时，默认配置（最大连接数 10）可能成为瓶颈。此外，所有读写都走主库，读操作占比约 80%，可通过读写分离降低主库压力。

### 涉及功能

- 所有数据库查询操作（Agent/Knowledge/Chat/Eval 等 38 张表）
- 向量检索（pgvector 的 embedding 表查询）
- 消息历史加载
- 仪表盘统计查询

### 技术设计

**1. HikariCP 连接池调优**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30        # 默认 10，生产环境调大
      minimum-idle: 10             # 最小空闲连接
      idle-timeout: 600000         # 空闲超时 10min
      max-lifetime: 1800000        # 连接最大生命周期 30min
      connection-timeout: 3000     # 连接超时 3s
      leak-detection-threshold: 60000  # 连接泄漏检测 60s
```

**2. 读写分离（ShardingSphere 或自定义 RoutingDataSource）**

```
                     ┌─────────────┐
                     │  应用层      │
                     └──────┬──────┘
                            │
                     ┌──────▼──────┐
                     │ RoutingDS   │  @Transactional(readOnly=true) -> 从库
                     │             │  @Transactional            -> 主库
                     └──┬──────┬───┘
                        │      │
                 ┌──────▼──┐ ┌─▼───────┐
                 │ 主库 PG │ │ 从库 PG │
                 └─────────┘ └─────────┘
```

- 使用 Spring 的 `AbstractRoutingDataSource` + `DynamicDataSourceContextHolder`
- `@Transactional(readOnly = true)` 自动路由到从库
- 写操作强制走主库
- 对于强一致性读（如写后读），使用 `@Master` 注解强制走主库

### 工作量评估

| 项目 | 工时 |
|------|------|
| HikariCP 参数调优 | 0.5d |
| 读写分离 DataSource 路由 | 2d |
| 从库配置 + 主从复制（DBA 侧） | 1d |
| 测试验证 | 1d |
| **合计** | **4.5d** |

### 难点

- **主从延迟**：写后立即读可能读到从库旧数据，需要会话级绑定或强制走主库
- **事务内读写混合**：同一个事务内既有读又有写，必须走主库
- **pgvector 查询**：向量检索是否支持从库需要验证

---

## 3. 消息队列解耦长任务

### 需求分析

当前长任务（文档处理、评测实验、基准生成等）使用自研的 Redis List 任务队列（`lightbot:task:queue`）。存在以下问题：

1. **无重试机制**：任务失败后仅标记 FAILED，无自动重试
2. **无死信队列**：反复失败的任务无隔离处理
3. **无优先级**：所有任务 FIFO，无法区分紧急/普通任务
4. **消费者固定 2 线程**：无法根据负载动态扩缩容
5. **无消费确认**：`leftPop` 取出即消费，消费者崩溃会丢失任务
6. **Trace 写入用 @Async**：无持久化保障，进程重启会丢失

### 涉及功能

| 任务类型 | 当前方式 | 优化后 |
|---------|---------|--------|
| 文档上传解析 | Redis List | MQ Topic |
| 文档向量化 | Redis List | MQ Topic |
| 图谱抽取 | Redis List | MQ Topic |
| 评测实验运行 | Redis List | MQ Topic |
| 基准 AI 生成 | Redis List | MQ Topic |
| 基准导入 | Redis List | MQ Topic |
| RAG 评估 | Redis List | MQ Topic |
| 问答对生成 | Redis List | MQ Topic |
| Trace 写入 | @Async | MQ Topic |
| 工具调用记录 | @Async | MQ Topic |

### 技术设计

**方案：引入 RocketMQ 替代 Redis List 任务队列**

```
现有:
  TaskService.createTask() -> Redis List (rightPush)
  TaskConsumerConfig -> 固定线程池 leftPop -> TaskExecutor

优化:
  TaskService.createTask() -> RocketMQ Topic (lightbot-task)
  RocketMQ Consumer Group -> 按任务类型路由 -> TaskExecutor
```

**为什么选 RocketMQ 而非 RabbitMQ/Kafka：**

| 对比项 | RocketMQ | RabbitMQ | Kafka |
|--------|----------|----------|-------|
| 延迟消息 | 原生支持 | 插件 | 不支持 |
| 死信队列 | 原生支持 | 原生支持 | 需自建 |
| 顺序消息 | 支持 | 支持 | 支持 |
| 事务消息 | 支持 | 不支持 | 支持 |
| Java 生态 | 最佳 | 良好 | 良好 |
| 运维复杂度 | 中 | 低 | 高 |
| 吞吐量 | 高 | 中 | 最高 |

**核心改造点：**

```java
// 1. 任务创建 -> 发送 MQ 消息
@Service
public class TaskService {
    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    public Task createTask(TaskType type, Long userId, String params) {
        Task task = new Task(type, userId, params);
        taskMapper.insert(task);
        // 发送 MQ 消息，携带 taskId
        rocketMQTemplate.syncSend("lightbot-task:" + type.getTag(),
            MessageBuilder.withPayload(task.getId()).build());
        return task;
    }
}

// 2. 消费者替代 TaskConsumerConfig
@RocketMQMessageListener(
    topic = "lightbot-task",
    consumerGroup = "lightbot-task-consumer",
    selectorExpression = "DOCUMENT_UPLOAD || DOCUMENT_INGEST || ..."
)
public class TaskMQConsumer implements RocketMQListener<Long> {
    @Override
    public void onMessage(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        TaskExecutor executor = executorMap.get(task.getType().getBeanName());
        executor.execute(task);
        // 消费成功后 ACK，失败自动重试
    }
}

// 3. Trace 写入改为 MQ
@RocketMQMessageListener(topic = "lightbot-trace", ...)
public class TraceMQConsumer implements RocketMQListener<LlmTrace> {
    @Override
    public void onMessage(LlmTrace trace) {
        llmTraceService.save(trace);
    }
}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| RocketMQ 部署 + Spring Boot 集成 | 1d |
| TaskService 改造（发消息） | 1d |
| 消费者替代 TaskConsumerConfig | 2d |
| Trace/ToolCall 写入 MQ 化 | 1d |
| 死信队列 + 重试策略配置 | 1d |
| 监控面板接入 | 0.5d |
| 测试 + 压测 | 2d |
| **合计** | **8.5d** |

### 难点

- **平滑迁移**：现有 Redis 任务队列需要与 MQ 并行运行一段时间，确保无任务丢失
- **幂等性**：MQ 重试会导致任务重复执行，TaskExecutor 需要幂等设计（检查任务状态）
- **顺序性**：文档上传 -> 向量化是两阶段任务，需要保证顺序（可用 MQ 延迟消息或依赖检查）
- **本地开发**：开发环境需要 RocketMQ，增加部署复杂度（可用 Docker Compose）

---

## 4. 微服务拆分

### 需求分析

当前是单体架构（28 个 Controller、38 张表、50+ Service 全在一个模块）。随着功能增长，单体面临：

1. **部署耦合**：改一个功能需要重新部署整个应用
2. **扩展受限**：对话模块需要高并发扩展，但文档处理模块需要 CPU 密集扩展，无法独立扩缩容
3. **故障隔离**：文档解析 OOM 会导致整个应用崩溃，影响在线对话
4. **技术栈锁定**：无法对不同模块选择最适合的技术栈

### 涉及功能

**拆分边界（按业务域）：**

| 服务 | 职责 | 核心表 | 核心依赖 |
|------|------|--------|---------|
| **lightbot-gateway** | API 网关、认证、限流、路由 | - | Sa-Token, Redis |
| **lightbot-chat** | 对话核心、会话管理、消息管理 | chat_session, message | LLM, Redis |
| **lightbot-agent** | Agent/Version 管理、工作流引擎 | agent, agent_version, subagent | chat 服务 |
| **lightbot-rag** | 知识库、文档、向量检索 | knowledge, document, chunk, embedding, qa_pair | Milvus, MinIO, 向量模型 |
| **lightbot-tool** | 工具管理、MCP、Skill | tool, mcp_server, skill, tool_call | 外部 API |
| **lightbot-obs** | 可观测性、Trace、仪表盘 | llm_trace, tool_call | ClickHouse (可选) |
| **lightbot-eval** | 评测体系 | eval_* (10 张表) | LLM, RAG 服务 |
| **lightbot-prompt** | Prompt 管理与模板 | prompt, prompt_version, prompt_build_template | - |
| **lightbot-common** | 公共模块：DTO、枚举、异常、工具类 | - | - |

### 技术设计

**拆分策略：渐进式拆分，先模块化后微服务**

```
Phase 1: 模块化（当前单体内拆 Maven 模块）
  lightbot-common/       # 公共 DTO、枚举、异常
  lightbot-chat/         # 对话域
  lightbot-agent/        # Agent 域
  lightbot-rag/          # RAG 域
  lightbot-tool/         # 工具域
  lightbot-obs/          # 可观测域
  lightbot-eval/         # 评测域
  lightbot-prompt/       # Prompt 域
  lightbot-server/       # 主入口，组装所有模块

Phase 2: 微服务化（模块独立部署）
  服务间通信: OpenFeign + Nacos 服务注册
  配置中心: Nacos Config
  链路追踪: SkyWalking / Micrometer Tracing
```

**服务间通信方式：**

| 场景 | 方式 | 示例 |
|------|------|------|
| 同步查询 | OpenFeign | chat 服务查询 Agent 配置 |
| 异步事件 | RocketMQ | 文档处理完成通知 RAG 服务 |
| 实时流 | SSE 直连 | 对话流式输出（不经过网关） |

### 工作量评估

| 项目 | 工时 |
|------|------|
| Phase 1: 模块化拆分 Maven 模块 | 5d |
| Phase 1: 模块间 Facade 接口定义 | 3d |
| Phase 2: Nacos 注册中心部署 | 1d |
| Phase 2: 服务拆分 + OpenFeign | 10d |
| Phase 2: 配置中心迁移 | 2d |
| Phase 2: 链路追踪接入 | 2d |
| Phase 2: Docker Compose / K8s 编排 | 3d |
| 测试 + 回归 | 5d |
| **合计** | **31d** |

### 难点

- **数据拆分**：chat_session 被多个服务引用，需要确定数据归属。chat 服务拥有 session 表，其他服务通过 Feign 查询
- **分布式事务**：Agent 发布需要同时更新 agent 表和 agent_version 表，如果拆到不同服务需要 Saga/TCC
- **对话 SSE 流**：SSE 是长连接，经过网关需要注意超时和负载均衡策略
- **向量检索跨服务**：RAG 服务的向量检索需要返回结果给 chat 服务，延迟增加
- **开发复杂度**：本地开发需要启动多个服务，需要 Docker Compose 或 Telepresence

---

## 5. API 网关与限流

### 需求分析

当前无 API 网关、无限流、无 API 版本控制。生产环境中：

1. **无限流**：恶意用户可以无限制调用 LLM 对话接口，导致成本失控
2. **无熔断**：外部 LLM 服务不可用时，请求堆积导致线程耗尽
3. **无 API 版本**：接口变更无法平滑过渡
4. **CORS 配置硬编码**：`CorsConfig` 中硬编码了 `localhost:5173`

### 涉及功能

- 所有 `/api/**` 接口
- 对话接口（最高频、最高成本）
- 文件上传接口（带宽敏感）
- SSE 流式接口（长连接管理）

### 技术设计

**方案：Spring Cloud Gateway + Sentinel 限流**

```yaml
# 限流配置示例
spring:
  cloud:
    gateway:
      routes:
        - id: chat
          uri: lb://lightbot-chat
          predicates:
            - Path=/api/chat/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10  # 每秒 10 个请求
                redis-rate-limiter.burstCapacity: 20
        - id: agent
          uri: lb://lightbot-agent
          predicates:
            - Path=/api/agents/**

# Sentinel 熔断规则
sentinel:
  degrade:
    rules:
      - resource: llm-call
        grade: 0          # 慢调用比例
        count: 5000        # 慢调用阈值 5s
        slowRatioThreshold: 0.5
        timeWindow: 30     # 熔断 30s
        minRequestAmount: 10
```

**分层限流策略：**

| 层级 | 策略 | 目标 |
|------|------|------|
| 网关层 | IP 限流 + 用户限流 | 防刷、防 DDoS |
| 服务层 | 令牌桶（Sentinel） | 保护后端资源 |
| LLM 调用层 | 用户级 QPS + 日 Token 预算 | 控制 AI 成本 |

### 工作量评估

| 项目 | 工时 |
|------|------|
| Spring Cloud Gateway 引入 + 路由配置 | 2d |
| Sentinel 限流 + 熔断规则 | 2d |
| 用户级 LLM 调用限流 | 2d |
| API 版本规划 | 1d |
| CORS 配置外部化 | 0.5d |
| 测试 | 1d |
| **合计** | **8.5d** |

### 难点

- **SSE 长连接**：网关对 SSE 的超时配置需要特殊处理（默认 30s 超时会断开流式对话）
- **限流粒度**：IP 限流对共享出口 IP 的企业用户不公平，需要结合用户维度
- **LLM 成本控制**：需要在对话前预估 Token 消耗，但流式输出无法提前知道总量

---

## 6. 向量数据库索引优化

### 需求分析

当前 pgvector 的 `embedding` 表向量检索使用 `ORDER BY e.vector <=> #{vector}::vector LIMIT #{topK}`，**未确认是否创建了 HNSW 或 IVFFlat 索引**。当 embedding 表数据量超过 10 万条时，无索引的向量检索会退化为全表扫描，响应时间从毫秒级退化到秒级。

Milvus 侧也存在每次检索前调用 `hasCollection()` RPC 的额外开销。

### 涉及功能

- 知识库 RAG 检索（pgvector 路径）
- 知识库 RAG 检索（Milvus 路径）
- 问答对检索
- 图谱检索

### 技术设计

**1. pgvector 索引创建**

```sql
-- 为 embedding 表创建 HNSW 索引（推荐，适合中小规模高召回率场景）
CREATE INDEX IF NOT EXISTS idx_embedding_vector_hnsw
ON embedding USING hnsw (vector vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- 设置查询时的 ef_search 参数（越大召回率越高，越慢）
SET hnsw.ef_search = 100;

-- 或使用 IVFFlat（适合大规模低召回率场景）
CREATE INDEX IF NOT EXISTS idx_embedding_vector_ivfflat
ON embedding USING ivfflat (vector vector_cosine_ops)
WITH (lists = 100);
```

**2. Milvus 集合检查缓存**

```java
// EmbeddingServiceImpl 中缓存集合存在性
private final ConcurrentHashMap<String, Boolean> collectionExistsCache = new ConcurrentHashMap<>();

public boolean hasCollectionCached(Long knowledgeId) {
    return collectionExistsCache.computeIfAbsent(
        String.valueOf(knowledgeId),
        k -> milvusUtil.hasCollection(knowledgeId)
    );
}
```

**3. 混合检索优化**

当前 Milvus 检索后还要查 DB 获取 documentName，应改为 Milvus collection 中冗余存储 documentName 字段，减少跨库查询。

### 工作量评估

| 项目 | 工时 |
|------|------|
| pgvector HNSW 索引创建 + SQL 脚本 | 0.5d |
| Milvus 集合检查缓存 | 0.5d |
| Milvus collection 冗余 documentName | 1d |
| 索引参数调优（m, ef_search, lists） | 1d |
| 性能测试对比 | 1d |
| **合计** | **4d** |

### 难点

- **索引构建时间**：HNSW 索引在大数据量下构建需要较长时间，需要离线构建
- **召回率 vs 延迟**：ef_search 越大召回率越高但延迟越大，需要根据业务调优
- **增量索引**：pgvector 的 HNSW 索引在 INSERT 时自动更新，但大量插入后需要 REINDEX

---

## 7. 文档处理流水线优化

### 需求分析

当前文档处理流水线存在多个瓶颈：

1. **分块保存逐条 INSERT**：500 个 chunk = 500 次 DB 写入
2. **Embedding 调用串行**：每批 50 条，逐批同步调用
3. **MinIO 双次往返**：上传解析后再存 MinIO，ingest 又从 MinIO 下载
4. **OCR 同步阻塞**：扫描 PDF 的 OCR 可能耗时数分钟
5. **固定 3 线程池**：所有文档共享 3 个 ingest 线程
6. **MinIO ensureBucket 每次调用**：每次上传都检查 bucket 是否存在

### 涉及功能

- 知识库文档上传
- 文档解析（Tika + OCR）
- 文档分块
- 向量化（Embedding）
- 向量写入（pgvector / Milvus）

### 技术设计

**1. 批量 INSERT 优化**

```java
// 改造前：逐条保存
for (Chunk chunk : chunks) {
    chunkService.saveChunk(chunk);  // N 次 INSERT
}

// 改造后：批量保存
chunkService.saveBatch(chunks, 500);  // 1 次批量 INSERT
```

**2. Embedding 并行调用**

```java
// 改造前：串行
for (List<String> batch : batches) {
    embeddings = embed(batch);  // 阻塞等待
}

// 改造后：CompletableFuture 并行 + 限流
List<CompletableFuture<List<float[]>>> futures = batches.stream()
    .map(batch -> CompletableFuture.supplyAsync(
        () -> embed(batch), embeddingExecutor))
    .toList();
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

**3. 消除 MinIO 双次往返**

将解析后的 Markdown 内容直接通过 Redis/临时文件传递给 ingest 阶段，避免再次从 MinIO 下载。

**4. MinIO ensureBucket 缓存**

```java
private volatile boolean bucketEnsured = false;

public void ensureBucketOnce() {
    if (bucketEnsured) return;
    synchronized (this) {
        if (bucketEnsured) return;
        // ensureBucket logic
        bucketEnsured = true;
    }
}
```

**5. 动态线程池**

```java
// 替代固定的 Executors.newFixedThreadPool(3)
ThreadPoolExecutor ingestPool = new ThreadPoolExecutor(
    3,                                    // core
    Runtime.getRuntime().availableProcessors(),  // max
    60, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| Chunk 批量保存改造 | 1d |
| Embedding 并行调用 + 限流 | 2d |
| MinIO 双次往返消除 | 1d |
| ensureBucket 缓存 | 0.5d |
| 动态线程池 | 0.5d |
| 压测验证 | 1d |
| **合计** | **6d** |

### 难点

- **Embedding API 限流**：并行调用需要控制 QPS，避免触发模型提供商限流
- **内存压力**：批量操作需要控制批次大小，避免 OOM
- **错误处理**：并行调用中部分失败需要正确处理（部分成功部分失败的幂等性）

---

## 8. 工作流引擎并行执行

### 需求分析

当前工作流引擎是严格顺序执行的：`while (currentNodeId != null)` 循环逐个执行节点。当工作流包含并行分支（如同时调用多个工具、同时进行 RAG 检索和 LLM 推理）时，无法利用并行性，导致整体延迟是各节点延迟之和。

### 涉及功能

- 工作流 Agent 执行
- 并行分支节点
- 批量/循环节点
- 条件分支后的并行路径

### 技术设计

**方案：DAG 拓扑排序 + 并行执行器**

```
现有执行顺序：
  START -> LLM1 -> Tool1 -> LLM2 -> END  (串行，总耗时 = 各节点之和)

优化后：
  START -> ┌─ LLM1 ─┐
           ├─ Tool1 ─┤ -> LLM2 -> END  (并行，总耗时 = max(各节点))
           └─ RAG1 ─┘
```

**核心改造：**

```java
// WorkflowExecutorService 改造
public void executeDAG(WorkflowContext context) {
    // 1. 拓扑排序，计算每个节点的入度
    Map<String, Integer> inDegree = calculateInDegree(edges);

    // 2. 找出所有入度为 0 的节点（可并行执行）
    Queue<String> readyQueue = new ConcurrentLinkedQueue<>();
    inDegree.forEach((node, deg) -> { if (deg == 0) readyQueue.add(node); });

    // 3. 并行执行
    ExecutorService executor = Executors.newWorkStealingPool();
    while (!readyQueue.isEmpty()) {
        List<Future<?>> futures = new ArrayList<>();
        for (String nodeId : readyQueue) {
            futures.add(executor.submit(() -> {
                executeNode(nodeId, context);
                // 执行完后，将后继节点入度减 1，入度为 0 的加入下一轮
                updateSuccessors(nodeId, inDegree, nextReadyQueue);
            }));
        }
        // 等待当前轮次所有节点完成
        CompletableFuture.allOf(...).join();
        readyQueue = nextReadyQueue;
    }
}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| DAG 拓扑排序算法 | 1d |
| 并行执行器 + 同步屏障 | 3d |
| 变量上下文并发安全改造 | 2d |
| 条件分支 + 并行分支兼容 | 2d |
| 超时控制 + 异常处理 | 1d |
| 现有 18 种节点适配测试 | 3d |
| **合计** | **12d** |

### 难点

- **变量上下文并发安全**：多个并行节点可能同时写入变量，需要 ConcurrentHashMap 或 CopyOnWrite
- **条件分支与并行的组合**：条件分支后的两条路径可能各自又有并行节点
- **错误传播**：并行分支中一个失败，其他分支是取消还是继续？
- **调试困难**：并行执行后 Trace 的时序关系更复杂

---

## 9. 可观测性体系增强

### 需求分析

当前可观测性仅有基本的 LLM Trace 和 Tool Call 记录，缺少：

1. **Metrics 指标**：QPS、延迟 P99、Token 消耗趋势、错误率等
2. **分布式链路追踪**：跨服务调用无法串联（拆微服务后更严重）
3. **日志聚合**：当前日志仅 SSE 实时流，无持久化和检索
4. **告警机制**：无主动告警，依赖人工查看
5. **Trace 概览全量聚合**：`getOverview` 将所有 trace 加载到内存做聚合

### 涉及功能

- LLM 调用监控
- 对话质量监控
- 系统性能监控
- 异常告警

### 技术设计

**方案：Prometheus + Grafana + SkyWalking + ELK**

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  LightBot   │────▶│  Prometheus  │────▶│   Grafana   │
│  (Metrics)  │     │  (存储指标)   │     │  (可视化)    │
└─────────────┘     └──────────────┘     └─────────────┘

┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  LightBot   │────▶│  SkyWalking  │────▶│   Dashboard │
│  (Tracing)  │     │  (链路追踪)   │     │  (拓扑/链路) │
└─────────────┘     └──────────────┘     └─────────────┘

┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  LightBot   │────▶│  Filebeat    │────▶│ Elasticsearch│
│  (Logback)  │     │  (日志采集)   │     │ + Kibana     │
└─────────────┘     └──────────────┘     └─────────────┘
```

**1. Metrics 指标（Micrometer + Prometheus）**

```java
// 注册自定义指标
@Bean
MeterRegistryCustomizer<MeterRegistry> metricsCustomizer() {
    return registry -> {
        // LLM 调用计数
        Counter.builder("lightbot.llm.calls.total")
            .tag("provider", "dashscope")
            .register(registry);
        // Token 消耗
        DistributionSummary.builder("lightbot.llm.tokens")
            .register(registry);
        // 对话延迟
        Timer.builder("lightbot.chat.duration")
            .register(registry);
    };
}
```

**2. Trace 概览 SQL 聚合（替代内存聚合）**

```java
// 改造前：全量加载到内存
list(wrapper.select(...)).stream().reduce(...)

// 改造后：SQL 聚合
@Select("""
    SELECT COUNT(*) as total_count,
           COALESCE(SUM(total_tokens), 0) as total_tokens,
           COALESCE(AVG(total_duration_ms), 0) as avg_duration,
           COALESCE(SUM(tool_call_count), 0) as total_tool_calls
    FROM llm_trace
    WHERE trace_source = #{traceSource}
    AND create_time >= #{startTime}
""")
Map<String, Object> getAggregatedOverview(@Param("traceSource") String traceSource,
                                          @Param("startTime") LocalDateTime startTime);
```

**3. 告警规则（Grafana Alerting）**

| 告警项 | 条件 | 级别 |
|--------|------|------|
| LLM 调用错误率 | > 10% 持续 5min | P1 |
| 对话 P99 延迟 | > 30s 持续 5min | P2 |
| 文档处理积压 | 队列 > 100 持续 10min | P2 |
| 数据库连接池使用率 | > 80% 持续 5min | P1 |
| 磁盘使用率 | > 85% | P1 |

### 工作量评估

| 项目 | 工时 |
|------|------|
| Micrometer + Prometheus 接入 | 2d |
| 自定义业务指标埋点 | 2d |
| Grafana Dashboard 模板 | 1d |
| SkyWalking 接入 | 2d |
| ELK 日志聚合 | 2d |
| Trace 概览 SQL 聚合改造 | 1d |
| 告警规则配置 | 1d |
| **合计** | **11d** |

### 难点

- **Trace 概览改造**：需要在不影响现有接口的前提下改为 SQL 聚合
- **SkyWalking Agent 侵入性**：需要在启动参数中添加 Java Agent
- **日志量控制**：生产环境 INFO 日志量巨大，需要合理的日志级别和采样策略

---

## 10. 多租户与权限体系

### 需求分析

当前系统是单租户设计：所有用户共享同一个数据空间，仅有知识库级别的 `KnowledgeMember` 权限控制。企业场景需要：

1. **组织/团队隔离**：不同团队的 Agent、知识库、对话记录互相不可见
2. **细粒度权限**：管理员、开发者、普通用户、只读用户等角色
3. **资源配额**：不同租户的 Token 用量、存储空间、Agent 数量限制
4. **操作审计**：谁在什么时间做了什么操作

### 涉及功能

- 用户管理
- Agent 管理
- 知识库管理
- 对话管理
- 工具/MCP 管理
- 系统配置

### 技术设计

**方案：共享数据库 + tenant_id 字段隔离**

```sql
-- 所有业务表增加 tenant_id
ALTER TABLE agent ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE knowledge ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE chat_session ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
-- ... 所有表

-- MyBatis-Plus 自动注入 tenant_id
@Component
public class TenantLineHandler implements TenantLineHandler {
    @Override
    public Expression getTenantId() {
        return new LongValue(SecurityContextHolder.getTenantId());
    }
    @Override
    public boolean ignoreTable(String tableName) {
        return IGNORE_TABLES.contains(tableName);  // system_config, model 等全局表
    }
}
```

**权限模型：RBAC + 数据权限**

```
Tenant (租户)
  └── Role (角色: ADMIN, DEVELOPER, VIEWER)
       └── Permission (权限: agent:create, knowledge:read, ...)
  └── User (用户)
       └── UserRole (用户-角色关联)
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| Tenant 表 + 字段改造（全量表加 tenant_id） | 3d |
| MyBatis-Plus TenantLineHandler | 2d |
| RBAC 角色权限表 + 接口 | 3d |
| 前端权限控制（路由守卫 + 按钮权限） | 3d |
| 资源配额管理 | 2d |
| 操作审计日志 | 2d |
| 数据迁移脚本 | 1d |
| 测试 | 3d |
| **合计** | **19d** |

### 难点

- **数据迁移**：现有数据需要补充 tenant_id，涉及全量表
- **全局表处理**：model、model_provider、system_config 等全局表不需要租户隔离
- **跨租户查询**：管理后台需要跨租户查询，需要绕过 TenantLineHandler
- **向量检索隔离**：Milvus/pgvector 检索需要按 tenant_id 过滤
- **性能影响**：每个 SQL 都带 tenant_id 条件，需要确保有对应索引

---

## 11. 配置管理与环境隔离

### 需求分析

当前只有一个 `application.yml`，无环境隔离。生产环境需要：

1. **多环境配置**：dev / test / staging / prod 配置隔离
2. **密钥管理**：API Key 不应明文写在配置文件中
3. **动态配置**：部分配置（如限流阈值、模型选择）需要运行时热更新
4. **配置审计**：谁在什么时间修改了什么配置

### 涉及功能

- 所有外部服务连接配置（LLM、Redis、PG、Milvus、MinIO、Neo4j）
- 业务配置（任务消费者线程数、OCR 配置等）
- 密钥管理

### 技术设计

**方案：Nacos Config + Vault**

```yaml
# application.yml 仅保留环境无关配置
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - optional:nacos:lightbot-common.yml
      - optional:nacos:lightbot-${spring.profiles.active}.yml

# Nacos 中的配置（按环境隔离）
# lightbot-dev.yml
lightbot:
  task:
    consumer:
      pool-size: 2

# lightbot-prod.yml
lightbot:
  task:
    consumer:
      pool-size: 8
```

**密钥管理：**

```yaml
# 使用 Vault 或环境变量，不硬编码
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
    openai:
      api-key: ${OPENAI_API_KEY}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| Nacos Config 部署 + 集成 | 1d |
| 多环境配置文件拆分 | 1d |
| 密钥外部化（环境变量/Vault） | 1d |
| 动态配置热更新 | 2d |
| **合计** | **5d** |

---

## 12. 数据库迁移工具

### 需求分析

当前使用手动 SQL 脚本管理数据库变更（`sql/` 目录下 7 个增量脚本）。问题：

1. **无法自动执行**：每次部署需要手动执行 SQL
2. **无回滚能力**：脚本不支持 DOWN 迁移
3. **多人协作冲突**：多人同时修改表结构容易冲突
4. **无执行记录**：无法追踪哪些脚本已执行

### 涉及功能

- 全部 38 张表的 DDL 管理
- 数据迁移脚本

### 技术设计

**方案：引入 Flyway**

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
```

```
resources/db/migration/
  V1__init_schema.sql          # 基线（现有 init-2026-05-29.sql）
  V2__add_eval_tables.sql      # 评测表
  V3__add_version_fields.sql   # 版本管理字段
  ...
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| Flyway 集成 + 配置 | 0.5d |
| 现有 SQL 脚本整理为 V1 基线 | 1d |
| 后续增量脚本规范化 | 0.5d |
| **合计** | **2d** |

---

## 13. 缓存一致性与热点防护

### 需求分析

引入 Redis 缓存后，需要解决：

1. **缓存穿透**：查询不存在的 ID，每次都打到 DB
2. **缓存击穿**：热点 Key 过期瞬间，大量并发请求同时重建缓存
3. **缓存雪崩**：大量 Key 同时过期，DB 瞬间压力暴增
4. **数据一致性**：DB 更新后缓存未及时失效

### 技术设计

**1. 布隆过滤器防穿透**

```java
@Component
public class AgentBloomFilter {
    private BloomFilter<Long> filter;

    @PostConstruct
    public void init() {
        filter = BloomFilter.create(Funnels.longFunnel(), 10000, 0.01);
        // 加载所有 agent ID
        agentMapper.selectList().forEach(a -> filter.put(a.getId()));
    }

    public boolean mightExist(Long id) {
        return filter.mightContain(id);
    }
}
```

**2. 分布式锁防击穿**

```java
@Cacheable(value = "agent", key = "#id")
public Agent getById(Long id) {
    String lockKey = "lock:agent:" + id;
    try {
        if (redisLock.tryLock(lockKey, 3, TimeUnit.SECONDS)) {
            Agent agent = agentMapper.selectById(id);
            if (agent == null) {
                // 空值缓存，防穿透
                cacheManager.getCache("agent").put(id, NULL_AGENT);
            }
            return agent;
        }
    } finally {
        redisLock.unlock(lockKey);
    }
    return null;
}
```

**3. 过期时间加随机因子防雪崩**

```java
// 基础 TTL 30 分钟 + 随机 0~5 分钟
long ttl = 30 * 60 + ThreadLocalRandom.current().nextInt(300);
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| 布隆过滤器 | 1d |
| 分布式锁防击穿 | 1d |
| 随机 TTL 防雪崩 | 0.5d |
| 缓存监控指标 | 0.5d |
| **合计** | **3d** |

---

## 14. 文件存储优化

### 需求分析

当前 MinIO 使用存在以下问题：

1. **`ensureBucket()` 每次上传都调用**：多余的 RPC 开销
2. **`downloadBytes` 全量读入内存**：大文件 OOM 风险
3. **无分片上传**：100MB 文件单次上传，失败需全部重传
4. **预签名 URL 7 天过期**：对于长期存储场景不够灵活

### 技术设计

**1. ensureBucket 启动时调用一次**

```java
@PostConstruct
public void init() {
    ensureBucket();
}
```

**2. 流式下载替代全量读取**

```java
// 改造前
byte[] data = in.readAllBytes();  // 全量读入内存

// 改造后
try (InputStream is = minioClient.getObject(...);
     OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile))) {
    is.transferTo(os);  // 流式写入临时文件
}
```

**3. 分片上传**

```java
// 大文件使用分片上传
ComposeObjectArgs args = ComposeObjectArgs.builder()
    .bucket(bucketName)
    .object(objectName)
    .sources(partSources)
    .build();
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| ensureBucket 缓存 | 0.5d |
| 流式下载改造 | 1d |
| 分片上传 | 2d |
| 测试 | 1d |
| **合计** | **4.5d** |

---

## 15. LLM 调用治理

### 需求分析

当前 LLM 调用存在以下问题：

1. **无 HTTP 连接池配置**：每次调用可能创建新连接
2. **重试用 Thread.sleep 阻塞线程**：在 reactor 线程上 sleep 会阻塞事件循环
3. **无超时配置**：LLM 调用无整体超时，可能无限等待
4. **无成本控制**：用户可以无限调用，Token 消耗无上限
5. **工具调用串行**：默认只执行第一个工具调用，需多轮 LLM 交互

### 技术设计

**1. HTTP 连接池配置**

```java
@Bean
public WebClient.Builder webClientBuilder() {
    ConnectionProvider provider = ConnectionProvider.builder("llm-pool")
        .maxConnections(100)
        .maxIdleTime(Duration.ofSeconds(20))
        .maxLifeTime(Duration.ofMinutes(5))
        .pendingAcquireTimeout(Duration.ofSeconds(5))
        .build();
    return WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(60))
        ));
}
```

**2. 响应式重试替代 Thread.sleep**

```java
// 改造前
Thread.sleep(delayMs);

// 改造后
Mono.delay(Duration.ofMillis(delayMs))
    .then(retryOperation());
```

**3. Token 预算控制**

```java
@Service
public class TokenBudgetService {
    // 用户级日 Token 预算
    public boolean checkBudget(Long userId, int estimatedTokens) {
        String key = "token_budget:" + userId + ":" + LocalDate.now();
        Long used = redisTemplate.opsForValue().increment(key, estimatedTokens);
        redisTemplate.expire(key, Duration.ofDays(1));
        return used <= getUserDailyLimit(userId);
    }
}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| HTTP 连接池配置 | 1d |
| 响应式重试改造 | 2d |
| LLM 调用超时配置 | 0.5d |
| Token 预算控制 | 2d |
| 工具并行调用优化 | 2d |
| 测试 | 1d |
| **合计** | **8.5d** |

---

## 16. 会话与消息存储优化

### 需求分析

1. **消息查询无复合索引**：`ORDER BY createTime DESC LIMIT N` 在大表上慢
2. **Session stats 更新有竞态条件**：read-modify-write 可能丢数据
3. **摘要生成加载全量历史**：内存压力
4. **工作流历史无限制加载**：`listBySessionId` 无 LIMIT

### 技术设计

**1. 添加复合索引**

```sql
CREATE INDEX idx_message_session_time ON message (session_id, create_time DESC);
CREATE INDEX idx_chunk_knowledge ON chunk (knowledge_id);
CREATE INDEX idx_llm_trace_source_time ON llm_trace (trace_source, create_time DESC);
```

**2. Session stats 原子更新**

```java
// 改造前：read-modify-write
session.setMessageCount(session.getMessageCount() + 1);
updateById(session);

// 改造后：SQL 原子递增
@Update("UPDATE chat_session SET message_count = message_count + #{delta}, " +
        "total_input_tokens = total_input_tokens + #{inputTokens}, " +
        "total_output_tokens = total_output_tokens + #{outputTokens} " +
        "WHERE id = #{sessionId}")
void incrementStats(@Param("sessionId") Long sessionId,
                    @Param("delta") int delta,
                    @Param("inputTokens") int inputTokens,
                    @Param("outputTokens") int outputTokens);
```

**3. 工作流历史限制**

```java
// 改造前
messageService.listBySessionId(sessionId)  // 无限制

// 改造后
messageService.listBySessionId(sessionId, maxHistoryMessages)  // 限制 50 条
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| 数据库索引创建 | 0.5d |
| Session stats 原子更新 | 1d |
| 摘要生成优化（增量加载） | 1d |
| 工作流历史限制 | 0.5d |
| **合计** | **3d** |

---

## 17. 前端优化

### 需求分析

1. **无构建优化配置**：未配置代码分割、Tree Shaking、Gzip 压缩
2. **无 CDN 加速**：静态资源从源站加载
3. **无前端监控**：无错误采集、性能监控
4. **SSE 重连机制**：断线后无自动重连
5. **大列表性能**：Trace 列表、消息列表无虚拟滚动

### 技术设计

**1. Vite 构建优化**

```js
// vite.config.js
export default {
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'ant-design-vue': ['ant-design-vue'],
          'echarts': ['echarts'],
          'vendor': ['vue', 'vue-router', 'pinia', 'axios'],
        }
      }
    },
    chunkSizeWarningLimit: 1000,
    target: 'es2020',
    minify: 'terser',
    terserOptions: {
      compress: { drop_console: true, drop_debugger: true }
    }
  }
}
```

**2. Nginx Gzip + 缓存**

```nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript text/xml;
gzip_min_length 1024;

location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}
```

**3. SSE 自动重连**

```js
function createSSE(url, options = {}) {
  const { maxRetries = 10, retryDelay = 1000 } = options
  let retries = 0

  function connect() {
    const es = new EventSource(url)
    es.onerror = () => {
      es.close()
      if (retries < maxRetries) {
        retries++
        setTimeout(connect, retryDelay * Math.min(retries, 5))
      }
    }
    es.onopen = () => { retries = 0 }
    return es
  }
  return connect()
}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| Vite 构建优化 | 1d |
| Nginx 配置 | 0.5d |
| 前端错误监控（Sentry） | 1d |
| SSE 自动重连 | 1d |
| 大列表虚拟滚动 | 2d |
| **合计** | **5.5d** |

---

## 18. 安全加固

### 需求分析

1. **默认密码**：admin/admin123 硬编码在 `AdminUserInitializer` 中
2. **无 API 签名**：接口无防重放、防篡改
3. **敏感信息日志**：LLM Trace 中存储了完整的系统提示词和用户消息
4. **无审计日志**：无法追踪谁做了什么操作
5. **文件上传无安全扫描**：仅限制了文件类型，无内容安全扫描（已有 SensitiveWordFilter 但仅用于对话）
6. **SQL 注入风险**：`PgSqlTool` 允许执行用户提供的 SQL

### 技术设计

**1. 密码策略**

```java
// 首次启动强制修改密码
@PostConstruct
public void checkDefaultPassword() {
    if (isDefaultPassword(adminUser)) {
        log.warn("[安全] 检测到默认密码，请立即修改！");
        // 标记需要修改密码，前端弹窗强制修改
    }
}
```

**2. 审计日志**

```java
@Aspect
@Component
public class AuditLogAspect {
    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint pjp, AuditLog auditLog) {
        Long userId = StpUtil.getLoginIdAsLong();
        String action = auditLog.value();
        // 记录：谁、什么时间、做了什么操作、操作了什么资源、结果如何
        auditLogService.record(userId, action, resource, result);
        return pjp.proceed();
    }
}
```

**3. PgSqlTool 安全限制**

```java
// 禁止危险操作
private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
    "DROP", "DELETE", "TRUNCATE", "ALTER", "CREATE", "GRANT", "REVOKE"
);

public ToolResult execute(ToolInput input) {
    String sql = input.getString("sql").toUpperCase();
    for (String keyword : FORBIDDEN_KEYWORDS) {
        if (sql.contains(keyword)) {
            return ToolResult.error("不允许执行 " + keyword + " 操作");
        }
    }
    // 只允许 SELECT 查询
}
```

### 工作量评估

| 项目 | 工时 |
|------|------|
| 密码策略改造 | 1d |
| 审计日志 | 2d |
| PgSqlTool 安全限制 | 0.5d |
| 敏感信息脱敏 | 1d |
| 文件上传安全扫描增强 | 1d |
| 安全测试 | 2d |
| **合计** | **7.5d** |

---

## 总览：优先级与工作量矩阵

| 优化项 | 优先级 | 工时 | 阶段 |
|--------|--------|------|------|
| Redis 缓存体系 | P0 | 5d | Phase 1 |
| 数据库索引 + 连接池 | P0 | 4.5d | Phase 1 |
| 会话与消息存储优化 | P0 | 3d | Phase 1 |
| 向量索引优化 | P0 | 4d | Phase 1 |
| 安全加固 | P0 | 7.5d | Phase 1 |
| 配置管理与环境隔离 | P1 | 5d | Phase 1 |
| 数据库迁移工具 | P1 | 2d | Phase 1 |
| API 网关与限流 | P1 | 8.5d | Phase 2 |
| LLM 调用治理 | P1 | 8.5d | Phase 2 |
| 消息队列解耦 | P1 | 8.5d | Phase 2 |
| 文档处理优化 | P1 | 6d | Phase 2 |
| 缓存一致性与热点防护 | P2 | 3d | Phase 2 |
| 文件存储优化 | P2 | 4.5d | Phase 2 |
| 可观测性增强 | P2 | 11d | Phase 2 |
| 前端优化 | P2 | 5.5d | Phase 2 |
| 工作流并行执行 | P3 | 12d | Phase 3 |
| 微服务拆分 | P3 | 31d | Phase 3 |
| 多租户与权限 | P3 | 19d | Phase 3 |

**阶段规划：**

| 阶段 | 目标 | 预估总工时 | 周期 |
|------|------|-----------|------|
| **Phase 1** | 单体优化，可上线 | ~31d | 6-8 周 |
| **Phase 2** | 性能与稳定性提升 | ~66d | 10-12 周 |
| **Phase 3** | 架构升级，企业级 | ~62d1 | 12-16 周 |

---

## 附录：当前架构 vs 目标架构

```
当前架构（单体）:
┌──────────────────────────────────────────────┐
│                lightbot-server               │
│  Controller -> Service -> Mapper -> PostgreSQL│
│  Redis (任务队列 + 缓存)                       │
│  MinIO (文件存储)                              │
│  Milvus (向量检索)                             │
│  Neo4j (知识图谱)                              │
└──────────────────────────────────────────────┘

Phase 1 目标（优化单体）:
┌──────────────────────────────────────────────┐
│                lightbot-server               │
│  + Spring Cache + Redis                      │
│  + HikariCP 调优 + 读写分离                    │
│  + Flyway 数据库迁移                           │
│  + Nacos 配置中心                              │
│  + pgvector HNSW 索引                         │
│  + 安全加固 + 审计日志                          │
└──────────────────────────────────────────────┘

Phase 2 目标（性能与稳定性）:
┌──────────────────────────────────────────────┐
│  Gateway (Spring Cloud Gateway + Sentinel)   │
│  ├── lightbot-server (优化后)                │
│  ├── RocketMQ (任务队列 + Trace)              │
│  ├── Prometheus + Grafana (监控)              │
│  ├── ELK (日志)                               │
│  └── SkyWalking (链路追踪)                    │
└──────────────────────────────────────────────┘

Phase 3 目标（微服务 + 多租户）:
┌──────────────────────────────────────────────┐
│  Gateway                                     │
│  ├── lightbot-chat (对话服务)                 │
│  ├── lightbot-agent (Agent 服务)              │
│  ├── lightbot-rag (RAG 服务)                  │
│  ├── lightbot-tool (工具服务)                  │
│  ├── lightbot-obs (可观测服务)                 │
│  ├── lightbot-eval (评测服务)                  │
│  └── Nacos (注册中心 + 配置中心)               │
└──────────────────────────────────────────────┘
```
