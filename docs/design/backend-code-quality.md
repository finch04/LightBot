# LightBot 后端代码质量改进方案

> 日期：2026-06-21
> 范围：lightbot-server/src/main/java/com/lightbot 全部 Java 文件
> 目标：消除重复逻辑、解耦业务、统一异常处理、规范常量管理

---

## 一、需求分析

### 1.1 项目概况

| 维度 | 现状 |
|------|------|
| 技术栈 | Spring Boot 3 + Spring AI + MyBatis-Plus + PostgreSQL + Redis + MinIO + Sa-Token |
| 代码规模 | 约 200+ Java 文件，核心业务代码约 15,000+ 行 |
| 分层架构 | Controller → Service → Mapper，对话链路引入中间件管道模式 |

### 1.2 问题分类与严重程度

| 严重程度 | 问题类型 | 影响 |
|---------|---------|------|
| **P0** | ChatServiceImpl 上帝类（1127 行） | 流式/非流式路径重复 600+ 行，维护困难 |
| **P0** | 36 处 `new ObjectMapper()` | 内存浪费，配置不一致 |
| **P0** | `buildSpan` 方法 3 处重复 | 修改需同步三处，易遗漏 |
| **P1** | `parseIds` / `resolveProviderId` / RAG 参数解析重复 | 逻辑不一致风险 |
| **P1** | KnowledgeController 40+ 接口 | 单文件 547 行，职责过重 |
| **P1** | `StpUtil.getLoginIdAsLong()` 48 处直接调用 | 鉴权框架渗透到业务层 |
| **P2** | 魔法字符串（"新对话"、工具状态前缀判断） | 可读性差，修改易遗漏 |
| **P2** | ErrorCode 重复码 30001 | 前端无法区分不同错误 |
| **P2** | Controller 注入 Util 类 | 违反分层原则 |
| **P2** | EnumController 使用反射 | 硬编码字段名，静默降级 |

---

## 二、技术设计

### 2.1 P0：ChatServiceImpl 上帝类拆分

#### 2.1.1 现状

`ChatServiceImpl.java` 1127 行，承担过多职责：
- 流式对话递归工具调用循环 (`processToolCallsRecursively`)
- 非流式对话处理 (`processBlockingRound`)
- MiMo 直连流式处理 (`streamMimoDirect`)
- RAG 检索逻辑 (`getRagReferences`, `getRagSearchResults`)
- 工具调用事件构建 (`appendToolCallStart`, `appendToolCallResult`)
- 参数解析 (`resolveMaxExecutionSteps`, `resolveModelRetryTimes`)
- Token 统计 (`accumulateStreamUsage`)
- thinking 标签过滤 (`stripThinkingTags`)

**关键问题**：`processToolCallsRecursively` 和 `processBlockingRound` 之间存在大量重复逻辑（工具执行、事件构建、RAG 结果收集），总计超过 600 行几乎相同的代码。

#### 2.1.2 改进方案

```
ChatServiceImpl (拆分后)
├── ToolCallExecutor          — 工具执行、事件构建、记录
├── RagResultCollector        — RAG 引用收集
├── StreamChatStrategy        — 流式对话策略
└── BlockingChatStrategy      — 非流式对话策略
```

**Step 1：提取 ToolCallExecutor**

```java
@Component
@RequiredArgsConstructor
public class ToolCallExecutor {

    private final ToolService toolService;
    private final ToolCallMapper toolCallMapper;

    /**
     * 执行工具调用并返回结果
     *
     * @param tcName    工具名
     * @param tcArgs    工具参数
     * @param sessionId 会话ID
     * @return 执行结果字符串
     */
    public ToolExecutionResult execute(String tcName, String tcArgs, Long sessionId) {
        // 1. 执行工具
        String result = toolService.executeByName(tcName, tcArgs, sessionId);

        // 2. 构建工具调用记录
        ToolCall toolCallLog = new ToolCall();
        toolCallLog.setToolName(tcName);
        toolCallLog.setToolInput(tcArgs);
        toolCallLog.setToolOutput(result);
        toolCallLog.setStatus(isError(result) ? "error" : "success");
        toolCallLog.setErrorMessage(isError(result) ? result : null);

        // 3. 保存记录
        toolCallMapper.insert(toolCallLog);

        return new ToolExecutionResult(result, toolCallLog);
    }

    private boolean isError(String result) {
        return result.startsWith(ToolResultPrefixes.FAILURE)
            || result.startsWith(ToolResultPrefixes.NOT_FOUND);
    }
}
```

**Step 2：提取 RagResultCollector**

```java
@Component
public class RagResultCollector {

    /**
     * 从工具执行结果中提取 RAG 引用
     */
    public void collect(List<RagReference> references, String resultType, String content, String source) {
        if ("chunk".equals(resultType) || "qa_pair".equals(resultType)) {
            references.add(new RagReference(resultType, content, source));
        }
    }
}
```

**Step 3：统一工具执行状态常量**

```java
public final class ToolResultPrefixes {
    public static final String FAILURE = "工具执行失败";
    public static final String NOT_FOUND = "工具不存在";
    private ToolResultPrefixes() {}
}
```

#### 2.1.3 预估工作量：3-4 人天

### 2.2 P0：统一 ObjectMapper

#### 2.2.1 现状

全项目 36 处 `new ObjectMapper()`，分散在中间件、工具、服务中。每个实例化都创建新对象，且未共享配置（如 JavaTimeModule）。

#### 2.2.2 改进方案

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
```

各处通过构造注入使用 `ObjectMapper`，删除所有 `new ObjectMapper()`。

#### 2.2.3 预估工作量：0.5 人天

### 2.3 P0：buildSpan 方法去重

#### 2.3.1 现状

`LlmTraceSpan` 的构建方法 `buildSpan()` 在 `InitMiddleware`、`WorkflowMiddleware`、`TraceMiddleware` 三个文件中完全重复实现。

#### 2.3.2 改进方案

提取到 `LlmTraceSpan` 实体类本身作为静态工厂方法：

```java
@Data
@Schema(description = "LLM Trace Span")
public class LlmTraceSpan {

    // ... 字段 ...

    /**
     * 从 ChatContext 构建 LlmTraceSpan
     */
    public static LlmTraceSpan fromContext(ChatContext ctx, String model, String provider) {
        LlmTraceSpan span = new LlmTraceSpan();
        span.setSessionId(ctx.getSessionId());
        span.setAgentId(ctx.getAgentId());
        span.setModel(model);
        span.setProvider(provider);
        span.setStartTime(System.currentTimeMillis());
        // ... 其他字段
        return span;
    }
}
```

#### 2.3.3 预估工作量：0.5 小时

### 2.4 P1：重复逻辑统一

#### 2.4.1 parseIds 去重

当前 `ToolPrepMiddleware` 和 `SkillPrepMiddleware` 各有一份完全相同的 `parseIds` 实现。

```java
// 提取到 util/JsonIdParser.java
public final class JsonIdParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static List<Long> parseIds(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
```

#### 2.4.2 resolveProviderId 统一

当前在 `AgentServiceImpl`、`RagServiceImpl`、`InitMiddleware` 三处独立实现，逻辑略有差异。

```java
// 提取到 model/ProviderResolver.java
@Component
@RequiredArgsConstructor
public class ProviderResolver {

    private final ModelFactory modelFactory;
    private final SystemConfigService systemConfigService;

    /**
     * 解析提供商 ID，优先级：指定值 > Agent 配置 > 系统默认 > 第一个可用
     */
    public Long resolve(Long explicitProviderId, Long agentId) {
        // 1. 显式指定
        if (explicitProviderId != null) return explicitProviderId;

        // 2. Agent 配置
        if (agentId != null) {
            Long fromAgent = getProviderFromAgentConfig(agentId);
            if (fromAgent != null) return fromAgent;
        }

        // 3. 系统默认
        Long fromSystem = systemConfigService.getDefaultProviderId();
        if (fromSystem != null) return fromSystem;

        // 4. 第一个可用
        List<Long> available = modelFactory.getAvailableProviderIds();
        return available.isEmpty() ? null : available.get(0);
    }
}
```

#### 2.4.3 RAG 参数解析统一

当前 `RagServiceImpl` 和 `ChatServiceImpl` 各自实现 RAG 参数解析，逻辑不一致。

```java
// 提取到 rag/RagParamResolver.java
@Component
public class RagParamResolver {

    /**
     * 解析 RAG 参数，优先级：overrides > queryParams > config > 默认值
     */
    public int resolveTopK(Map<String, Object> overrides, Map<String, Object> queryParams,
                           String configJson, int defaultValue) {
        // 1. overrides
        if (overrides != null && overrides.containsKey("topK")) {
            return ((Number) overrides.get("topK")).intValue();
        }
        // 2. queryParams
        if (queryParams != null && queryParams.containsKey("topK")) {
            return ((Number) queryParams.get("topK")).intValue();
        }
        // 3. config
        if (configJson != null) {
            try {
                Map<String, Object> config = MAPPER.readValue(configJson, MAP_TYPE);
                if (config.containsKey("topK")) {
                    return ((Number) config.get("topK")).intValue();
                }
            } catch (Exception ignored) {}
        }
        // 4. 默认值
        return defaultValue;
    }
}
```

#### 2.4.4 预估工作量：2 人天

### 2.5 P1：KnowledgeController 拆分

#### 2.5.1 现状

`KnowledgeController` 547 行，包含 40+ 个接口：知识库 CRUD、成员管理、文档管理、分块查看、思维导图、知识图谱、RAG 问答、问答对管理。

#### 2.5.2 改进方案

按功能域拆分：

```
KnowledgeController          — 知识库 CRUD + 成员管理（保留）
KnowledgeDocController       — 文档管理（13 个接口）
KnowledgeGraphController     — 知识图谱（10 个接口）
KnowledgeRagController       — RAG 问答（3 个接口）
KnowledgeQAPairController    — 问答对管理（7 个接口）
```

#### 2.5.3 预估工作量：1-2 人天

### 2.6 P1：StpUtil 调用封装

#### 2.6.1 现状

17 个文件、48 处直接调用 `StpUtil.getLoginIdAsLong()`，Sa-Token 鉴权细节渗透到 Controller 层。

#### 2.6.2 改进方案

```java
// util/SecurityUtil.java
public final class SecurityUtil {

    /**
     * 获取当前登录用户 ID
     */
    public static Long getCurrentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 获取当前登录用户 ID（无登录时返回 null）
     */
    public static Long getCurrentUserIdOrNull() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            return null;
        }
    }
}
```

全项目搜索替换 `StpUtil.getLoginIdAsLong()` → `SecurityUtil.getCurrentUserId()`。

#### 2.6.3 预估工作量：1 人天

### 2.7 P2：魔法字符串治理

#### 2.7.1 问题清单

| 魔法值 | 位置 | 改进方案 |
|--------|------|---------|
| `"admin123"` | AdminUserInitializer L48 | 配置文件注入 `@Value("${admin.default-password}")` |
| `"新对话"` | ChatSessionServiceImpl L122, TraceMiddleware L234 | 常量 `ChatSession.DEFAULT_TITLE` |
| `result.startsWith("工具执行失败")` | ChatServiceImpl L330, L392 | 常量 `ToolResultPrefixes.FAILURE` |
| `"qa_pair"` / `"chunk"` | ChatServiceImpl, RagServiceImpl | 枚举 `RagResultType` |
| `"pending"` / `"running"` | TaskEventController L67-68 | `TaskStatus.PENDING.getCode()` |

#### 2.7.2 预估工作量：1 人天

### 2.8 P2：ErrorCode 重复码修复

`SESSION_NOT_FOUND` (30001) 和 `AI_NO_PROVIDER` (30001) 使用了相同的 code。

修正 `AI_NO_PROVIDER` 为 30002。

#### 预估工作量：10 分钟

### 2.9 P2：Controller 注入 Util 下沉

`KnowledgeController` 注入了 `MilvusUtil` 和 `MinioUtil`，违反分层原则。

将相关调用下沉到 `KnowledgeService` 或新建 `KnowledgeStorageService`。

#### 预估工作量：0.5 人天

### 2.10 P2：EnumController 反射优化

当前使用反射 `getDeclaredField("code")` 硬编码字段名。

```java
// 定义统一接口
public interface EnumDisplay {
    String getCode();
    String getDesc();
}

// 枚举实现
public enum CommonStatus implements EnumDisplay {
    ENABLED("enabled", "启用"),
    DISABLED("disabled", "禁用");

    private final String code;
    private final String desc;
    // ...
}

// EnumController 改造
private EnumVO toEnumVO(EnumDisplay e) {
    return new EnumVO(e.getCode(), e.getDesc());
}
```

#### 预估工作量：0.5 人天

---

## 三、设计模式分析

### 3.1 已采用的良好模式（保持）

| 模式 | 应用 | 说明 |
|------|------|------|
| 中间件管道 | ChatMiddleware + ChatMiddlewareChain | 洋葱模型，关注点分离清晰 |
| TaskExecutor 策略 | TaskType 枚举关联 beanName | 运行时按 beanName 路由执行器 |
| ModelFactory 工厂 | 统一管理不同提供商的 ChatModel | 创建和缓存统一管理 |
| ErrorCode 枚举 | 所有业务错误信息集中管理 | 支持 HTTP 状态码映射 |
| ConfigKeys 常量 | JSONB 字段 key 统一管理 | 避免魔法值 |

### 3.2 建议引入的模式

| 模式 | 应用场景 | 说明 |
|------|---------|------|
| 策略模式 | 工具调用（串行/并行） | 提取 `ToolExecutionStrategy`，`SerialToolExecutor` / `ParallelToolExecutor` |
| 模板方法 | RAG 参数解析 | `resolveTopK` / `resolveThreshold` 等方法模式一致，可提取通用模板 |
| 静态工厂 | LlmTraceSpan 构建 | `LlmTraceSpan.fromContext()` 替代 3 处重复的 `buildSpan()` |

---

## 四、难点与风险

### 4.1 ChatServiceImpl 拆分风险

`processToolCallsRecursively` 是对话链路核心，涉及流式响应、工具调用、敏感词过滤、RAG 引用收集等多条线索交织。拆分时需确保：
- Flux 链的时序和错误传播不受影响
- 工具调用事件的 SSE 推送顺序不变
- RAG 引用收集在流式和非流式路径都正常工作

**建议**：在有完善集成测试的基础上进行，先提取无状态的工具执行逻辑，再处理流式路径。

### 4.2 resolveProviderId 统一

当前各处的 fallback 逻辑略有差异：
- `AgentServiceImpl` 使用 `systemConfigService.getDefaultAiConfig()`
- `InitMiddleware` 使用 `modelFactory.getAvailableProviderIds().get(0)`
- `RagServiceImpl` 有更复杂的 fallback 链

**建议**：统一前先确认优先级策略，写成文档，再实现。

### 4.3 SSE 连接管理

`TaskEventController` 和 `LogController` 使用 `static Map` 管理 SSE 连接。单实例下正常，多实例部署需引入 Redis Pub/Sub 或 WebSocket。

**建议**：当前阶段保持现状，多实例部署时再改造。

---

## 五、工作量估算与实施结果

| 序号 | 改进项 | 严重程度 | 预估工时 | 状态 | 实际改动 |
|------|--------|---------|---------|------|---------|
| 1 | 统一 ObjectMapper Bean | P0 | 0.5 天 | ✅ 已完成 | 新建 `JacksonConfig`，替换 32 处 `new ObjectMapper()`，`ToolArgsSanitizer`/`ToolEventGenerator` 改为 `@Component` 注入 |
| 2 | buildSpan 静态工厂方法 | P0 | 0.5 小时 | ✅ 已完成 | `LlmTraceSpan.of()` 替代 3 处重复 `buildSpan`，涉及 InitMiddleware/WorkflowMiddleware/TraceMiddleware/ChatServiceImpl 共 17 个调用点 |
| 3 | parseIds 去重 → JsonIdParser | P1 | 0.5 小时 | ✅ 已完成 | 新建 `util/JsonIdParser.java`，替换 ToolPrepMiddleware/SkillPrepMiddleware 中的重复实现 |
| 4 | resolveProviderId 统一 → ProviderResolver | P1 | 1 天 | ✅ 已完成 | 新建 `model/ProviderResolver.java`，替换 AgentServiceImpl/RagServiceImpl/InitMiddleware/TraceMiddleware/MessageMiddleware/SubAgentRuntime 中的 6 处实现 |
| 5 | RAG 参数解析统一 → RagParamResolver | P1 | 0.5 天 | ✅ 已完成 | 新建 `util/RagParamResolver.java`，替换 RagServiceImpl（5 个方法）和 ChatServiceImpl（2 个方法）中的重复逻辑 |
| 6 | KnowledgeController 按功能域拆分 | P1 | 1-2 天 | ✅ 已完成 | 拆分为 5 个 Controller：KnowledgeController(193行)、KnowledgeDocController(175行)、KnowledgeGraphController(118行)、KnowledgeQAPairController(90行)、KnowledgeRagController(75行) |
| 7 | 魔法字符串治理 | P2 | 1 天 | ✅ 已完成 | 新建 `ToolResultPrefixes`、`RagResultType` 常量类；`ChatSession.DEFAULT_TITLE` 替换 "新对话"；`TaskStatus` 枚举替换 TaskEventController 中硬编码状态 |
| 8 | ErrorCode 重复码 30001 修正 | P2 | 10 分钟 | ✅ 已完成 | `SESSION_NOT_FOUND` code 从 30001 改为 30010（30003 已被占用） |
| 9 | Controller 注入 Util 下沉 | P2 | 0.5 天 | ✅ 已完成 | `MilvusUtil` 从 KnowledgeController 下沉到 KnowledgeService.isMilvusAvailable() |
| 10 | EnumController 反射优化 → EnumDisplay 接口 | P2 | 0.5 天 | ✅ 已完成 | 新建 `EnumDisplay` 接口，17 个枚举实现该接口，EnumController 移除反射逻辑 |
| 11 | ChatServiceImpl MiMo 逻辑内聚 | P0 | 0.5 天 | ✅ 已完成 | `streamMimoDirect` 移除重复的 reasoning_content 解析（MimoChatClient 已处理），仅保留通用关注点（敏感词过滤、回复累积、日志），MiMo 特有逻辑内聚在 MimoChatClient 中 |

**总预估：约 11-14 人天**

**已完成：11 项**（全部完成，编译通过）

**未纳入本期**：
- ChatServiceImpl 上帝类完整拆分：当前仅完成 MiMo 逻辑内聚，完整拆分（ToolCallExecutor/RagResultCollector）需更细致设计，避免破坏中间件管道整体性
- StpUtil 封装：优先级较低，后续迭代处理
- Agent 绑定关系重构：JSONB 方案合理，保持现状

---

*文档更新时间: 2026-06-21*
