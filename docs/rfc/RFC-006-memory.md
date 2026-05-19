# RFC-006: Memory System

| 字段 | 值 |
|------|------|
| RFC 编号 | 006 |
| 标题 | Memory System |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/memory |

---

## 1. 背景

AI Agent 的记忆能力决定了对话的连贯性和个性化程度。一个没有记忆的 Agent 每次对话都是从零开始，无法理解上下文、记住用户偏好、维护长期关系。

Memory 系统需要解决三个层次的问题：

1. **短期记忆** — 当前会话内的多轮对话历史
2. **长期记忆** — 跨会话的用户偏好、关键事实
3. **工作记忆** — Agent 执行过程中的中间状态（Tool 调用结果、变量）

本 RFC 定义 LightBot Memory 系统的架构与实现策略。

---

## 2. 问题定义

### 2.1 核心问题

**如何构建一个分层的 Memory 系统，使其满足：**

1. **会话连续性** — 多轮对话中 Agent 能记住之前的交互内容
2. **上下文窗口管理** — 在有限的 Context Window 内保留最有价值的信息
3. **长期记忆** — 跨会话记住用户偏好、关键事实
4. **记忆检索** — 从大量历史中检索与当前对话相关的内容
5. **记忆生命周期** — 记忆的创建、更新、衰减、遗忘
6. **多策略支持** — 滑动窗口、摘要压缩、向量检索等多种策略可配置

### 2.2 约束条件

| 约束 | 说明 |
|------|------|
| Context Window | 受模型限制（4K / 8K / 128K tokens） |
| 存储成本 | 历史消息需持久化，但不能无限增长 |
| 检索延迟 | 记忆检索延迟 < 100ms |
| 一致性 | 同一会话内消息顺序一致 |

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **会话历史持久化** | 多轮对话历史完整存储，支持查询 |
| P0 | **滑动窗口策略** | 按消息数/token 数截取最近历史 |
| P0 | **消息注入** | 历史消息自动注入 Agent Context |
| P1 | **摘要压缩** | 长对话自动摘要，压缩 Context 占用 |
| P1 | **向量检索记忆** | 从历史对话中语义检索相关内容 |
| P1 | **用户偏好记忆** | 跨会话记住用户偏好和关键事实 |
| P2 | **记忆衰减** | 老旧记忆自动降权或遗忘 |
| P2 | **记忆隔离** | 多租户记忆严格隔离 |

---

## 4. 非目标

| 非目标 | 原因 |
|--------|------|
| 人格/角色记忆 | 属于 Agent Prompt 配置，不属于 Memory |
| 知识库存储 | 知识库是结构化文档，与会话记忆不同（RFC-004） |
| 模型微调 | 不通过 Memory 实现模型个性化 |
| 跨 Agent 记忆共享 | 各 Agent 记忆独立，避免信息泄露 |

---

## 5. 核心架构

### 5.1 记忆分层模型

```
┌─────────────────────────────────────────────────────────────┐
│                    Memory System Architecture                │
├─────────────────────────────────────────────────────────────┤
│  Memory Types                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                                                     │   │
│  │   ┌─────────────┐  Working Memory (执行态)          │   │
│  │   │  Tool 结果  │  生命周期：单次执行               │   │
│  │   │  变量快照   │  存储：内存                       │   │
│  │   └─────────────┘                                   │   │
│  │                                                     │   │
│  │   ┌─────────────┐  Short-term Memory (会话级)       │   │
│  │   │  对话历史   │  生命周期：会话                   │   │
│  │   │  消息序列   │  存储：PostgreSQL + Redis         │   │
│  │   └─────────────┘                                   │   │
│  │                                                     │   │
│  │   ┌─────────────┐  Long-term Memory (用户级)        │   │
│  │   │  用户偏好   │  生命周期：持久                   │   │
│  │   │  关键事实   │  存储：PostgreSQL + pgvector      │   │
│  │   └─────────────┘                                   │   │
│  │                                                     │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  Strategy Layer                                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ Window   │ │ Summary  │ │ Vector   │ │ Fact     │     │
│  │ Strategy │ │ Strategy │ │ Retrieve │ │ Extract  │     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
├─────────────────────────────────────────────────────────────┤
│  Storage Layer                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │  PostgreSQL  │  │    Redis     │  │   pgvector   │    │
│  │  (消息持久化) │  │  (会话缓存)  │  │  (语义检索)  │    │
│  └──────────────┘  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 组件 | 职责 |
|------|------|
| `MemoryManager` | 记忆管理入口，协调短期/长期记忆 |
| `SessionMemory` | 会话级短期记忆，管理消息历史 |
| `LongTermMemory` | 用户级长期记忆，存储偏好和事实 |
| `MemoryStrategy` | 记忆策略接口（窗口/摘要/向量检索） |
| `TokenCounter` | Token 计数器，用于窗口策略 |
| `Summarizer` | 对话摘要器（调用 LLM） |
| `FactExtractor` | 事实提取器（从对话中提取关键信息） |

---

## 6. 短期记忆（Session Memory）

### 6.1 会话消息管理

```java
public class DefaultSessionMemory implements SessionMemory {

    private final MessageRepository messageRepository;
    private final RedisTemplate<String, List<Message>> redisTemplate;
    private final List<MemoryStrategy> strategies;

    @Override
    public List<Message> getHistory(String sessionId, AgentContext context) {
        // 1. 优先从 Redis 读取（热数据）
        List<Message> cached = redisTemplate.opsForValue().get("session:" + sessionId);
        if (cached != null) {
            return applyStrategies(cached, context);
        }

        // 2. 从 PostgreSQL 读取（冷数据）
        List<Message> messages = messageRepository.findBySessionIdOrderByCreatedAt(sessionId);

        // 3. 写入 Redis 缓存
        redisTemplate.opsForValue().set("session:" + sessionId, messages,
            Duration.ofHours(2));

        return applyStrategies(messages, context);
    }

    @Override
    public void append(String sessionId, Message message) {
        // 持久化
        messageRepository.save(message);
        // 更新缓存
        List<Message> cached = redisTemplate.opsForValue().get("session:" + sessionId);
        if (cached != null) {
            cached.add(message);
            redisTemplate.opsForValue().set("session:" + sessionId, cached);
        }
    }

    /**
     * 按配置的策略链处理消息历史
     */
    private List<Message> applyStrategies(List<Message> messages, AgentContext context) {
        List<Message> result = new ArrayList<>(messages);
        for (MemoryStrategy strategy : strategies) {
            if (strategy.supports(context)) {
                result = strategy.apply(result, context);
            }
        }
        return result;
    }
}
```

### 6.2 滑动窗口策略

```java
@Component
public class SlidingWindowStrategy implements MemoryStrategy {

    private final TokenCounter tokenCounter;

    @Override
    public List<Message> apply(List<Message> messages, AgentContext context) {
        MemoryConfig config = context.getAgentDefinition().getMemoryConfig();
        int maxMessages = config.getMaxMessages();       // 默认 50
        int maxTokens = config.getMaxTokens();           // 默认 8000
        boolean keepSystem = config.isKeepSystemPrompt(); // 默认 true

        List<Message> result = new ArrayList<>();

        // 1. 保留 System Prompt
        List<Message> systemMessages = messages.stream()
            .filter(m -> m.getRole() == MessageRole.SYSTEM)
            .collect(Collectors.toList());
        if (keepSystem) {
            result.addAll(systemMessages);
        }

        // 2. 从最新消息向前截取
        List<Message> nonSystem = messages.stream()
            .filter(m -> m.getRole() != MessageRole.SYSTEM)
            .collect(Collectors.toList());

        int systemTokens = systemMessages.stream()
            .mapToInt(tokenCounter::count).sum();
        int remainingTokens = maxTokens - systemTokens;
        int remainingMessages = maxMessages - systemMessages.size();

        int tokenCount = 0;
        for (int i = nonSystem.size() - 1; i >= 0; i--) {
            Message msg = nonSystem.get(i);
            int tokens = tokenCounter.count(msg);
            if (tokenCount + tokens > remainingTokens || result.size() >= maxMessages) {
                break;
            }
            result.add(systemMessages.size(), msg); // 插入到 system 之后
            tokenCount += tokens;
        }

        return result;
    }
}
```

### 6.3 摘要压缩策略

当对话历史过长时，调用 LLM 生成摘要，替换早期消息：

```java
@Component
public class SummaryCompressionStrategy implements MemoryStrategy {

    private final ChatModel chatModel;
    private final TokenCounter tokenCounter;

    @Override
    public List<Message> apply(List<Message> messages, AgentContext context) {
        MemoryConfig config = context.getAgentDefinition().getMemoryConfig();
        if (!config.isSummaryEnabled()) {
            return messages;
        }

        int totalTokens = messages.stream().mapToInt(tokenCounter::count).sum();
        if (totalTokens < config.getSummaryThresholdTokens()) {
            return messages; // 未达阈值，不压缩
        }

        // 分割：早期消息 → 摘要，近期消息 → 保留
        int splitIndex = messages.size() / 2;
        List<Message> oldMessages = messages.subList(0, splitIndex);
        List<Message> recentMessages = messages.subList(splitIndex, messages.size());

        // 调用 LLM 生成摘要
        String summary = generateSummary(oldMessages);

        // 组装：System + Summary + Recent
        List<Message> result = new ArrayList<>();
        result.addAll(messages.stream()
            .filter(m -> m.getRole() == MessageRole.SYSTEM)
            .collect(Collectors.toList()));
        result.add(Message.system("[对话摘要] " + summary));
        result.addAll(recentMessages);

        return result;
    }

    private String generateSummary(List<Message> messages) {
        String prompt = "请将以下对话历史压缩为简洁的摘要，保留关键信息：\n\n"
            + messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        ChatResponse response = chatModel.call(new Prompt(prompt));
        return response.getResult().getOutput().getContent();
    }
}
```

---

## 7. 长期记忆（Long-term Memory）

### 7.1 事实提取

从对话中自动提取用户偏好和关键事实：

```java
public class LLMFactExtractor implements FactExtractor {

    private final ChatModel chatModel;

    private static final String EXTRACTION_PROMPT = """
        从以下对话中提取用户的关键信息和偏好。
        以 JSON 数组格式输出，每条格式：{"fact": "事实描述", "category": "分类"}
        仅提取明确陈述的事实，不要推测。

        对话内容：
        {conversation}
        """;

    @Override
    public List<UserFact> extract(String userId, List<Message> conversation) {
        String conversationText = conversation.stream()
            .map(m -> m.getRole() + ": " + m.getContent())
            .collect(Collectors.joining("\n"));

        String prompt = EXTRACTION_PROMPT.replace("{conversation}", conversationText);
        ChatResponse response = chatModel.call(new Prompt(prompt));

        return parseFacts(response.getResult().getOutput().getContent());
    }
}
```

### 7.2 长期记忆存储

```java
@Component
public class DefaultLongTermMemory implements LongTermMemory {

    private final UserFactRepository factRepository;
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    @Override
    public void remember(String userId, UserFact fact) {
        // 1. 存储结构化事实
        factRepository.save(UserFactEntity.from(userId, fact));

        // 2. 存储向量（用于语义检索）
        float[] embedding = embeddingService.embed(fact.getFact());
        vectorStore.add(List.of(new DocumentChunk(
            fact.getId(), fact.getFact(), embedding,
            Map.of("userId", userId, "category", fact.getCategory())
        )));
    }

    @Override
    public List<UserFact> recall(String userId, String query, int topK) {
        // 语义检索相关事实
        float[] queryEmbedding = embeddingService.embed(query);
        List<SearchResult> results = vectorStore.search(
            queryEmbedding, "user_facts:" + userId, topK);

        return results.stream()
            .map(r -> new UserFact(r.getId(), r.getContent(), r.getMetadata().get("category")))
            .collect(Collectors.toList());
    }

    @Override
    public List<UserFact> getAllFacts(String userId) {
        return factRepository.findByUserId(userId).stream()
            .map(UserFactEntity::toFact)
            .collect(Collectors.toList());
    }
}
```

### 7.3 长期记忆表结构

```sql
CREATE TABLE user_fact (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    fact            TEXT NOT NULL,
    category        VARCHAR(64),         -- 偏好 / 身份 / 工作 / 其他
    source_session_id VARCHAR(36),       -- 来源会话
    confidence      DOUBLE DEFAULT 1.0,  -- 置信度
    access_count    INTEGER DEFAULT 0,   -- 访问次数
    last_accessed_at TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    expired_at      TIMESTAMP            -- 过期时间（可选）
);

CREATE INDEX idx_user_fact_user ON user_fact(user_id);
CREATE INDEX idx_user_fact_category ON user_fact(user_id, category);
```

---

## 8. 记忆与 Agent 集成

### 8.1 记忆注入流程

```
Agent Request
    │
    ▼
┌──────────────────────┐
│ 1. Load Session Mem  │  加载会话历史
└────────┬─────────────┘
         ▼
┌──────────────────────┐
│ 2. Apply Strategies  │  滑动窗口 / 摘要压缩
└────────┬─────────────┘
         ▼
┌──────────────────────┐
│ 3. Recall LTM        │  检索长期记忆
└────────┬─────────────┘
         ▼
┌──────────────────────┐
│ 4. Build Context     │  组装最终 Context
│    System + LTM +    │
│    History + User    │
└────────┬─────────────┘
         ▼
┌──────────────────────┐
│ 5. LLM Call          │  调用模型
└──────────────────────┘
```

### 8.2 Context 组装

```java
public class MemoryAwareContextBuilder implements ContextBuilder {

    private final SessionMemory sessionMemory;
    private final LongTermMemory longTermMemory;

    @Override
    public AgentContext build(ExecutionContext execContext) {
        AgentDefinition def = execContext.getAgentDefinition();
        String userId = execContext.getUserContext().getUserId();
        String sessionId = execContext.getSessionId();

        // 1. 加载会话历史（含策略处理）
        List<Message> history = sessionMemory.getHistory(sessionId, execContext);

        // 2. 检索长期记忆
        String query = execContext.getLatestUserMessage();
        List<UserFact> facts = longTermMemory.recall(userId, query, 5);

        // 3. 组装 System Prompt
        StringBuilder systemPrompt = new StringBuilder(def.getSystemPrompt());
        if (!facts.isEmpty()) {
            systemPrompt.append("\n\n关于用户的已知信息：\n");
            for (UserFact fact : facts) {
                systemPrompt.append("- ").append(fact.getFact()).append("\n");
            }
        }

        return AgentContext.builder()
            .agentId(def.getId())
            .sessionId(sessionId)
            .systemPrompt(systemPrompt.toString())
            .messages(history)
            .variables(execContext.getVariables())
            .build();
    }
}
```

---

## 9. Token 计数

### 9.1 计数策略

```java
public interface TokenCounter {
    int count(Message message);
    int count(String text);
}

@Component
public class DefaultTokenCounter implements TokenCounter {

    // 简化估算：1 中文字符 ≈ 2 tokens，1 英文单词 ≈ 1.3 tokens
    private static final double CHINESE_RATIO = 2.0;
    private static final double ENGLISH_RATIO = 1.3;

    @Override
    public int count(Message message) {
        return count(message.getContent())
            + (message.getToolCalls() != null ? countToolCalls(message.getToolCalls()) : 0);
    }

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) return 0;

        int chineseChars = 0;
        int englishWords = 0;
        boolean inWord = false;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
                inWord = false;
            } else if (Character.isLetter(c)) {
                if (!inWord) englishWords++;
                inWord = true;
            } else {
                inWord = false;
            }
        }

        return (int) (chineseChars * CHINESE_RATIO + englishWords * ENGLISH_RATIO);
    }
}
```

---

## 10. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| Context Window 溢出 | 高 | 滑动窗口 + token 计数双重保障 |
| 摘要丢失关键信息 | 中 | 保留近期原始消息，仅压缩早期 |
| 长期记忆提取不准确 | 中 | 置信度评分 + 人工确认机制 |
| Redis 缓存与 DB 不一致 | 中 | 写入 DB 后更新缓存，缓存设 TTL |
| 多租户记忆泄露 | 高 | 严格租户隔离，查询强制带 tenantId |
| 记忆无限增长 | 中 | 过期清理 + 访问衰减 |

---

## 11. 后续演进

| 阶段 | 能力 |
|------|------|
| v0.1 | 会话历史持久化 + 滑动窗口 |
| v0.2 | 摘要压缩 + 长期记忆 + 向量检索 |
| v0.3 | 记忆作为 Workflow 节点数据源 |
| v1.0 | 记忆衰减 + 多维度过滤 + 记忆分析面板 |

---

## 附录：配置项

```yaml
lightbot:
  memory:
    session:
      # 最大消息数
      max-messages: 50
      # 最大 token 数
      max-tokens: 8000
      # 是否保留 System Prompt
      keep-system-prompt: true
      # Redis 缓存 TTL
      cache-ttl-hours: 2
    summary:
      # 是否启用摘要压缩
      enabled: false
      # 触发摘要的 token 阈值
      threshold-tokens: 6000
      # 摘要使用的模型
      model: gpt-4o-mini
    long-term:
      # 是否启用长期记忆
      enabled: false
      # 事实提取模型
      extraction-model: gpt-4o-mini
      # 检索 Top-K
      recall-top-k: 5
      # 记忆过期天数（0=永不过期）
      expiry-days: 0
```
