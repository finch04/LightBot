# 后端优化文档

> 基于完整代码审查，聚焦 `lightbot-server` 核心模块的可优化点
>
> 生成时间：2026-06-23
> 最后更新：2026-06-24（标记已修复项）

---

## 一、需求分析

### 1.1 ChatServiceImpl 巨型类（~1477 行）❌ 未修复

**文件**：`lightbot-server/src/main/java/com/lightbot/service/impl/ChatServiceImpl.java`

**问题**：
- 单类承载 SSE 流式推理、工具调用循环、RAG 参考构建、消息持久化、Trace 记录等全部职责
- RAG 参考构建逻辑被复制了 4 处（`buildRagMetadataJson`/`processToolCallsRecursively`/`buildToolMetadataFlux`/`getRagReferences`）
- 工具调用日志记录逻辑被复制了 5 处
- `processToolCallsRecursively()` ~306 行、`processBlockingRound()` ~257 行，嵌套深度 5-6 层
- 已抽取部分中间件（`InitMiddleware`/`MessageMiddleware`/`ToolPrepMiddleware`）和辅助类（`ToolEventGenerator`/`ToolArgsSanitizer`），但核心循环和 RAG/日志逻辑仍为单体

**影响**：可维护性极差，修改一处需同步修改多处重复逻辑，容易遗漏

### 1.2 SensitiveWordFilter 正则重复编译 ✅ 已修复

**文件**：`lightbot-server/.../util/SensitiveWordFilter.java`

**当前状态**：使用 `PATTERN_CACHE`（`ConcurrentHashMap<String, Pattern>`）+ `computeIfAbsent` 预编译缓存，每个敏感词只编译一次

**注意**：无 Bloom Filter 实现，匹配方式为遍历词表 + `Pattern.find()`；词表每次从 `configMap` 重新解析，配置变更即时生效无需重启

### 1.3 ToolServiceImpl 启动扫描 ⚠️ 部分修复

**文件**：`lightbot-server/.../service/impl/ToolServiceImpl.java`

**当前状态**：使用 `cachedBuiltinCallbacks` 双重检查锁懒加载，不会每次请求都扫描 Bean。但未使用 `@PostConstruct` 启动预热，首次请求有冷启动开销

**影响**：仅首次调用有延迟，后续调用无性能问题

### 1.4 ChatSessionServiceImpl.updateStats 竞态条件 ❌ 未修复

**文件**：`lightbot-server/.../service/impl/ChatSessionServiceImpl.java`

**问题**：`updateStats()` (line 190-202) 使用先读后写模式：读取 session → Java 中累加 `messageCount`/`totalTokens` → `updateById()`。并发请求会导致计数丢失

**修复方案**：改为原子 SQL `UPDATE chat_session SET message_count = message_count + 1, total_tokens = total_tokens + ? WHERE id = ?`

### 1.5 CORS 配置允许所有来源 + 凭证 ✅ 已修复

**文件**：`lightbot-server/.../config/CorsConfig.java`

**当前状态**：使用 `addAllowedOriginPattern("*")` 替代已废弃的 `addAllowedOrigin("*")`，`OriginPattern` 是 Spring 5.3+ 正确兼容 `allowCredentials(true)` 的方式

### 1.6 RAG_EXECUTOR 无界线程池 ✅ 已修复

**文件**：`lightbot-server/.../config/ThreadPoolConfig.java`

**当前状态**：`ChatServiceImpl` 中无 `Executors.newCachedThreadPool()`。已有 `ThreadPoolConfig` 定义有界线程池 `lightBotExecutor`（core=8, max=32, queue=256, CallerRunsPolicy）。其他模块（TaskConsumerConfig/WorkflowSubgraphExecutor 等）均使用有界固定线程池

### 1.7 N+1 查询问题 ✅ 大部分已修复

**详细清单**：见 `docs/design/cascade-deletion-fixes.md` 循环 SQL 优化清单

| 项 | 状态 |
|----|------|
| enrichExperiment 评估器/数据集批量查询 | ✅ 已修复 |
| SearchDocumentsTool 批量查询 | ✅ 已修复 |
| KnowledgeTools 批量查询 | ✅ 已修复 |
| GraphServiceImpl.searchForRag Neo4j 合并查询 | ✅ 已修复 |
| executeExperiment 结果批量 INSERT + 间隔进度更新 | ✅ 已修复 |
| saveChunk 批量插入 | ✅ 已修复 |
| setDefaultAgent 单条 SQL | ✅ 已修复 |
| 会话批量删除（#6/#7） | 跳过（MinIO 副作用） |
| QaPairServiceImpl.batchVectorize (#16) | 待修复（部分可优化） |

### 1.8 ToolCallServiceImpl.pageList sessionId Bug ❌ 未修复

**文件**：`lightbot-server/.../service/impl/ToolCallServiceImpl.java`

**问题**：line 44 使用 `wrapper.eq(ToolCall::getMessageId, sessionId)` — 用 `messageId` 字段过滤 `sessionId`，语义错误。ToolCall 表无 `sessionId` 列，需通过 message 表关联查询

### 1.9 非流式路径只执行第一个工具调用 ❌ 未修复

**文件**：`lightbot-server/.../service/impl/ChatServiceImpl.java`

**问题**：`processChatWithToolCalls()` (line 196) 仅取 `toolCalls.get(0)`，注释说明"currently non-streaming only handles the first tool call (simplified handling)"。多工具调用被静默丢弃

### 1.10 关键操作无事务边界 ⚠️ 部分修复

**当前状态**：
- 消息持久化：`MessageMiddleware.saveMessage()` 有 `@Transactional(rollbackFor = Exception.class)`
- Trace 持久化：`LlmTraceService` 在事务外调用，message + Trace 非原子绑定

### 1.11 Agent 列表缓存驱逐过于激进 ❌ 未修复

**位置**：Agent 列表缓存逻辑

**问题**：任意 Agent 变更即清除整个列表缓存，命中率低

### 1.12 Bloom Filter 初始化后未更新 — 不适用

**当前状态**：未使用 Bloom Filter，敏感词匹配为遍历词表 + 正则。词表每次从配置重新解析，变更即时生效，不存在"初始化后未更新"问题

---

## 二、修改建议

### 2.1 ChatServiceImpl 拆分

**方案**：按职责拆分为多个 Service

```
ChatServiceImpl（主协调器，≤300行）
├── ChatSseService          — SSE 流式推理 + 工具调用循环
├── RagReferenceBuilder     — RAG 参考构建（消除 5 处重复）
├── ToolCallLogger          — 工具调用日志（消除 4 处重复）
└── MessagePersistenceService — 消息 + Trace 持久化
```

**原则**：ChatServiceImpl 只做流程编排，具体逻辑委托给子服务

### 2.2 SensitiveWordFilter 预编译

```java
// 改前：每次调用编译
Pattern pattern = Pattern.compile(keyword);

// 改后：启动时预编译缓存
private static final Map<String, Pattern> PATTERN_CACHE = new ConcurrentHashMap<>();
```

### 2.3 ToolServiceImpl 启动缓存

```java
@PostConstruct
public void init() {
    // 启动时扫描一次，缓存 toolName -> Tool 映射
    toolRegistry = applicationContext.getBeansWithAnnotation(SystemTool.class)
        .values().stream()
        .collect(Collectors.toMap(Tool::getName, Function.identity()));
}
```

### 2.4 updateStats 原子更新

```sql
UPDATE chat_session
SET message_count = message_count + 1,
    update_time = NOW()
WHERE id = #{sessionId}
```

使用 SQL 原子操作替代先读后写。

### 2.5 CORS 收紧

```java
// 改为配置化白名单
@Value("${cors.allowed-origins}")
private List<String> allowedOrigins;

// 或仅允许同源 + localhost 开发
```

### 2.6 RAG 线程池有界化

```java
private static final ExecutorService RAG_EXECUTOR = new ThreadPoolExecutor(
    4, 16, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(256),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
```

### 2.7 批量操作替代 N+1

```java
// 改前
documents.forEach(doc -> documentMapper.deleteById(doc.getId()));

// 改后
documentMapper.deleteBatchIds(documents.stream().map(Document::getId).toList());
```

### 2.8 修复 ToolCall 查询 Bug

确保 sessionId 参数正确传递到 MyBatis-Plus 的 `LambdaQueryWrapper`。

### 2.9 非流式工具调用循环

```java
while (!toolCalls.isEmpty()) {
    // 执行所有工具调用
    List<ToolResult> results = executeToolCalls(toolCalls);
    // 构建下一轮消息
    toolCalls = callModel(messages);
}
```

### 2.10 添加事务边界

```java
@Transactional(rollbackFor = Exception.class)
public void persistMessageAndTrace(Message msg, Trace trace) {
    messageService.save(msg);
    traceService.save(trace);
}
```

### 2.11 缓存精细化驱逐

改为按 agentId 驱逐而非全量清除：

```java
cache.evict("agent:list:" + agentId);
```

### 2.12 Bloom Filter 热更新

定时任务或监听器模式，定期重建 Bloom Filter，或使用 Redis Bloom Filter。

---

## 三、难点分析

| 优化项 | 难度 | 状态 | 说明 |
|--------|------|------|------|
| ChatServiceImpl 拆分 | **高** | ❌ 未修复 | 核心链路，需保证流式/非流式两条路径均正确 |
| 非流式工具循环 | **中** | ❌ 未修复 | 需要理解 SpringAI 工具调用协议 |
| 事务边界 | **中** | ⚠️ 部分修复 | 消息已有事务，Trace 缺失 |
| 竞态条件修复 | **低** | ❌ 未修复 | 改为原子 SQL 即可 |
| 正则预编译 | **低** | ✅ 已修复 | — |
| N+1 批量化 | **低** | ✅ 大部分已修复 | 详见 cascade-deletion-fixes.md |
| CORS 收紧 | **低** | ✅ 已修复 | — |
| RAG 线程池有界化 | **低** | ✅ 已修复 | — |
| ToolCall 查询 Bug | **低** | ❌ 未修复 | 需关联 message 表 |
| 缓存精细化驱逐 | **低** | ❌ 未修复 | — |

---

## 四、工作量安排

### P0 — 安全 & 数据一致性（1-2 天）

| 任务 | 预估工时 | 状态 |
|------|----------|------|
| CORS 收紧 | 0.5d | ✅ 已修复 |
| 竞态条件修复（原子 SQL） | 0.5d | ❌ 未修复 |
| 关键操作添加事务边界 | 0.5d | ⚠️ 部分修复（Trace 缺失） |
| 非流式工具调用循环修复 | 0.5d | ❌ 未修复 |

### P1 — 性能优化（2-3 天）

| 任务 | 预估工时 | 状态 |
|------|----------|------|
| SensitiveWordFilter 预编译 | 0.5d | ✅ 已修复 |
| ToolServiceImpl 启动缓存 | 0.5d | ⚠️ 部分修复（懒加载，无预热） |
| N+1 查询批量化 | 1d | ✅ 大部分已修复 |
| RAG 线程池有界化 | 0.5d | ✅ 已修复 |
| ToolCall 查询 Bug 修复 | 0.5d | ❌ 未修复 |

### P2 — 架构优化（3-5 天）

| 任务 | 预估工时 | 状态 |
|------|----------|------|
| ChatServiceImpl 拆分 | 3-4d | ❌ 未修复（已抽取部分中间件） |
| 缓存精细化驱逐 | 0.5d | ❌ 未修复 |
| ~~Bloom Filter 热更新~~ | ~~1d~~ | 不适用（未使用 Bloom Filter） |

**总预估（剩余未修复项）**：约 3-5 个工作日

---

## 五、涉及文件清单

| 文件 | 优化项 | 状态 |
|------|--------|------|
| `ChatServiceImpl.java` | 拆分、RAG 去重、工具日志去重、非流式循环 | ❌ 未修复 |
| `ChatSessionServiceImpl.java` | 竞态条件（原子 SQL） | ❌ 未修复 |
| `ToolCallServiceImpl.java` | sessionId 查询 Bug | ❌ 未修复 |
| `MessageMiddleware.java` + `ChatServiceImpl.java` | Trace 持久化事务 | ⚠️ 部分修复 |
| `AgentServiceImpl.java` / 缓存配置 | 缓存精细化驱逐 | ❌ 未修复 |
| `SensitiveWordFilter.java` | 正则预编译 | ✅ 已修复 |
| `ToolServiceImpl.java` | 启动缓存 | ⚠️ 部分修复 |
| `ThreadPoolConfig.java` | RAG 线程池有界化 | ✅ 已修复 |
| `CorsConfig.java` | CORS 配置 | ✅ 已修复 |
| `MessageServiceImpl.java` | N+1 批量化 | ✅ 已修复 |
