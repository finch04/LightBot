# 后端优化文档

> 基于完整代码审查，聚焦 `lightbot-server` 核心模块的可优化点
>
> 生成时间：2026-06-23

---

## 一、需求分析

### 1.1 ChatServiceImpl 巨型类（~1479 行）

**文件**：`lightbot-server/src/main/java/com/lightbot/service/impl/ChatServiceImpl.java`

**问题**：
- 单类承载 SSE 流式推理、工具调用循环、RAG 参考构建、消息持久化、Trace 记录等全部职责
- RAG 参考构建逻辑被复制了约 5 次（不同分支）
- 工具调用日志记录逻辑被复制了约 4 次
- 方法过长，单方法超 200 行，嵌套深度达 5-6 层

**影响**：可维护性极差，修改一处需同步修改多处重复逻辑，容易遗漏

### 1.2 SensitiveWordFilter 正则重复编译

**文件**：`lightbot-server/.../filter/SensitiveWordFilter.java`

**问题**：每次调用 `filter()` 方法时重新编译正则表达式，而非使用预编译的 `Pattern` 对象

**影响**：高频调用场景下 CPU 开销显著

### 1.3 ToolServiceImpl 启动扫描

**文件**：`lightbot-server/.../service/impl/ToolServiceImpl.java`

**问题**：每次解析工具时扫描所有 Spring Bean 查找 `@SystemTool` 注解，而非启动时缓存

**影响**：工具解析时间随 Bean 数量线性增长

### 1.4 ChatSessionServiceImpl.updateStats 竞态条件

**文件**：`lightbot-server/.../service/impl/ChatSessionServiceImpl.java`

**问题**：`updateStats` 方法先读后写，无并发保护，高并发下消息计数可能丢失

**影响**：统计数据不准确

### 1.5 CORS 配置允许所有来源 + 凭证

**文件**：`lightbot-server/.../config/WebMvcConfig.java`（或类似配置类）

**问题**：`allowedOrigins("*")` 配合 `allowCredentials(true)`，存在安全风险

**影响**：CSRF 攻击面扩大

### 1.6 RAG_EXECUTOR 无界线程池

**文件**：`lightbot-server/.../service/impl/ChatServiceImpl.java`（或 RAG 相关服务）

**问题**：`Executors.newCachedThreadPool()` 或类似无界线程池，无上限控制

**影响**：高并发时线程数失控，可能 OOM

### 1.7 N+1 查询问题

**位置**：
- 知识库删除时逐个删除文档
- 会话批量删除时逐个删除消息

**问题**：循环内逐条执行 SQL，而非批量操作

**影响**：数据量大时响应时间线性增长

### 1.8 ToolCallServiceImpl.pageList sessionId Bug

**文件**：`lightbot-server/.../service/impl/ToolCallServiceImpl.java`

**问题**：分页查询中 sessionId 参数传递或过滤存在 bug，可能导致查询结果不正确

### 1.9 非流式路径只执行第一个工具调用

**文件**：`lightbot-server/.../service/impl/ChatServiceImpl.java`

**问题**：非流式响应路径中，工具调用循环只处理了第一个工具结果，未循环执行后续工具

**影响**：非流式场景下多工具调用链断裂

### 1.10 关键操作无事务边界

**位置**：消息持久化 + 工具调用记录 + Trace 写入等操作

**问题**：多个关联数据库操作未包裹在 `@Transactional` 中

**影响**：部分失败时数据不一致

### 1.11 Agent 列表缓存驱逐过于激进

**位置**：Agent 列表缓存逻辑

**问题**：任意 Agent 变更即清除整个列表缓存，命中率低

### 1.12 Bloom Filter 初始化后未更新

**位置**：敏感词过滤的 Bloom Filter

**问题**：启动时初始化一次，后续新增敏感词不会更新 Filter

**影响**：新增敏感词无法被过滤

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

| 优化项 | 难度 | 说明 |
|--------|------|------|
| ChatServiceImpl 拆分 | **高** | 核心链路，需保证流式/非流式两条路径均正确 |
| 非流式工具循环 | **中** | 需要理解 SpringAI 工具调用协议 |
| 事务边界 | **中** | 需梳理哪些操作必须在同一事务内 |
| 竞态条件修复 | **低** | 改为原子 SQL 即可 |
| 正则预编译 | **低** | 纯重构，无业务逻辑变更 |
| N+1 批量化 | **低** | 替换为批量 API |
| CORS 收紧 | **低** | 配置变更，需测试前端跨域 |
| Bloom Filter 热更新 | **中** | 需要设计更新策略（全量/增量） |

---

## 四、工作量安排

### P0 — 安全 & 数据一致性（1-2 天）

| 任务 | 预估工时 |
|------|----------|
| CORS 收紧 | 0.5d |
| 竞态条件修复（原子 SQL） | 0.5d |
| 关键操作添加事务边界 | 0.5d |
| 非流式工具调用循环修复 | 0.5d |

### P1 — 性能优化（2-3 天）

| 任务 | 预估工时 |
|------|----------|
| SensitiveWordFilter 预编译 | 0.5d |
| ToolServiceImpl 启动缓存 | 0.5d |
| N+1 查询批量化 | 1d |
| RAG 线程池有界化 | 0.5d |
| ToolCall 查询 Bug 修复 | 0.5d |

### P2 — 架构优化（3-5 天）

| 任务 | 预估工时 |
|------|----------|
| ChatServiceImpl 拆分 | 3-4d |
| 缓存精细化驱逐 | 0.5d |
| Bloom Filter 热更新 | 1d |

**总预估**：6-10 个工作日

---

## 五、涉及文件清单

| 文件 | 优化项 |
|------|--------|
| `ChatServiceImpl.java` | 拆分、RAG 去重、工具日志去重、非流式循环、事务 |
| `SensitiveWordFilter.java` | 正则预编译 |
| `ToolServiceImpl.java` | 启动缓存 |
| `ChatSessionServiceImpl.java` | 竞态条件 |
| `ToolCallServiceImpl.java` | sessionId 查询 Bug |
| `MessageServiceImpl.java` | 批量删除 |
| `WebMvcConfig.java` | CORS 配置 |
| `BloomFilter`（相关类） | 热更新机制 |
