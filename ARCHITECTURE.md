# LightBot Architecture

轻量级现代化 Java AI Agent 平台架构设计文档。

---

## 1. 系统整体架构

```mermaid
graph TB
    subgraph Client["客户端层"]
        WebUI["Vue3 Web UI"]
        APIClient["API Client / SDK"]
    end

    subgraph Gateway["网关层"]
        APIGateway["API Gateway<br/>路由 / 鉴权 / 限流"]
    end

    subgraph Core["核心引擎层"]
        AgentRuntime["Agent Runtime"]
        WorkflowEngine["Workflow Engine"]
        ToolRuntime["Tool Runtime"]
        MCPBridge["MCP Bridge"]
        StreamingEngine["Streaming Engine"]
    end

    subgraph Capability["能力层"]
        ModelMgmt["Model Management"]
        KnowledgeBase["Knowledge Base"]
        KnowledgeGraph["Knowledge Graph"]
        SkillSystem["Skill System"]
        PromptMgmt["Prompt Management"]
    end

    subgraph Infra["基础设施层"]
        PostgreSQL[("PostgreSQL<br/>+ pgvector")]
        Redis[("Redis")]
        OSS["Object Storage"]
        EventBus["Event Bus"]
    end

    subgraph External["外部接入"]
        OpenAI["OpenAI / 通义千问"]
        MCP["MCP Servers"]
        ThirdPartyTools["Third-party Tools"]
    end

    Client --> Gateway
    Gateway --> Core
    Core --> Capability
    Capability --> Infra
    Core --> External
```

### 架构分层说明

| 层级 | 职责 | 关键设计原则 |
|------|------|-------------|
| **客户端层** | 用户交互、可视化编排 | 前后端分离，SSE 流式渲染 |
| **网关层** | 统一入口、鉴权限流 | API Key 鉴权、多租户隔离 |
| **核心引擎层** | Agent/Workflow/Tool 执行 | 事件驱动、可插拔、有状态 |
| **能力层** | 模型、知识、技能管理 | 能力注册发现、热加载 |
| **基础设施层** | 存储、缓存、消息 | 统一抽象、可替换 |

---

## 2. 模块划分

```mermaid
graph LR
    subgraph LightBot["LightBot 模块结构"]
        direction TB
        M1["lightbot-common"]
        M2["lightbot-core"]
        M3["lightbot-agent"]
        M4["lightbot-workflow"]
        M5["lightbot-tool"]
        M6["lightbot-mcp"]
        M7["lightbot-knowledge"]
        M8["lightbot-model"]
        M9["lightbot-skill"]
        M10["lightbot-gateway"]
        M11["lightbot-server"]
        M12["lightbot-dashboard"]
    end

    M11 --> M10
    M10 --> M3
    M10 --> M4
    M3 --> M5
    M3 --> M6
    M3 --> M7
    M3 --> M8
    M4 --> M5
    M4 --> M8
    M7 --> M8
    M9 --> M3
```

---

## 3. 模块职责

### 3.1 lightbot-common

通用基础层，零业务依赖。

| 组件 | 职责 |
|------|------|
| `common-core` | 通用工具类、Result 封装、异常体系 |
| `common-mybatis` | MyBatis-Plus 配置、分页、审计字段填充 |
| `common-redis` | Redis 工具、分布式锁、缓存注解 |
| `common-web` | 统一响应、全局异常处理、线程池配置 |
| `common-ai` | SpringAI 扩展、消息协议、Token 计算 |

### 3.2 lightbot-core

核心抽象层，定义 SPI 和领域模型。

| 组件 | 职责 |
|------|------|
| `core-spi` | Tool/Model/Knowledge 统一接口 |
| `core-model` | 领域模型：Agent、Workflow、Tool、Message |
| `core-event` | 事件定义：AgentEvent、ToolEvent、WorkflowEvent |
| `core-protocol` | 消息协议：ChatMessage、ToolCall、StreamingChunk |

### 3.3 lightbot-agent

Agent 运行时。

| 组件 | 职责 |
|------|------|
| `agent-runtime` | Agent 实例化、上下文管理、对话循环 |
| `agent-template` | Agent 模板系统、变量注入 |
| `agent-memory` | 会话记忆（短期/长期）、上下文窗口管理 |

### 3.4 lightbot-workflow

Workflow 引擎。

| 组件 | 职责 |
|------|------|
| `workflow-engine` | DAG 解析、拓扑排序、并发执行 |
| `workflow-node` | 节点类型：LLM / Tool / Condition / Variable |
| `workflow-runtime` | 运行时状态机、断点续跑、节点追踪 |

### 3.5 lightbot-tool

Tool 运行时。

| 组件 | 职责 |
|------|------|
| `tool-spi` | Tool 接口定义（@Tool 注解） |
| `tool-builtin` | 内置工具：HTTP、时间、代码执行 |
| `tool-registry` | Tool 注册中心、热加载 |
| `tool-sandbox` | 沙盒执行环境（可选） |

### 3.6 lightbot-mcp

MCP 协议桥接。

| 组件 | 职责 |
|------|------|
| `mcp-client` | MCP Client 实现、Server 连接管理 |
| `mcp-protocol` | JSON-RPC 消息编解码 |
| `mcp-tool-bridge` | MCP Tool 到内部 Tool 的适配 |

### 3.7 lightbot-knowledge

知识管理。

| 组件 | 职责 |
|------|------|
| `knowledge-base` | 知识库 CRUD、文档管理 |
| `knowledge-embed` | 文档解析、分块、向量化 |
| `knowledge-retrieval` | 向量检索、混合检索、重排序 |
| `knowledge-graph` | 知识图谱构建、实体抽取、关系推理 |

### 3.8 lightbot-model

模型管理。

| 组件 | 职责 |
|------|------|
| `model-provider` | 模型供应商适配（OpenAI / 通义千问 / Ollama） |
| `model-router` | 模型路由、负载均衡、降级策略 |
| `model-usage` | Token 计量、费用统计 |

### 3.9 lightbot-skill

Skill 系统。

| 组件 | 职责 |
|------|------|
| `skill-spi` | Skill 接口定义 |
| `skill-registry` | Skill 注册、发现、版本管理 |
| `skill-builtin` | 内置 Skill（总结、翻译、代码生成） |

### 3.10 lightbot-gateway

API 网关。

| 组件 | 职责 |
|------|------|
| `gateway-auth` | API Key 鉴权、JWT、多租户 |
| `gateway-route` | 路由转发、协议适配 |
| `gateway-limit` | 限流、熔断、降级 |

### 3.11 lightbot-server

应用启动模块。

| 组件 | 职责 |
|------|------|
| `server-app` | SpringBoot 启动类、配置 |
| `server-api` | REST API Controller 层 |
| `server-config` | 全局配置、Bean 装配 |

### 3.12 lightbot-dashboard

管理后台（Vue3）。

| 组件 | 职责 |
|------|------|
| `Agent 管理` | Agent 创建、编辑、发布 |
| `Workflow 编辑器` | Vue Flow 画布、节点拖拽 |
| `知识库管理` | 文档上传、检索测试 |
| `模型管理` | 供应商配置、模型列表 |
| `监控面板` | 调用量、Token 消耗、异常统计 |

---

## 4. 模块依赖

```mermaid
graph TD
    common["lightbot-common"]
    core["lightbot-core"]
    agent["lightbot-agent"]
    workflow["lightbot-workflow"]
    tool["lightbot-tool"]
    mcp["lightbot-mcp"]
    knowledge["lightbot-knowledge"]
    model["lightbot-model"]
    skill["lightbot-skill"]
    gateway["lightbot-gateway"]
    server["lightbot-server"]

    core --> common
    model --> core
    tool --> core
    knowledge --> core
    mcp --> core
    agent --> core
    agent --> tool
    agent --> mcp
    agent --> knowledge
    agent --> model
    agent --> skill
    workflow --> core
    workflow --> tool
    workflow --> model
    skill --> agent
    gateway --> agent
    gateway --> workflow
    server --> gateway
```

### 依赖规则

1. **单向依赖**：上层依赖下层，禁止反向引用
2. **SPI 解耦**：跨层调用通过 SPI 接口，不直接引用实现
3. **common 零依赖**：common 模块不依赖任何业务模块
4. **core 仅依赖 common**：core 模块只定义抽象，不含实现

---

## 5. 分层架构

```mermaid
graph TB
    subgraph API["API 层"]
        Controller["REST Controller"]
        WebSocket["SSE / WebSocket"]
    end

    subgraph App["应用层"]
        AppService["Application Service<br/>流程编排"]
    end

    subgraph Domain["领域层"]
        AgentAgg["Agent 聚合根"]
        WorkflowAgg["Workflow 聚合根"]
        ToolAgg["Tool 聚合根"]
        KnowledgeAgg["Knowledge 聚合根"]
    end

    subgraph InfraLayer["基础设施层"]
        Repository["Repository"]
        ModelProvider["Model Provider"]
        VectorStore["Vector Store"]
        CacheClient["Cache Client"]
    end

    API --> App
    App --> Domain
    Domain --> InfraLayer
```

### 各层职责

| 层级 | 职责 | 技术实现 |
|------|------|---------|
| **API 层** | 请求接收、参数校验、响应封装 | Spring MVC + SSE Emitter |
| **应用层** | 用例编排、事务边界、事件发布 | Application Service |
| **领域层** | 业务规则、状态管理、领域事件 | 充血模型 + Domain Event |
| **基础设施层** | 持久化、外部调用、缓存 | MyBatis-Plus + SpringAI |

### 关键设计

- **Agent 为聚合根**：Tool、Memory、Config 作为 Agent 内部实体
- **Workflow 为聚合根**：Node、Edge、Variable 作为 Workflow 内部实体
- **领域事件驱动**：Agent 通过事件与 Workflow/Tool 解耦

---

## 6. Workflow Engine

### 6.1 整体架构

```mermaid
graph TB
    subgraph Definition["定义阶段"]
        DSL["Workflow DSL<br/>JSON / YAML"]
        Parser["DSL Parser"]
        DAGBuilder["DAG Builder"]
    end

    subgraph Validation["校验阶段"]
        CycleCheck["环检测"]
        TypeCheck["类型校验"]
        VarCheck["变量校验"]
    end

    subgraph Runtime["运行时"]
        Scheduler["节点调度器"]
        Executor["节点执行器"]
        Context["运行上下文"]
        StateMachine["状态机"]
    end

    subgraph Nodes["节点类型"]
        LLMNode["LLM Node"]
        ToolNode["Tool Node"]
        ConditionNode["Condition Node"]
        VarNode["Variable Node"]
        CodeNode["Code Node"]
        SubflowNode["Sub-workflow Node"]
    end

    DSL --> Parser --> DAGBuilder
    DAGBuilder --> CycleCheck --> TypeCheck --> VarCheck
    VarCheck --> Scheduler
    Scheduler --> Executor
    Executor --> Nodes
    Context --> Executor
    StateMachine --> Scheduler
```

### 6.2 执行流程

```mermaid
sequenceDiagram
    participant Client
    participant Engine as Workflow Engine
    participant DAG as DAG Scheduler
    participant Node as Node Executor
    participant LLM as Model Provider

    Client->>Engine: execute(workflowId, inputs)
    Engine->>Engine: 加载 Workflow 定义
    Engine->>DAG: 构建 DAG + 拓扑排序
    Engine->>Engine: 初始化运行上下文

    loop 按拓扑序执行
        DAG->>Node: 调度就绪节点
        Node->>LLM: (LLM 节点) 调用模型
        LLM-->>Node: 流式返回
        Node->>Node: 更新节点状态
        Node-->>DAG: 节点完成，触发下游
    end

    DAG-->>Engine: 所有节点完成
    Engine-->>Client: 返回最终输出
```

### 6.3 节点协议

```java
public interface WorkflowNode {
    /** 节点类型标识 */
    String getType();

    /** 执行节点逻辑 */
    Mono<NodeResult> execute(NodeContext context);

    /** 校验节点配置 */
    ValidationResult validate(NodeConfig config);
}
```

### 6.4 状态机

```mermaid
stateDiagram-v2
    [*] --> Created
    Created --> Running: execute()
    Running --> NodeRunning: nextNode()
    NodeRunning --> Running: nodeComplete()
    NodeRunning --> Failed: nodeError()
    NodeRunning --> Cancelled: cancel()
    Running --> Completed: allDone()
    Running --> Failed: error()
    Failed --> Running: retry()
    Completed --> [*]
    Cancelled --> [*]
    Failed --> [*]
```

### 6.5 数据传递

| 传递方式 | 场景 | 说明 |
|----------|------|------|
| **变量引用** | `${nodeA.output.field}` | 节点间数据引用 |
| **全局变量** | `${vars.myVar}` | Workflow 级变量 |
| **运行时上下文** | `context.getInput()` | 节点入参 |
| **事件传递** | `NodeOutputEvent` | 异步事件通知 |

---

## 7. Agent Runtime

### 7.1 运行架构

```mermaid
graph TB
    subgraph AgentLoop["Agent 执行循环"]
        direction TB
        Receive["接收用户消息"]
        ContextBuild["构建上下文<br/>System + History + User"]
        ModelCall["调用 LLM"]
        Decision{"Tool Call?"}

        subgraph ToolExecution["Tool 执行"]
            ToolParse["解析 Tool Call"]
            ToolInvoke["调用 Tool"]
            ToolResult["组装 Tool Result"]
        end

        LoopBack["拼接结果，继续循环"]
        StreamOutput["流式输出最终回答"]
    end

    Receive --> ContextBuild --> ModelCall --> Decision
    Decision -->|Yes| ToolParse --> ToolInvoke --> ToolResult --> LoopBack --> ModelCall
    Decision -->|No| StreamOutput
```

### 7.2 Agent 配置模型

```java
public class AgentConfig {
    private String id;
    private String name;
    private String systemPrompt;        // 系统提示词
    private String modelId;             // 绑定模型
    private List<String> toolIds;       // 可用工具列表
    private List<String> knowledgeIds;  // 关联知识库
    private MemoryConfig memory;        // 记忆配置
    private Map<String, Object> variables; // 模板变量
    private AgentMetadata metadata;     // 元信息
}
```

### 7.3 上下文窗口管理

```mermaid
graph LR
    subgraph Context["上下文组成"]
        SP["System Prompt<br/>(固定)"]
        KB["Knowledge Context<br/>(动态注入)"]
        History["History Messages<br/>(滑动窗口)"]
        Current["Current Input<br/>(用户消息)"]
    end

    SP --> TokenCalc["Token 计算"]
    KB --> TokenCalc
    History --> TokenCalc
    Current --> TokenCalc

    TokenCalc --> Check{"超过窗口?"}
    Check -->|Yes| Compress["压缩策略<br/>摘要 / 截断 / 滑动"]
    Check -->|No| Send["发送请求"]
    Compress --> Send
```

### 7.4 记忆系统

| 类型 | 存储 | 生命周期 | 用途 |
|------|------|---------|------|
| **短期记忆** | Redis | 单次会话 | 当前对话上下文 |
| **长期记忆** | PostgreSQL | 持久化 | 用户偏好、历史摘要 |
| **工作记忆** | 内存 | 单次执行 | Agent 运行时临时状态 |

---

## 8. Tool Runtime

### 8.1 Tool 生命周期

```mermaid
graph LR
    Define["定义 Tool"] --> Register["注册到 Registry"]
    Register --> Discover["Agent 发现 Tool"]
    Discover --> Bind["绑定到 Agent"]
    Bind --> Invoke["运行时调用"]
    Invoke --> Result["返回结果"]
    Result --> Log["日志记录"]
```

### 8.2 Tool 注册方式

```mermaid
graph TB
    subgraph Registration["注册方式"]
        Anno["@Tool 注解<br/>声明式注册"]
        Code["编程式注册<br/>ToolRegistry.register()"]
        MCPReg["MCP 自动发现<br/>MCP Bridge"]
        Plugin["插件加载<br/>JAR 热加载"]
    end

    Anno --> Registry["Tool Registry"]
    Code --> Registry
    MCPReg --> Registry
    Plugin --> Registry

    Registry --> Meta["Tool 元数据"]
    Registry --> Cache["工具缓存"]
```

### 8.3 Tool 接口定义

```java
@FunctionalInterface
public interface Tool {

    /**
     * 执行工具
     * @param input   工具入参（JSON Schema 定义）
     * @param context 执行上下文
     * @return 工具执行结果
     */
    ToolResult execute(ToolInput input, ToolContext context);

    /** 工具元数据 */
    default ToolMetadata metadata() {
        return ToolMetadata.from(this);
    }
}
```

### 8.4 Tool 安全策略

| 策略 | 说明 |
|------|------|
| **超时控制** | 单次 Tool 执行最大时长限制 |
| **沙盒隔离** | 代码执行类 Tool 在沙盒中运行 |
| **权限校验** | Tool 执行前校验 Agent 是否有调用权限 |
| **参数校验** | 基于 JSON Schema 校验入参 |
| **结果脱敏** | 敏感字段过滤（API Key、密码等） |

---

## 9. MCP 设计

### 9.1 MCP 架构

```mermaid
graph TB
    subgraph LightBot["LightBot"]
        AgentRT["Agent Runtime"]
        MCPClient["MCP Client"]
        ToolBridge["Tool Bridge"]
    end

    subgraph MCPServers["MCP Servers"]
        S1["Filesystem Server"]
        S2["Database Server"]
        S3["Custom Server"]
    end

    AgentRT -->|"tool_call(name, args)"| MCPClient
    MCPClient -->|"JSON-RPC 2.0"| MCPServers
    MCPServers -->|"tool_result"| MCPClient
    MCPClient --> ToolBridge
    ToolBridge -->|"ToolResult"| AgentRT
```

### 9.2 MCP 通信协议

```mermaid
sequenceDiagram
    participant Agent
    participant Client as MCP Client
    participant Server as MCP Server

    Agent->>Client: initialize()
    Client->>Server: initialize (JSON-RPC)
    Server-->>Client: capabilities

    Agent->>Client: listTools()
    Client->>Server: tools/list
    Server-->>Client: tool definitions

    Agent->>Client: callTool(name, args)
    Client->>Server: tools/call
    Server-->>Client: tool result
    Client-->>Agent: ToolResult
```

### 9.3 MCP 配置模型

```java
public class MCPConfig {
    private String id;
    private String name;
    private TransportType transport;  // STDIO / SSE / HTTP
    private String endpoint;          // Server 地址
    private Map<String, String> env;  // 环境变量
    private RetryConfig retry;        // 重试策略
    private HealthCheck health;       // 健康检查
}
```

### 9.4 MCP Tool 适配

```mermaid
graph LR
    MCPTool["MCP Tool Definition<br/>name + inputSchema"] --> Adapter["Tool Adapter"]
    Adapter --> InternalTool["内部 Tool 接口"]
    InternalTool --> Registry["Tool Registry"]

    style Adapter fill:#f9f,stroke:#333
```

MCP Tool 自动转换为内部 Tool 接口，对 Agent 透明。

---

## 10. RAG 架构

### 10.1 RAG 流程

```mermaid
graph TB
    subgraph Ingestion["文档摄入 Pipeline"]
        Upload["文档上传"] --> Parse["文档解析<br/>PDF / Word / MD / HTML"]
        Parse --> Chunk["智能分块<br/>语义分块 / 递归分块"]
        Chunk --> Embed["向量化<br/>Embedding Model"]
        Embed --> Store["存入 Vector Store<br/>pgvector"]
    end

    subgraph Retrieval["检索 Pipeline"]
        Query["用户 Query"] --> QueryEmbed["Query 向量化"]
        QueryEmbed --> Search["向量检索"]
        Search --> Rerank["重排序<br/>Cross-Encoder"]
        Rerank --> Filter["元数据过滤<br/>权限 / 标签"]
        Filter --> Context["组装 Context"]
    end

    subgraph Generation["生成 Pipeline"]
        Context --> Prompt["拼装 Prompt<br/>System + Context + Query"]
        Prompt --> LLM["LLM 生成"]
        LLM --> Cite["引用标注"]
        Cite --> Answer["返回答案"]
    end

    Store -.-> Search
```

### 10.2 检索策略

| 策略 | 实现 | 适用场景 |
|------|------|---------|
| **向量检索** | pgvector cosine | 语义相似 |
| **关键词检索** | PostgreSQL FTS | 精确匹配 |
| **混合检索** | 向量 + 关键词加权 | 通用场景 |
| **图谱检索** | Knowledge Graph traversal | 实体关系推理 |

### 10.3 分块策略

```java
public interface ChunkStrategy {
    List<DocumentChunk> split(Document document, ChunkConfig config);
}

// 实现
public enum ChunkType {
    RECURSIVE,      // 递归字符分块
    SEMANTIC,       // 语义分块（基于 Embedding 相似度）
    MARKDOWN,       // Markdown 标题分块
    FIXED_SIZE      // 固定大小 + 滑动窗口
}
```

### 10.4 引用追溯

```mermaid
graph LR
    Answer["生成答案"] --> CiteParser["引用解析器"]
    CiteParser --> SourceMap["Source 映射"]
    SourceMap --> DocRef["原始文档定位<br/>文件 + 页码 + 段落"]
```

---

## 11. Knowledge 设计

### 11.1 知识库模型

```mermaid
erDiagram
    KNOWLEDGE_BASE ||--o{ DOCUMENT : contains
    DOCUMENT ||--o{ DOCUMENT_CHUNK : split_into
    DOCUMENT_CHUNK ||--o{ CHUNK_EMBEDDING : has

    KNOWLEDGE_BASE {
        string id PK
        string name
        string description
        string embedding_model
        json metadata
    }

    DOCUMENT {
        string id PK
        string knowledge_base_id FK
        string filename
        string file_type
        int chunk_count
        string status
    }

    DOCUMENT_CHUNK {
        string id PK
        string document_id FK
        int chunk_index
        text content
        json metadata
        vector embedding
    }
```

### 11.2 Knowledge Graph

```mermaid
graph TB
    subgraph Construction["图谱构建"]
        Doc["文档"] --> NER["实体抽取<br/>LLM / NER"]
        NER --> RE["关系抽取"]
        RE --> Dedup["实体消歧"]
        Dedup --> GraphStore["存入图数据库<br/>PostgreSQL"]
    end

    subgraph Query["图谱查询"]
        Q["用户问题"] --> EntityLink["实体链接"]
        EntityLink --> Traverse["图遍历"]
        Traverse --> SubGraph["子图提取"]
        SubGraph --> Reasoning["LLM 推理"]
        Reasoning --> Answer["返回答案"]
    end

    GraphStore -.-> Traverse
```

### 11.3 知识检索混合策略

```mermaid
graph TB
    Query["用户 Query"] --> Router["检索路由器"]

    Router -->|"事实类问题"| KG["Knowledge Graph 检索"]
    Router -->|"文档类问题"| RAG["RAG 向量检索"]
    Router -->|"复杂问题"| Hybrid["混合检索<br/>RAG + KG"]

    KG --> Merge["结果合并 + 去重"]
    RAG --> Merge
    Hybrid --> Merge

    Merge --> Rerank["重排序"]
    Rerank --> Context["注入上下文"]
```

---

## 12. Streaming 设计

### 12.1 流式输出架构

```mermaid
graph LR
    subgraph Server["服务端"]
        LLM["LLM Provider<br/>(SSE Stream)"] --> Processor["Chunk 处理器"]
        Processor --> Emitter["SSE Emitter"]
    end

    subgraph Client["客户端"]
        ES["EventSource"] --> Parser["SSE Parser"]
        Parser --> Render["Markdown 渲染<br/>(逐 Token)"]
    end

    Emitter -->|"text/event-stream"| ES
```

### 12.2 SSE 事件协议

```
event: message_start
data: {"message_id":"xxx","model":"gpt-4"}

event: content_delta
data: {"delta":"你好","index":0}

event: tool_call_start
data: {"tool_name":"search","tool_id":"xxx"}

event: tool_call_delta
data: {"delta":"{\"query\":"}

event: tool_call_end
data: {"tool_id":"xxx","result":{...}}

event: message_end
data: {"usage":{"prompt_tokens":100,"completion_tokens":50}}
```

### 12.3 Tool Call 流式处理

```mermaid
sequenceDiagram
    participant LLM
    participant Server
    participant Client

    LLM->>Server: stream: content delta "让我"
    Server->>Client: event: content_delta

    LLM->>Server: stream: tool_call_start
    Server->>Client: event: tool_call_start

    LLM->>Server: stream: tool_call arguments
    Server->>Client: event: tool_call_delta

    LLM->>Server: stream: tool_call_end
    Server->>Server: 执行 Tool
    Server->>Client: event: tool_call_end + result

    LLM->>Server: stream: 最终回答
    Server->>Client: event: content_delta
    Server->>Client: event: message_end
```

### 12.4 背压控制

```java
// Reactor 背压处理
Flux<ServerSentEvent<ChunkEvent>> stream = agentService
    .streamChat(request)
    .onBackpressureBuffer(256, BufferOverflowStrategy.DROP_OLDEST)
    .map(chunk -> ServerSentEvent.builder(chunk)
        .event(chunk.getType().name())
        .build());
```

---

## 13. 插件化设计

### 13.1 插件体系

```mermaid
graph TB
    subgraph Plugin["插件加载器"]
        Scanner["JAR Scanner"]
        ClassLoader["Plugin ClassLoader"]
        Registry["Plugin Registry"]
    end

    subgraph Extensions["扩展点"]
        ToolExt["Tool SPI"]
        ModelExt["Model Provider SPI"]
        NodeExt["Workflow Node SPI"]
        SkillExt["Skill SPI"]
    end

    subgraph Plugins["插件实现"]
        P1["自定义 Tool 插件"]
        P2["模型供应商插件"]
        P3["自定义节点插件"]
    end

    Scanner --> ClassLoader --> Registry
    Registry --> Extensions
    Plugins --> Extensions
```

### 13.2 扩展点定义

```java
/** Tool 扩展点 */
public interface ToolPlugin {
    List<Tool> getTools();
    PluginMetadata metadata();
}

/** 模型供应商扩展点 */
public interface ModelProviderPlugin {
    ModelProvider getProvider();
    List<String> supportedModels();
}

/** Workflow 节点扩展点 */
public interface NodePlugin {
    List<WorkflowNode> getNodes();
}
```

### 13.3 插件生命周期

```mermaid
stateDiagram-v2
    [*] --> Discovered: 扫描 JAR
    Discovered --> Loaded: 加载类
    Loaded --> Initialized: 调用 init()
    Initialized --> Active: 注册到 Registry
    Active --> Suspended: 卸载请求
    Suspended --> Active: 重新激活
    Active --> Unloaded: 卸载
    Unloaded --> [*]
```

---

## 14. 数据流

### 14.1 用户请求全链路

```mermaid
graph LR
    User["用户"] -->|"HTTP/SSE"| Gateway["API Gateway"]
    Gateway -->|"鉴权通过"| Router["路由分发"]
    Router -->|"Chat 请求"| AgentSvc["Agent Service"]
    Router -->|"Workflow 请求"| WFSvc["Workflow Service"]

    AgentSvc -->|"构建上下文"| CtxBuilder["Context Builder"]
    CtxBuilder -->|"注入知识"| RAGSvc["RAG Service"]
    CtxBuilder -->|"调用模型"| ModelSvc["Model Service"]
    ModelSvc -->|"Tool Call"| ToolSvc["Tool Service"]

    WFSvc -->|"解析 DAG"| DAGParser["DAG Parser"]
    DAGParser -->|"调度节点"| NodeExecutor["Node Executor"]
    NodeExecutor -->|"调用模型"| ModelSvc
    NodeExecutor -->|"调用工具"| ToolSvc

    RAGSvc -->|"查询"| PG["PostgreSQL + pgvector"]
    ModelSvc -->|"调用"| LLM["LLM Provider"]
    ToolSvc -->|"执行"| Tools["Tool 实现"]
```

### 14.2 端到端数据流

```mermaid
sequenceDiagram
    participant User
    participant Gateway
    participant Agent
    participant Context
    participant RAG
    participant Model
    participant Tool
    participant DB

    User->>Gateway: POST /chat (stream=true)
    Gateway->>Gateway: API Key 鉴权
    Gateway->>Agent: forward request

    Agent->>DB: 加载 Agent 配置
    Agent->>Context: 构建上下文

    Context->>RAG: 查询相关知识
    RAG->>DB: pgvector 检索
    DB-->>RAG: Top-K 文档片段
    RAG-->>Context: 知识上下文

    Context->>Model: 调用 LLM (stream)
    Model-->>Agent: 流式返回

    alt Tool Call
        Agent->>Tool: 执行工具
        Tool-->>Agent: 工具结果
        Agent->>Model: 继续对话
    end

    Agent-->>Gateway: SSE 事件流
    Gateway-->>User: text/event-stream
```

### 14.3 Workflow 数据流

```mermaid
sequenceDiagram
    participant Client
    participant Engine
    participant Scheduler
    participant NodeA as Node A (LLM)
    participant NodeB as Node B (Tool)
    participant NodeC as Node C (Condition)

    Client->>Engine: execute(inputs)
    Engine->>Scheduler: 构建 DAG

    Scheduler->>NodeA: 执行（输入: user_query）
    NodeA-->>Scheduler: output: {answer, confidence}

    par 并行执行
        Scheduler->>NodeB: 执行（输入: NodeA.answer）
        Scheduler->>NodeC: 执行（输入: NodeA.confidence）
    end

    NodeC-->>Scheduler: branch: "high"
    Scheduler->>Scheduler: 按条件选择后续节点

    NodeB-->>Scheduler: output: {result}
    Scheduler-->>Engine: 汇总输出
    Engine-->>Client: 最终结果
```

---

## 15. 未来扩展能力

### 15.1 扩展路线图

```mermaid
graph LR
    subgraph Phase1["v0.1 MVP"]
        Chat["基础对话"]
        BasicAgent["基础 Agent"]
        BasicTool["内置 Tool"]
    end

    subgraph Phase2["v0.2 Agent + Tool"]
        AgentTemplate["Agent 模板"]
        CustomTool["自定义 Tool"]
        BasicRAG["基础 RAG"]
    end

    subgraph Phase3["v0.3 Workflow"]
        VisualWF["可视化 Workflow"]
        NodeTypes["多种节点类型"]
        WFDebug["Workflow 调试"]
    end

    subgraph Phase4["v2.0 生产"]
        MultiTenant["多租户"]
        APIKey["API Key 管理"]
        Docker["Docker 部署"]
    end

    subgraph Future["未来规划"]
        MultiAgent["多 Agent 协同"]
        KnowledgeGraph["知识图谱"]
        SkillMarket["Skill 市场"]
        Observability["可观测性"]
    end

    Phase1 --> Phase2 --> Phase3 --> Phase4 --> Future
```

### 15.2 多 Agent 协同（规划中）

```mermaid
graph TB
    subgraph Orchestrator["编排层"]
        Planner["任务规划器"]
        Dispatcher["Agent 调度器"]
    end

    subgraph Agents["Agent 池"]
        A1["研究 Agent"]
        A2["写作 Agent"]
        A3["代码 Agent"]
        A4["审核 Agent"]
    end

    Planner -->|"分解任务"| Dispatcher
    Dispatcher -->|"分发子任务"| Agents
    Agents -->|"返回结果"| Planner
    Planner -->|"汇总结论"| Output["最终输出"]
```

### 15.3 可观测性（规划中）

| 维度 | 指标 | 实现 |
|------|------|------|
| **Trace** | 请求链路追踪 | OpenTelemetry |
| **Metrics** | Token 消耗、延迟、QPS | Prometheus + Grafana |
| **Logs** | Agent 执行日志、Tool 调用日志 | ELK / Loki |
| **Eval** | 回答质量评估、RAG 准确率 | 自定义评估框架 |

### 15.4 渐进式演进策略

| 阶段 | 架构形态 | 触发条件 |
|------|---------|---------|
| **当前** | 单体应用 | 起步阶段 |
| **中期** | 模块化单体 | 模块边界清晰后 |
| **远期** | 微服务（按需拆分） | 独立扩展需求出现 |

> **核心原则**：不过度设计。单体优先，模块边界清晰后再考虑拆分。

---

## 附录：技术选型

| 组件 | 选型 | 理由 |
|------|------|------|
| **框架** | SpringBoot 3.x | Java 生态主流，SpringAI 原生支持 |
| **AI SDK** | SpringAI | 统一模型抽象，Spring 生态融合 |
| **前端** | Vue3 + TypeScript | 轻量、生态成熟 |
| **工作流画布** | Vue Flow | Vue3 原生 DAG 编辑器 |
| **数据库** | PostgreSQL + pgvector | 关系 + 向量一体化 |
| **缓存** | Redis | 会话、限流、分布式锁 |
| **ORM** | MyBatis-Plus | 灵活 SQL + 代码生成 |
| **文档解析** | Apache Tika | 多格式统一解析 |
| **构建** | Maven | Java 项目标准构建 |

---

*Last updated: 2026-05-19*
