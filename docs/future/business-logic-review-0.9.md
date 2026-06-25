# LightBot 业务逻辑不合理之处审查报告

> 审查时间：2026-06-24
> 审查范围：后端全模块 + 前端核心模块
> 审查标准：安全性、可靠性、性能、可维护性

---

## 目录

- [一、安全类问题（Critical）](#一安全类问题critical)
- [二、可靠性问题（High）](#二可靠性问题high)
- [三、性能问题（High）](#三性能问题high)
- [四、数据一致性问题（Medium）](#四数据一致性问题medium)
- [五、代码设计问题（Medium）](#五代码设计问题medium)
- [六、前端问题（Medium）](#六前端问题medium)
- [七、代码质量问题（Low）](#七代码质量问题low)

---

## 一、安全类问题（Critical）

### 1.1 PgSqlTool SQL 注入风险 ✅ 已修复

**位置**：`PgSqlTool.java` L104, L125, L150

**问题**：`describeTable()` 将 `tableName` 直接拼接进 SQL 字符串。`query()` 方法接受 LLM 生成的原始 SQL，仅靠正则过滤（`^[a-zA-Z_][a-zA-Z0-9_]*$`），prompt injection 可绕过。

**修复内容**（2026-06-24）：
- `describeTable` 改用 `PreparedStatement` 参数化查询（列信息和索引查询均已参数化）
- `query()` 增加安全校验：禁止访问 `pg_catalog`/`information_schema`/`pg_toast`/`pg_temp` 系统 schema
- 增加危险函数拦截：`pg_sleep`/`pg_terminate_backend`/`lo_import` 等
- 增加 SQL 注释剥离后校验，防止利用注释绕过
- `DANGEROUS_KEYWORDS` 增加 `COPY`/`IMPORT`

**工作量**：已完成
**影响范围**：PgSqlTool 单个工具

---

### 1.2 ScriptNodeProcessor 无沙箱执行用户脚本 ✅ 已修复

**位置**：`ScriptNodeProcessor.java` L98-116

**问题**：用户配置的 JavaScript 通过 `ScriptEngine.eval()` 直接执行，无沙箱、无资源限制、无安全 Manager。脚本可访问完整 Java 运行时（文件读写、网络、进程）。

**修复内容**（2026-06-24）：
- 新增 `DANGEROUS_ACCESS` 正则，拦截 `Java.type`/`importClass`/`Packages.*`/`java.lang.Runtime`/`java.io`/`java.net` 等危险访问
- 脚本执行前调用 `checkScriptSecurity()` 检测，命中则拒绝执行
- 使用 `CompletableFuture.orTimeout(5s)` 包装执行，超时自动终止（防止死循环）
- 超时抛出友好错误信息："脚本执行超时（5秒），请检查是否存在死循环"

**工作量**：已完成
**影响范围**：工作流 Script 节点

---

### 1.3 ApiNodeProcessor SSRF 风险 ✅ 已修复

**位置**：`ApiNodeProcessor.java` L49

**问题**：URL 由用户配置，无白名单校验。可访问内网服务（如 `http://localhost:8080/api/admin/...`）。

**修复内容**（2026-06-24）：
- 新增 `validateUrl()` 方法，在 HTTP 请求前校验 URL
- 仅允许 http/https 协议
- 禁止 localhost、IPv6 回环（::1）
- 通过 `InetAddress` 解析 IP，禁止回环地址、站点本地地址（内网）、链路本地地址
- 禁止云厂商元数据服务（169.254.x.x）

**工作量**：已完成
**影响范围**：工作流 API 节点

---

### 1.5 Windows 命令注入（MCP Stdio 传输） ✅ 已修复

**位置**：`McpClientServiceImpl.java` L316-324

**问题**：Stdio 传输在 Windows 上用 `cmd /c` 包装命令，若命令含 `&`、`|`、`>` 等特殊字符可被利用。

**修复内容**（2026-06-24）：
- 新增 `validateCommandSafety()` 方法，在 Windows `cmd /c` 包装前校验命令和参数
- 使用正则检测 shell 元字符（`& | > < ^ ! \` $`），命中则拒绝并抛出 `MCP_CONFIG_ERROR`
- 校验覆盖 command 和所有 args

**工作量**：已完成
**影响范围**：MCP Stdio 传输

---

### 1.6 GlobalExceptionHandler 信息泄露 — 风险极低，暂不修改

**位置**：`GlobalExceptionHandler.java` L54

**问题**：`BizException` 处理器直接返回 `e.getMessage()`，若 BizException 包装了含敏感信息的 cause（如数据库连接串），会暴露给客户端。

**分析**（2026-06-24）：
- 当前项目中所有 `BizException` 均由 `ErrorCode` 枚举构造（如 `throw new BizException(ErrorCode.AGENT_NOT_FOUND)`），ErrorCode 的 message 是固定的中文提示，不含敏感信息
- `handleException` 兜底处理器已返回通用 "系统内部错误"，不会泄露原始异常
- `BizException` 构造函数不接受 cause 参数，无法携带底层异常的敏感信息
- 结论：**当前实现不存在信息泄露风险**，无需修改

**工作量**：无需修改
**影响范围**：全局异常处理

---

## 二、可靠性问题（High）

### 2.1 工作流引擎无环路检测 ✅ 已修复

**位置**：`WorkflowExecutorService.java` L143-246

**问题**：`while (currentNodeId != null)` 循环沿边遍历节点，若图中存在环路（A→B→A）将无限循环，线程永久阻塞。

**修复内容**（2026-06-24）：
- 在 `WorkflowConfigServiceImpl.validateGraph()` 中增加 DFS 环路检测
- 构建邻接表 → DFS 遍历（三色标记法：未访问/访问中/已完成）→ 发现"访问中"节点即为环路
- 检测到环路时返回环路路径描述（如 "A → B → A"），前端在发布/校验时展示
- 发布（`publish`）和独立校验（`validate`）均会触发检测，保存草稿不检测

**工作量**：已完成
**影响范围**：工作流发布校验

---

### 2.2 工作流 LLM 节点无超时保护 ✅ 已修复

**位置**：`LlmNodeProcessor.java` L237-265

**问题**：`callSync()` 和 `callStream()` 均无超时设置，LLM API 挂起时线程永久阻塞。

**修复内容**（2026-06-24）：
- 新增常量 `LLM_TIMEOUT_SECONDS = 120`
- `callSync` 使用 `CompletableFuture.supplyAsync().orTimeout(120s).join()` 包装，超时抛出 `BizException`
- `callStream` 在 Reactor Flux 上添加 `.timeout(Duration.ofSeconds(120))` 操作符，超时抛出 `BizException`
- 超时异常统一捕获并返回用户友好的错误信息："LLM调用超时（120秒），请检查模型服务状态"

**工作量**：已完成
**影响范围**：工作流 LLM 节点

---

### 2.3 QueryKnowledgeTool.SEARCH_RESULTS_MAP 内存泄漏 ✅ 已修复

**位置**：`QueryKnowledgeTool.java` L69

**问题**：静态 `ConcurrentHashMap` 按 requestId 存储搜索结果，仅通过 `getSearchResults()` 的 `remove()` 清理。若调用方因异常未读取结果，条目永久残留。

**修复内容**（2026-06-24）：
- 引入 `TimedEntry` record 包装搜索结果 + 创建时间戳
- `SEARCH_RESULTS_MAP` 改为 `ConcurrentHashMap<String, TimedEntry>`
- `getSearchResults()` 读取时检查 TTL（5 分钟），过期条目自动丢弃
- 无外部依赖，纯 JDK 实现

**工作量**：已完成
**影响范围**：知识库搜索工具

---

### 2.4 ModelFactory ChatModel 缓存无淘汰

**位置**：`ModelFactory.java` L48

**问题**：`chatModelCache` 是无 TTL 的 `ConcurrentHashMap`。Provider 凭证变更后旧 ChatModel 仍被使用，直到显式调用 `invalidateCache()`。且 `invalidateCache()` 不关闭底层 HTTP 客户端，可能泄漏连接。

**修改建议**：
- 使用 Caffeine Cache 设置 TTL（如 30 分钟）和最大容量
- `invalidateCache()` 时清理底层资源

**工作量**：1 天
**影响范围**：模型调用层

---

### 2.5 Milvus 连接初始化失败后永久禁用 ✅ 已修复

**位置**：`MilvusUtil.java` L66-89

**问题**：首次连接失败后 `available = false`，后续所有 Milvus 操作静默跳过，直到服务重启。无重试机制。

**修复内容**（2026-06-24）：
- 新增 `lastReconnectAttempt` 字段和 `RECONNECT_INTERVAL_MS = 60_000` 常量
- `getClient()` 方法增加重连逻辑：当 `available=false` 且距上次重连超过 60 秒时，尝试重新初始化客户端
- 使用 `synchronized` 保证并发安全，重连前先 `close()` 旧客户端
- 新增 `shouldRetryReconnect()` 方法控制重连频率

**工作量**：已完成
**影响范围**：向量检索

---

### 2.6 SubAgent 同步阻塞执行

**位置**：`SubAgentRuntime.java` L60-151

**问题**：`run()` 完全同步执行，阻塞调用线程直到 SubAgent 完成所有工具循环。长时间任务可能导致主 Agent 的 SSE 连接超时。

**修改建议**：
- 改为异步执行 + 超时保护
- 或设置 SubAgent 最大执行时间（如 60 秒）

**工作量**：3-5 天
**影响范围**：SubAgent 委派系统

---

### 2.7 孤儿任务恢复误杀长时任务

**位置**：`TaskConsumerConfig.java` L96-127

**问题**：`recoverOrphanTasks` 将 updateTime 超过 10 分钟的 RUNNING 任务视为孤儿并标记 FAILED。大文档 ingestion 可能超过 10 分钟。

**修改建议**：
- 使用分布式锁（Redis）标记任务归属，而非时间判断
- 或将超时阈值改为可配置（当前硬编码 10 分钟）

**工作量**：1-2 天
**影响范围**：异步任务系统

---

### 2.8 敏感词过滤正则每次编译 ✅ 已修复

**位置**：`SensitiveWordFilter.java` L217-224

**问题**：`containsIgnoreCase()` 和 `replaceIgnoreCase()` 每次调用都 `Pattern.compile()`。流式输出时 `processChunk` 每个 token 调用一次，50 个敏感词 × 1000 个 token = 50,000 次正则编译/消息。

**修复内容**（2026-06-24）：
- 新增 `PATTERN_CACHE`（`ConcurrentHashMap<String, Pattern>`）缓存编译后的正则
- 新增 `getPattern()` 方法，通过 `computeIfAbsent` 懒加载编译并缓存
- `containsIgnoreCase()` 和 `replaceIgnoreCase()` 改为从缓存获取 Pattern

**工作量**：已完成
**影响范围**：敏感词过滤、所有对话

---

### 2.9 消息保存无事务保护 ✅ 已修复

**位置**：`MessageMiddleware.java` L529-552

**问题**：`saveMessage` 插入消息并更新 session 统计（messageCount、lastMessageAt），但不在同一事务中。统计更新失败时 session 计数漂移。

**修复内容**（2026-06-24）：
- 在主 `saveMessage` 方法上添加 `@Transactional(rollbackFor = Exception.class)`
- 保证消息插入和 session 统计更新在同一事务中，任一失败则整体回滚

**工作量**：已完成
**影响范围**：消息保存、会话统计

---

## 三、性能问题（High）

### 3.1 多个无界线程池 ✅ 已修复

**位置**：
- `ChatServiceImpl.RAG_EXECUTOR` — `newCachedThreadPool()`
- `QueryKnowledgeTool.SEARCH_EXECUTOR` — `newCachedThreadPool()`
- `DocumentServiceImpl.INGEST_EXECUTOR` — 固定 3 线程

**问题**：无界线程池在高并发下可创建数千线程，导致 OOM 或 CPU 争抢。

**修复内容**（2026-06-24）：
- 新增 `ThreadPoolConfig` 配置类，定义共享有界线程池 `lightBotExecutor`
- corePoolSize=8, maxPoolSize=32, queueCapacity=256, CallerRunsPolicy
- `ChatServiceImpl` 的 `RAG_EXECUTOR` 替换为注入的 `lightBotExecutor`
- `QueryKnowledgeTool` 的 `SEARCH_EXECUTOR` 替换为注入的 `lightBotExecutor`
- `DocumentServiceImpl.INGEST_EXECUTOR`（固定3线程）已有界，暂不修改

**工作量**：已完成
**影响范围**：全局线程管理

---

### 3.2 ToolServiceImpl 每次解析工具都扫描全量 Bean ✅ 已修复

**位置**：`ToolServiceImpl.java` L288-319

**问题**：`getAllBuiltinToolCallbacks()` 每次调用都扫描所有 Spring Bean（`getBeansWithAnnotation(Component.class)`），用反射找 `@Tool` 方法。每个对话请求都会触发。

**修复内容**（2026-06-24）：
- `@PostConstruct` 启动时扫描一次，结果缓存到 `cachedBuiltinCallbacks`
- `getAllBuiltinToolCallbacks()` 优先返回缓存，兜底实时扫描
- 扫描逻辑提取到 `scanBuiltinToolCallbacks()` 方法

**工作量**：已完成
**影响范围**：工具解析、每次对话

---

### 3.3 ToolPrepMiddleware 每次查询 Tool 表获取 displayName ✅ 已修复

**位置**：`ToolPrepMiddleware.java` L242-256

**问题**：`buildDisplayNameMap()` 每次对话请求都查 Tool 表。工具显示名很少变更。

**修复内容**（2026-06-24）：
- 新增 `displayNameCache` + 5 分钟 TTL
- `getDisplayNameCache()` 缓存全量 toolName → displayName 映射
- `buildDisplayNameMap()` 从缓存中按需过滤，不再每次查库

**工作量**：已完成
**影响范围**：每次对话

---

### 3.4 MCP 工具加载 N+1 问题 ✅ 已修复

**位置**：`ToolPrepMiddleware.java` L187-193

**问题**：每个 MCP Server ID 串行调用 `mcpClientService.getToolCallbacks(serverId)`。5 个 MCP Server = 5 次串行网络调用。

**修复内容**（2026-06-24）：
- 使用 `CompletableFuture.allOf()` + `lightBotExecutor` 并行加载所有 MCP Server 工具
- 单个 MCP 加载失败不影响其他 Server，返回空列表并 warn 日志

**工作量**：已完成
**影响范围**：对话工具准备

---

### 3.5 EmbeddingServiceImpl 路由决策每次查库 ✅ 已修复

**位置**：`EmbeddingServiceImpl.java`

**问题**：`shouldRouteToMilvus()` 每次 RAG 搜索都查 `knowledgeService.getById()` 判断用 pgvector 还是 Milvus。

**修复内容**（2026-06-24）：
- 新增 `routingCache`（`ConcurrentHashMap<Long, Boolean>`），缓存 knowledgeId → 是否 Milvus 类型
- 知识库类型创建后不变，缓存永不失效
- Milvus 可用性（`milvusUtil.isAvailable()`）仍实时检查，不受缓存影响

**工作量**：已完成
**影响范围**：RAG 搜索

---

### 3.6 MimoChatClient 每次请求创建新 RestClient ✅ 已修复

**位置**：`MimoChatClient.java` L306-319

**问题**：`buildClient()` 每次 `streamChat()` 创建新 `RestClient`，无法复用连接池。

**修复内容**（2026-06-24）：
- 新增 `clientCache`（`ConcurrentHashMap<Long, RestClient>`），按 providerId 缓存 RestClient
- `buildClient()` 使用 `computeIfAbsent` 懒创建
- 新增 `clearClientCache(providerId)` 方法，Provider 凭证变更时可清除缓存

**工作量**：已完成
**影响范围**：Mimo 模型调用

---

### 3.7 全量导入 Ant Design（前端）✅ 已修复

**位置**：`main.js` L3

**问题**：`app.use(Antd)` 注册所有组件，无法 tree-shaking，显著增大打包体积。

**修复内容**（2026-06-24）：
- `main.js` 无 `app.use(Antd)` 全量注册
- `vite.config.js` 已配置 `Components({ resolvers: [AntDesignVueResolver({ importStyle: false })] })` 按需导入

**工作量**：已完成
**影响范围**：前端构建产物

---

## 四、数据一致性问题（Medium）

### 4.2 setDefaultAgent 竞态条件

**位置**：`AgentServiceImpl.java` L567-587

**问题**：先清除所有默认 Agent，再设置新的。两个并发请求可同时清除，导致无默认 Agent。

**修改建议**：
- 使用数据库唯一约束 + 事务
- 或用 `UPDATE agent SET is_default = CASE WHEN id = ? THEN true ELSE false END`

**工作量**：0.5 天
**影响范围**：Agent 默认设置

---

### 4.3 Knowledge 计数器无事务保证

**位置**：`Knowledge` entity（`documentCount`、`chunkCount`、`totalTokens`）

**问题**：计数器在多个 Service 方法中增量更新，无事务保护，易漂移。

**修改建议**：
- 定期用 `SELECT COUNT(*)` 校准
- 或改为查询时实时计算，不维护冗余字段

**工作量**：1-2 天
**影响范围**：知识库管理

---

### 4.4 chat_session 逻辑删除 + 消息级联清理不完整 ✅ 已修复

**位置**：`ChatSessionServiceImpl.java` L205-218

**问题**：
- 删除会话时未清理 `tool_calls` 孤儿记录
- 未清理用户附件 MinIO 文件
- 未清理 Redis 中的 Skill 激活状态（`skill:activated:{sessionId}`）
- `chat_session` 使用逻辑删除，但关联数据（message、tool_calls、llm_trace）是物理删除，不一致

**修复内容**（2026-06-24）：
- `chat_session` 已改为物理删除（`removeById`），与子表一致
- `MessageServiceImpl.deleteBySessionId` 级联清理：tool_calls + MinIO 附件（AI 图片 + 用户上传）
- `LlmTraceServiceImpl.deleteBySessionId` 级联清理调用链记录
- Redis `skill:activated:{sessionId}` 有 24 小时 TTL 自动过期，暂不主动清理

**工作量**：已完成
**影响范围**：会话删除、消息删除

---

### 4.5 前端 Loosse Equality 作为后端类型不一致的 Workaround

**位置**：`Chat.vue` L1714, L1732, L1759, L1762, L1770

**问题**：offset 值有时是 number 有时是 string，前端用 `==`（宽松比较）规避。根源在后端 SSE 事件序列化不一致。

**修改建议**：
- 后端 `ToolEventGenerator` 确保 `contentOffset` 始终为 int
- 前端改用 `===` 并在边界处做类型归一化

**工作量**：0.5 天
**影响范围**：对话流式渲染

---

## 五、代码设计问题（Medium）

### 5.1 ChatServiceImpl 流式/非流式路径大量重复

**位置**：`ChatServiceImpl.java`（1479 行）

**问题**：`processToolCallsRecursively`（流式）和 `processBlockingRound`（非流式）实现几乎相同的工具调用循环逻辑。非流式路径还只处理第一个工具调用。

**修改建议**：
- 抽取共享的工具执行逻辑为独立方法
- 统一事件发射和元数据构建
- 非流式路径补全多工具调用支持

**难点**：流式路径涉及 SSE 发射，需小心处理异步边界
**工作量**：3-5 天
**影响范围**：核心对话引擎

---

### 5.2 Model Handler 大量重复代码

**位置**：`OpenAIModelHandler.java`、`DashScopeModelHandler.java`、`MimoModelHandler.java`、`MimoChatClient.java`

**问题**：
- `toDouble()`/`toInt()` 在 4 个文件中重复
- `addExtraHeaders()`/`resolveBaseUrl()`/`resolveModelsEndpoint()` 在 3 个 Handler 中重复
- 多处 `new ObjectMapper()` 而非注入

**修改建议**：
- 提取 `AbstractModelHandler` 基类
- 共享工具方法放到 `ModelHandlerUtil`
- 统一注入 ObjectMapper

**工作量**：2-3 天
**影响范围**：模型层

---

### 5.3 RAG 引用映射逻辑重复 4 次

**位置**：`ChatServiceImpl.buildRagMetadataJson`、`buildChatMetadata`、`buildPersistMetadata`，`TraceMiddleware.buildPersistMetadata`

**问题**：将搜索结果转为 `RagReferenceVO` 的逻辑在 4 处重复。

**修改建议**：
- 抽取为 `RagMetadataBuilder` 工具类

**工作量**：0.5 天
**影响范围**：RAG 元数据构建

---

### 5.4 resolveAgentId 在 3 个工具中复制粘贴

**位置**：`QueryKnowledgeTool` L271、`KnowledgeTools` L181、`ReadSkillTool` L97

**问题**：完全相同的 `resolveAgentId(ToolContext)` 方法在 3 个工具类中重复。

**修改建议**：
- 提取到 `ToolContextUtil` 或基类

**工作量**：0.5 天
**影响范围**：工具层

---

### 5.5 ChatContext God Object

**位置**：`ChatContext.java`（40+ 字段）

**问题**：可变共享状态对象，任何中间件可修改任何字段，难以推理状态。

**修改建议**：
- 按阶段拆分：`ChatInput`（不可变）→ `ChatPrepState`（中间件产出）→ `ChatStreamState`（流式累加器）
- 减少可变字段，使用不可变对象传递

**难点**：改动范围大，涉及所有中间件
**工作量**：5-7 天
**影响范围**：整个对话管道

---

### 5.6 接口向下转型（Interface Downcast）

**位置**：`RagServiceImpl.java` L95-96、`QueryKnowledgeTool.java` L52

**问题**：注入 `EmbeddingService` 接口后强转为 `EmbeddingServiceImpl` 调用实现类特有方法。

**修改建议**：
- 将 `searchSimilarSql` 方法加入 `EmbeddingService` 接口

**工作量**：0.5 天
**影响范围**：RAG 搜索

---

### 5.7 InitMiddleware 硬编码 30+ 配置键

**位置**：`InitMiddleware.java` L145-188

**问题**：`overlayModelBehaviorConfig` 手动维护 30+ 个配置键列表。新增配置键时必须同步修改，否则静默失效。

**修改建议**：
- 用反射或注解自动收集 `ConfigKeys.Agent` 的所有字段
- 或将 overlay 逻辑改为"除明确排除的字段外全部 overlay"

**工作量**：1 天
**影响范围**：对话初始化

---

### 5.8 前端巨型组件

**位置**：
- `AgentDetail.vue`：5,423 行
- `KnowledgeDetail.vue`：3,579 行
- `WorkflowEdit.vue`：3,472 行
- `Chat.vue`：2,974 行

**问题**：单文件组件过大，难以维护和测试。

**修改建议**：
- `AgentDetail.vue`：按 Tab 拆分为独立子组件
- `Chat.vue`：抽取 `useChatStream`、`useChatHistory`、`useChatAttachments` 等 composable
- `KnowledgeDetail.vue`：按功能域拆分

**难点**：需要仔细处理 props/events 传递
**工作量**：每个组件 3-5 天
**影响范围**：前端可维护性

---

## 六、前端问题（Medium）

### 6.1 Token 存在 localStorage + v-html 使用

**位置**：`request.js` L79、`user.js` L14、`MarkdownPreview.vue`、`KnowledgeDetail.vue` L364/L578

**问题**：Token 存 localStorage 易被 XSS 窃取。`v-html` 在 8 处使用，虽 Markdown 有 DOMPurify，但 `KnowledgeDetail.vue` 直接渲染服务端 HTML。

**修改建议**：
- Token 改存 httpOnly Cookie
- 所有 `v-html` 使用 DOMPurify 过滤

**工作量**：2-3 天
**影响范围**：全局安全

---

### 6.2 225 个空 catch 块

**位置**：73 个文件中

**问题**：`catch {}` 静默吞掉错误，生产环境难以调试。

**修改建议**：
- 至少加 `console.warn`
- 关键路径加 `message.error()` 提示

**工作量**：2-3 天（逐步修复）
**影响范围**：全局错误处理

---

### 6.3 工具组件大量内联样式

**位置**：`QueryKnowledgeResult.vue`、`PgSqlQueryResult.vue`、`AskUserResult.vue`、`ImageGenResult.vue`

**问题**：数百行 `style=""` 内联样式，无法覆盖、无法响应式、膨胀 DOM。

**修改建议**：
- 提取为 scoped CSS 类

**工作量**：1-2 天
**影响范围**：工具渲染组件

---

### 6.4 SSE 解析逻辑重复

**位置**：`api/chat.js`、`api/prompt.js`

**问题**：两处独立实现 SSE 流解析，错误处理和缓冲策略不同。

**修改建议**：
- 抽取共享的 `useSSE` composable

**工作量**：1 天
**影响范围**：SSE 通信层

---

### 6.5 scrollToBottom 无防抖

**位置**：`Chat.vue`

**问题**：`onChunk`、`onToolEvent`、`onStatus` 每次回调都调 `scrollToBottom()`，快速流式时每秒数百次 DOM 读取。

**修改建议**：
- 使用 `requestAnimationFrame` 做防抖，每帧最多滚动一次

**工作量**：0.5 天
**影响范围**：对话滚动体验

---

### 6.6 工具渲染 JSON 重复解析

**位置**：`ToolCallsGroupComponent.vue`、各 ToolResult 组件

**问题**：`hasArgs()` 和 `parseArgsPreview()` 各自 `JSON.parse(evt.args)`。每个工具组件也独立 `JSON.parse(event.result)`。

**修改建议**：
- 使用 `computed` 缓存解析结果，避免重复解析
- 统一使用 `useToolResult.js` composable

**工作量**：1 天
**影响范围**：工具渲染

---

## 七、代码质量问题（Low）

### 7.1 魔法数字散布

| 位置 | 魔法值 | 含义 |
|------|--------|------|
| `GeneralChunkStrategy.java:21` | `200` | 最大重叠 token |
| `GraphExtractor.java:87` | `2000` | 内容截断长度 |
| `WorkflowExecutorService.java:539` | `400` | 截断长度 |
| `SubAgentRuntime.java:45` | `6` | 最大循环深度 |
| `PgSqlTool.java:39-40` | `50/10000` | 最大行数/内容长度 |
| `LlmNodeProcessor.java:211` | `3` | 默认历史轮次 |
| `EmbeddingServiceImpl.java:285` | `60` | RRF k 常数 |
| `GraphRetrievalUtil.java:21` | `15` | PPR 迭代次数 |

**修改建议**：抽取为可配置常量或 application.yml 配置项。

---

### 7.2 死代码

| 文件 | 问题 |
|------|------|
| `stores/workflow.js` | 整个 store 未使用 |
| `utils/theme.js` | 空文件 |
| `workflowLayout.js` L551-572 | 5 个 `@deprecated` 函数仍在导出 |
| `Chat.vue` L746 `toolEvents` ref | 写入但从未读取 |
| `OpenAiStreamUsageSupport.java` | 只调一行 `streamUsage(true)` |

---

### 7.3 命名不一致

- 文件名混用 `PascalCase`（`Chat.vue`）和 `camelCase`（`toolRegistry.js`）
- 组件名混用 `ToolCallsGroupComponent` 和 `BaseToolCall`
- API 函数名混用 `getAgents` 和 `getAgentDetail`
- `SubAgentRuntime` 中 `modelId` 实际用作 `providerId`

---

### 7.4 前端死代码/废弃设计

- `stores/task.js` 使用 `reactive()` 而非 `defineStore`，与 Pinia 不一致
- `window.dispatchEvent('session-title-updated')` 全局事件总线绕过 Vue 响应式
- 会话列表只加载 50 条，无分页/无限滚动
- SSE 重连无 jitter，服务重启时所有客户端同时重连（惊群效应）

---

## 附录：修改优先级排序

### P0（安全，立即修复）
1. ~~PgSqlTool SQL 注入 → 参数化查询~~ ✅ 已修复（2026-06-24）
2. ~~ScriptNodeProcessor 沙箱 → 危险访问拦截 + 5 秒超时~~ ✅ 已修复（2026-06-24）
3. ~~ApiNodeProcessor SSRF → URL 校验（禁止内网/元数据服务）~~ ✅ 已修复（2026-06-24）

### P1（可靠性，1-2 周内修复）
1. ~~工作流环路检测 → visited 集合~~ ✅ 已修复（2026-06-24）— DFS 环路检测，发布/校验时拦截
2. ~~LLM 节点超时 → HTTP 超时配置~~ ✅ 已修复（2026-06-24）— 120 秒超时保护
3. ~~Milvus 连接重试~~ ✅ 已修复（2026-06-24）— 60 秒冷却重连
4. ~~SEARCH_RESULTS_MAP 泄漏 → TTL 缓存~~ ✅ 已修复（2026-06-24）— 5 分钟 TTL 自动过期
5. ~~敏感词正则预编译 → Pattern 缓存~~ ✅ 已修复（2026-06-24）— ConcurrentHashMap 缓存
6. ~~消息保存事务 → @Transactional~~ ✅ 已修复（2026-06-24）— 事务保护消息插入+统计更新
7. ~~MCP Stdio 命令注入 → shell 元字符校验~~ ✅ 已修复（2026-06-24）
8. ~~无界线程池 → 有界线程池~~ ✅ 已修复（2026-06-24）— ThreadPoolConfig 统一有界线程池

### P2（性能/一致性，1 个月内）
1. 流式/非流式去重 → 抽取共享逻辑
2. Model Handler 去重 → 基类
3. ~~工具解析缓存 → 启动时缓存~~ ✅ 已修复（2026-06-24）— @PostConstruct 缓存
4. ~~chat_session 级联清理~~ ✅ 已修复（2026-06-24）— 物理删除 + tool_calls/MinIO/Trace 级联清理
5. 前端组件拆分 → composables
6. ~~displayName 缓存~~ ✅ 已修复（2026-06-24）— 5 分钟 TTL 缓存
7. ~~MCP 工具并行加载~~ ✅ 已修复（2026-06-24）— CompletableFuture.allOf()
8. ~~向量路由缓存~~ ✅ 已修复（2026-06-24）— routingCache 永久缓存
9. ~~RestClient 缓存~~ ✅ 已修复（2026-06-24）— 按 providerId 缓存
10. ~~Ant Design 按需导入~~ ✅ 已修复（2026-06-24）— 已配置按需导入

### P3（代码质量，持续改进）
1. 魔法数字常量化
2. 死代码清理
3. 空 catch 块修复
4. 内联样式提取
5. 命名统一
