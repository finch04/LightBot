# LightBot 后端模块化拆分设计文档

> 作者：finch
> 日期：2026-06-20
> 状态：草案

---

## 一、需求分析

### 1.1 现状

当前 LightBot 后端是单体架构，所有代码在 `lightbot-server` 一个 Maven 模块中，通过包结构做业务域划分：

```
com.lightbot/
├── controller/    # 30 个 Controller
├── service/       # 61 个 Service + impl/
├── entity/        # 40 个 Entity
├── dto/           # 数据传输对象
├── mapper/        # 40 个 Mapper
├── workflow/      # 工作流引擎
├── tool/          # Tool 体系
├── skill/         # Skill 运行时
├── subagent/      # SubAgent 运行时
├── task/          # 异步任务框架
├── log/           # 日志基础设施
├── model/         # 模型工厂
├── util/          # 中间件工具类
├── config/        # 配置类
├── common/        # 公共工具、异常
├── enums/         # 枚举
└── validation/    # 校验逻辑
```

### 1.2 问题

| 问题 | 影响 |
|------|------|
| 编译慢 | 改一行代码需要全量编译 5.3 万行 |
| 边界模糊 | 任何包都能直接 import 任何其他包，容易产生隐式耦合 |
| 无法独立部署 | 扩展模块必须和主服务一起启动 |
| 测试困难 | 单元测试需要加载整个 Spring 上下文 |
| 协作冲突 | 多人开发同一模块，合并冲突频繁 |

### 1.3 目标

1. **编译隔离**：改动只影响所在模块，增量编译
2. **依赖显式化**：模块间通过接口通信，禁止直接 import 实现类
3. **可独立测试**：每个模块可单独加载和测试
4. **为微服务铺路**：模块边界清晰，未来可按需拆分部署
5. **渐进式迁移**：不一次性拆完，先核心后辅助

---

## 二、现有依赖关系分析

通过扫描所有 Java 文件的 import 语句，得到以下依赖图：

```
┌──────────────────────────────────────────────────────────────┐
│                    依赖关系图（A → B 表示 A 依赖 B）           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  workflow ──→ service (AgentService, ToolService, etc.)       │
│  tool     ──→ service (KnowledgeService, DocumentService)     │
│  subagent ──→ service (ToolService, chat/InitMiddleware)      │
│  skill    ──→ entity, mapper, model (最孤立)                  │
│                                                              │
│  service  ──→ workflow (WorkflowConfigParser, Executor)       │
│  service  ──→ tool     (ToolEventEmitter, QueryKnowledgeTool) │
│  service  ──→ subagent (DelegateSubAgentTool)                 │
│  service  ──→ skill    (不直接依赖，通过 SkillService 接口)    │
│                                                              │
│  controller ──→ service（纯消费，不直接依赖 workflow/tool/…）  │
│                                                              │
│  eval     ──→ entity, model, service（完全独立于 workflow 等） │
│  prompt   ──→ entity, mapper, service（完全独立）              │
│  rag      ──→ entity, model, service, util（完全独立）         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**关键发现**：

1. `workflow`、`tool`、`skill`、`subagent` **四者互不依赖**
2. `service` 层是唯一的**汇聚点**，桥接了 workflow、tool、subagent
3. `eval`、`prompt`、`rag` **完全独立**于 workflow/tool/skill/subagent
4. `controller` 是纯消费者，只依赖 `service` 接口

---

## 三、模块化方案

### 3.1 模块划分

```
lightbot/
├── lightbot-common/          # 公共基础层
├── lightbot-ai/              # AI 能力层（模型工厂 + LLM 调用）
├── lightbot-extension/       # 扩展层（Tool + MCP + Skill + SubAgent）
├── lightbot-workflow/        # 工作流引擎
├── lightbot-rag/             # RAG 知识库 + 评测 + Prompt
├── lightbot-admin/           # 主服务入口（Spring Boot 启动类 + Controller + Service 编排层）
```

### 3.2 各模块职责

#### lightbot-common

**职责**：所有模块的公共基础，不包含业务逻辑。

```
com.lightbot.common/
├── common/          # Result, BizException, ErrorCode
├── enums/           # 全局枚举
├── entity/          # 所有数据库实体（40 个 Entity）
├── dto/             # 数据传输对象
├── mapper/          # MyBatis-Plus Mapper（40 个）
├── model/           # ModelFactory, SkillMetadata 等模型定义
├── util/            # MinioUtil, RedisUtil, LlmTraceContext 等
├── config/          # 全局配置类
├── validation/      # 校验逻辑
└── log/             # 日志基础设施
```

**依赖**：无（最底层）

**理由**：Entity、Mapper、DTO、枚举、工具类是所有模块都需要的公共类型。把它们放在 common 中，避免循环依赖。

---

#### lightbot-ai

**职责**：AI 能力封装，提供统一的 LLM 调用接口。

```
com.lightbot.ai/
├── model/           # ModelFactory（动态 ChatModel 创建）
├── chat/            # 对话引擎核心（ChatService 接口、中间件链）
└── eval/            # 评测引擎（EvalChatService、RagEvaluationEngine）
```

**依赖**：lightbot-common

**理由**：
- `ModelFactory` 是多个模块（workflow、subagent、eval、rag）的共同依赖
- 对话引擎（`ChatService`、中间件链）是 Agent 运行的核心，extension 和 workflow 都需要
- 评测引擎只依赖 ModelFactory 和 service 接口，属于 AI 能力范畴

**需要从 service 层迁移的内容**：
- `service/chat/` 整个包（InitMiddleware、ToolPrepMiddleware、WorkflowMiddleware、MemoryMiddleware 等）
- `service/EvalChatService` + `service/impl/EvalChatServiceImpl`
- `service/eval/RagEvaluationEngine`

---

#### lightbot-extension

**职责**：Agent 扩展体系，提供能力注入机制。

```
com.lightbot.extension/
├── tool/            # Tool 体系
│   ├── Tool.java                    # Tool 接口
│   ├── ToolEventEmitter.java        # 工具事件发射器
│   ├── builtin/                     # 内置工具（HTTP、代码执行等）
│   ├── systemtool/                  # 系统工具
│   └── registrar/                   # ToolRegistrar
├── mcp/             # MCP 客户端
│   ├── McpClientService.java        # MCP 连接管理
│   ├── BuiltinMcpRegistrar.java     # MCP 注册器
│   └── ...
├── skill/           # Skill 运行时
│   ├── BuiltInSkillRegistrar.java   # 内置技能注册
│   └── ...
├── subagent/        # SubAgent 运行时
│   ├── SubAgentRuntime.java         # SubAgent 执行引擎
│   ├── DelegateSubAgentTool.java    # SubAgent 委托工具
│   └── BuiltInSubAgentRegistrar.java
└── sandbox/         # 沙箱服务
    ├── SkillStorageService.java
    └── SkillActivationStore.java
```

**依赖**：lightbot-common, lightbot-ai

**理由**：
- Tool、MCP、Skill、SubAgent **四者互不依赖**，但它们有共同的职责模式：都是 Agent 的能力扩展
- 合并为一个模块可以减少模块数量，降低管理成本
- 它们都通过 service 接口与业务层交互，不需要直接依赖 rag 或 workflow

**需要从现有包迁移的内容**：
- `tool/` 整个包
- `skill/` 整个包
- `subagent/` 整个包
- `service/sandbox/` 整个包
- `service/McpClientService`、`service/McpServerService`、`service/ToolService`、`service/SkillService`、`service/SubAgentService` 的**接口定义**（实现留在 admin）

---

#### lightbot-workflow

**职责**：DAG 工作流引擎，节点定义与执行。

```
com.lightbot.workflow/
├── WorkflowExecutorService.java    # DAG 执行器
├── WorkflowDefinition.java         # 工作流定义
├── WorkflowConfigParser.java       # 配置解析
├── processor/                      # 18 种节点处理器
│   ├── Node.java                   # 节点接口
│   ├── LlmNodeProcessor.java
│   ├── ToolNodeProcessor.java
│   ├── McpNodeProcessor.java
│   ├── RetrievalNodeProcessor.java
│   ├── ConditionNodeProcessor.java
│   └── ...
└── model/                          # 工作流内部模型
    ├── WorkflowNode.java
    ├── WorkflowEdge.java
    └── NodeExecutionContext.java
```

**依赖**：lightbot-common, lightbot-ai, lightbot-extension（通过 service 接口间接依赖）

**关键问题**：workflow 的 `ToolNodeProcessor` 需要调用 Tool，`McpNodeProcessor` 需要调用 MCP。但分析发现，它们是通过 `ToolService`、`McpClientService` 等**接口**调用的，不是直接 import tool 包。所以 workflow 模块只需要依赖这些接口（放在 common 或 extension 中），不需要依赖 tool 的实现。

**需要处理的依赖**：
- `ToolNodeProcessor` → `ToolService`（接口放 common）
- `McpNodeProcessor` → `McpClientService`、`McpServerService`（接口放 common 或 extension）
- `RetrievalNodeProcessor` → `KnowledgeService`、`EmbeddingService`（接口放 rag 或 common）

---

#### lightbot-rag

**职责**：RAG 知识库 + 评测体系 + Prompt 工程。

```
com.lightbot.rag/
├── knowledge/       # 知识库管理
│   ├── KnowledgeService.java
│   ├── DocumentService.java
│   ├── ChunkService.java
│   ├── EmbeddingService.java
│   └── ...
├── graph/           # 知识图谱
│   ├── GraphService.java
│   └── ...
├── qa/              # QA 对管理
│   ├── QaPairService.java
│   └── ...
├── eval/            # 评测体系
│   ├── EvalDatasetService.java
│   ├── EvalEvaluatorService.java
│   ├── EvalExperimentService.java
│   ├── EvalRagBenchmarkService.java
│   └── ...
├── prompt/          # Prompt 工程
│   ├── PromptService.java
│   ├── PromptVersionService.java
│   └── PromptBuildTemplateService.java
└── ingest/          # 文档摄入
    ├── IngestService.java
    └── chunking/    # 分块策略
```

**依赖**：lightbot-common, lightbot-ai

**理由**：
- RAG、评测、Prompt 三者都**完全独立**于 workflow/tool/skill/subagent
- 它们共同的依赖是 entity、model（ModelFactory）、util（MinIO、向量数据库）
- 评测系统需要调用 LLM（通过 ModelFactory），属于 AI 能力的消费者
- Prompt 工程与评测紧密相关（评估器的 prompt 模板），放在一起更合理

**为什么不单独拆 eval 和 prompt**：
- eval 只有 12 个 Entity + 6 个 Service，体量不大
- prompt 只有 3 个 Entity + 3 个 Service，体量更小
- 它们的依赖完全相同（entity + model + service 接口），拆开只会增加模块数量

---

#### lightbot-admin

**职责**：Spring Boot 主服务入口，Controller 层 + Service 编排层。

```
com.lightbot.admin/
├── LightBotApplication.java       # 启动类
├── controller/                    # 30 个 Controller（纯透传）
├── service/                       # Service 接口定义 + 编排层实现
│   ├── AgentService.java          # 接口
│   ├── impl/
│   │   ├── AgentServiceImpl.java  # 编排实现
│   │   ├── ChatServiceImpl.java   # 对话编排（桥接 extension + workflow + rag）
│   │   └── ...
│   └── ...
├── handler/                       # 全局异常处理
├── task/                          # 异步任务框架
└── config/                        # 业务配置
```

**依赖**：lightbot-common, lightbot-ai, lightbot-extension, lightbot-workflow, lightbot-rag

**理由**：
- admin 是唯一的聚合点，负责组装所有模块
- Controller 只做参数校验和返回封装，不含业务逻辑
- Service 编排层协调各模块：如 `ChatServiceImpl` 需要调用 extension（Tool 调用）、workflow（工作流执行）、rag（知识库检索）
- 任务框架（task/）是全局调度，放在 admin

---

### 3.3 依赖关系总览

```
                    ┌──────────────┐
                    │ lightbot-admin│  ← Spring Boot 入口
                    │ (controller,  │
                    │  service编排) │
                    └──────┬───────┘
                           │ 依赖所有下层模块
            ┌──────────────┼──────────────┐
            │              │              │
            ▼              ▼              ▼
   ┌────────────┐  ┌──────────────┐  ┌─────────┐
   │lightbot-rag│  │lightbot-work-│  │lightbot- │
   │(知识库,评测,│  │flow(DAG引擎, │  │extension │
   │ Prompt)    │  │ 18种节点)    │  │(Tool,MCP,│
   └─────┬──────┘  └──────┬───────┘  │ Skill,   │
         │                │          │ SubAgent)│
         │                │          └────┬─────┘
         │                │               │
         ▼                ▼               ▼
   ┌─────────────────────────────────────────┐
   │           lightbot-ai                   │
   │  (ModelFactory, ChatEngine, EvalEngine) │
   └─────────────────┬───────────────────────┘
                     │
                     ▼
   ┌─────────────────────────────────────────┐
   │          lightbot-common                │
   │  (Entity, Mapper, DTO, Enum, Util,     │
   │   Config, Validation, Log)              │
   └─────────────────────────────────────────┘
```

**规则**：
- **单向依赖**：上层依赖下层，下层禁止依赖上层
- **同层禁止循环依赖**：rag、workflow、extension 三者互不依赖
- **admin 是唯一聚合点**：只有 admin 能同时依赖所有模块

---

## 四、技术设计

### 4.1 Service 接口分层

核心问题：extension 模块的 `ToolNodeProcessor` 需要调用 Tool，但 Tool 的实现在 extension 模块内部，而 `ToolService` 的实现在 admin 模块。

**解决方案：接口下沉，实现上浮**

```
common 中定义接口：
  com.lightbot.common.service.ToolService        → 接口
  com.lightbot.common.service.KnowledgeService    → 接口
  com.lightbot.common.service.McpClientService    → 接口

admin 中提供实现：
  com.lightbot.admin.service.impl.ToolServiceImpl        → 实现
  com.lightbot.admin.service.impl.KnowledgeServiceImpl    → 实现
  com.lightbot.admin.service.impl.McpClientServiceImpl    → 实现
```

各模块通过接口调用，运行时由 Spring 注入 admin 中的实现。

### 4.2 Service 接口迁移清单

哪些 Service 接口需要下沉到 common：

| 接口 | 当前位置 | 被谁调用 | 下沉目标 |
|------|---------|---------|---------|
| `ToolService` | service/ | workflow(ToolNode), subagent(SubAgentRuntime) | common |
| `McpClientService` | service/ | workflow(McpNode) | common |
| `McpServerService` | service/ | tool(BuiltinMcpRegistrar), workflow(McpNode) | common |
| `KnowledgeService` | service/ | tool(QueryKnowledgeTool), workflow(RetrievalNode), rag | common |
| `EmbeddingService` | service/ | tool(QueryKnowledgeTool), workflow(RetrievalNode), rag | common |
| `DocumentService` | service/ | tool(FindInDocumentTool) | common |
| `AgentService` | service/ | tool(KnowledgeTools), subagent, workflow | common |
| `AgentVersionService` | service/ | workflow(WorkflowExecutor) | common |
| `MessageService` | service/ | workflow(WorkflowExecutor) | common |
| `QaPairService` | service/ | tool(QueryKnowledgeTool) | common |
| `SkillService` | service/ | tool(ReadSkillTool) | common |
| `SubAgentService` | service/ | subagent(DelegateSubAgentTool) | common |
| `SystemConfigService` | service/ | rag, ai(EvalEngine) | common |

### 4.3 Entity / Mapper 归属

所有 Entity 和 Mapper 放在 common 中，因为：
- 多个模块需要访问同一张表（如 `Agent` 被 workflow、extension、admin 共同访问）
- Entity 是数据库映射，不属于任何业务域
- Mapper 是数据访问层，依赖 Entity，自然跟随 Entity

### 4.4 配置类归属

| 配置类 | 归属模块 | 理由 |
|--------|---------|------|
| MyBatis-Plus 配置 | common | 全局 ORM 配置 |
| Redis 配置 | common | 全局缓存 |
| Sa-Token 配置 | admin | 认证属于入口层 |
| MinIO 配置 | common | 文件存储是公共能力 |
| Milvus 配置 | common | 向量数据库是公共能力 |
| Neo4j 配置 | rag | 图数据库只被知识图谱使用 |
| Spring AI 配置 | ai | 模型框架配置 |

---

## 五、难点与风险

### 5.1 循环依赖风险

**场景**：`ChatServiceImpl`（admin）需要调用 extension 的 Tool 调用，而 extension 的某些工具（如 `QueryKnowledgeTool`）又需要调用 rag 的 `KnowledgeService`。

**分析**：这不是循环依赖。调用链是单向的：
```
admin(ChatServiceImpl) → extension(Tool) → common(KnowledgeService接口) ← admin(KnowledgeServiceImpl)
```
运行时通过 Spring 的依赖注入，extension 拿到的是 common 中的接口，admin 提供实现。模块间的编译依赖是单向的。

### 5.2 ChatMiddleware 归属

当前 `service/chat/` 包含多个中间件（InitMiddleware、ToolPrepMiddleware、WorkflowMiddleware、MemoryMiddleware），它们是对话引擎的核心编排逻辑。

**方案**：将 `service/chat/` 整体迁移到 `lightbot-ai` 模块。理由：
- 中间件链是对话引擎的内部机制，不属于任何具体业务域
- `WorkflowMiddleware` 虽然调用了 workflow，但它是通过 `WorkflowExecutorService` 接口调用的
- `ToolPrepMiddleware` 调用 `DelegateSubAgentTool`，但也是通过接口

**风险**：`ToolPrepMiddleware` 直接 import 了 `com.lightbot.subagent.DelegateSubAgentTool`。迁移到 ai 模块后，ai 模块需要依赖 extension 模块（因为 DelegateSubAgentTool 在 extension 中）。这违反了"同层不依赖"原则。

**解决方案**：在 common 中定义 `SubAgentDelegate` 接口，`DelegateSubAgentTool` 在 extension 中实现，`ToolPrepMiddleware` 通过接口调用。

### 5.3 内置工具对业务 Service 的依赖

`tool/builtin/` 中的工具（如 `FindInDocumentTool`、`QueryKnowledgeTool`、`KnowledgeTools`）直接 import 了多个 Service（KnowledgeService、DocumentService、EmbeddingService、AgentService）。

**方案**：这些 Service 接口已下沉到 common，工具通过接口调用，无循环依赖问题。

### 5.4 Spring Boot 启动类扫描

拆分后，Spring Boot 需要扫描多个模块的 `@Component`、`@Service` 等注解。

**方案**：`LightBotApplication.java` 放在 admin 模块，通过 `@SpringBootApplication(scanBasePackages = "com.lightbot")` 扫描所有模块。

### 5.5 MyBatis-Plus Mapper 扫描

Mapper 接口在 common 模块中，需要被 Spring Boot 正确扫描。

**方案**：在 admin 的启动类上配置 `@MapperScan("com.lightbot.common.mapper")`。

### 5.6 前端 API 不变

模块化只影响后端内部结构，Controller 的 URL 路径和请求/响应格式不变，**前端零改动**。

---

## 六、工作量评估

### 6.1 Phase 1：抽离 common（核心基础，优先级最高）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 创建 lightbot-common Maven 模块 | 0.5d | pom.xml + 目录结构 |
| 迁移 entity/ (40 个) | 1d | 包路径不变，只移文件 |
| 迁移 mapper/ (40 个) | 0.5d | 同上 |
| 迁移 dto/ | 0.5d | |
| 迁移 enums/ | 0.5d | |
| 迁移 common/ (Result, BizException 等) | 0.5d | |
| 迁移 util/ | 0.5d | |
| 迁移 config/ (全局配置) | 0.5d | |
| 迁移 model/ | 0.5d | |
| 迁移 validation/, log/ | 0.5d | |
| 定义 Service 接口（13 个） | 2d | 从 impl 中抽取接口 |
| 修改 admin 模块依赖 | 1d | 调整 import 路径 |
| 编译验证 + 测试 | 1d | |
| **小计** | **~10d** | |

### 6.2 Phase 2：抽离 ai（模型 + 对话引擎）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 创建 lightbot-ai Maven 模块 | 0.5d | |
| 迁移 model/ModelFactory | 0.5d | |
| 迁移 service/chat/（中间件链） | 2d | 需要处理 DelegateSubAgentTool 依赖 |
| 迁移 EvalChatService + RagEvaluationEngine | 1d | |
| 定义 SubAgentDelegate 接口 | 0.5d | 解耦 ToolPrepMiddleware |
| 编译验证 + 测试 | 1d | |
| **小计** | **~5.5d** | |

### 6.3 Phase 3：抽离 extension（扩展体系）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 创建 lightbot-extension Maven 模块 | 0.5d | |
| 迁移 tool/ 整个包 | 1d | |
| 迁移 skill/ 整个包 | 0.5d | |
| 迁移 subagent/ 整个包 | 0.5d | |
| 迁移 service/sandbox/ | 0.5d | |
| 编译验证 + 测试 | 1d | |
| **小计** | **~4d** | |

### 6.4 Phase 4：抽离 workflow（工作流引擎）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 创建 lightbot-workflow Maven 模块 | 0.5d | |
| 迁移 workflow/ 整个包 | 1d | |
| 迁移 service/impl/WorkflowConfigServiceImpl | 0.5d | |
| 处理 WorkflowMiddleware 依赖 | 1d | 已在 Phase 2 迁移到 ai |
| 编译验证 + 测试 | 1d | |
| **小计** | **~4d** | |

### 6.5 Phase 5：抽离 rag（知识库 + 评测 + Prompt）

| 任务 | 工作量 | 说明 |
|------|--------|------|
| 创建 lightbot-rag Maven 模块 | 0.5d | |
| 迁移知识库 Service（Knowledge, Document, Chunk, Embedding, QA, Graph） | 2d | |
| 迁移评测 Service（Eval*） | 1d | |
| 迁移 Prompt Service | 0.5d | |
| 迁移 IngestService + 分块策略 | 0.5d | |
| 编译验证 + 测试 | 1d | |
| **小计** | **~5.5d** | |

### 6.6 总计

| Phase | 模块 | 工作量 |
|-------|------|--------|
| Phase 1 | lightbot-common | ~10d |
| Phase 2 | lightbot-ai | ~5.5d |
| Phase 3 | lightbot-extension | ~4d |
| Phase 4 | lightbot-workflow | ~4d |
| Phase 5 | lightbot-rag | ~5.5d |
| **总计** | | **~29d** |

> 以上为保守估计，实际执行中 Phase 2-5 会越来越快（因为 common 已经就绪）。

---

## 七、可能存在的问题

### 7.1 接口膨胀

将 13+ 个 Service 接口下沉到 common，可能导致 common 模块变得臃肿。

**缓解措施**：
- 只下沉被跨模块调用的接口，不迁移所有 Service
- 按业务域分子包：`common.service.tool/`、`common.service.knowledge/` 等
- 未来如果 common 太大，可以进一步拆分为 `common-core` 和 `common-service-api`

### 7.2 开发体验变化

拆分后，开发者需要在多个 Maven 模块间切换，IDE 索引和跳转可能变慢。

**缓解措施**：
- 使用 IntelliJ IDEA 的多模块项目支持，体验基本不变
- 模块数量控制在 6 个以内，不要过度拆分

### 7.3 测试策略调整

拆分后，单元测试需要 mock 跨模块的接口调用。

**缓解措施**：
- Phase 1 完成后就开始写接口级测试，不要等到全部拆完
- 使用 Spring Boot Test 的 `@MockBean` 注入 mock 实现

### 7.4 事务边界

跨模块的事务操作需要特别注意。例如创建 Agent（admin）同时创建工作流配置（workflow）。

**缓解措施**：
- 事务注解 `@Transactional` 放在 admin 的 Service 编排层
- 下层模块不管理事务，只提供原子操作
- 需要分布式事务的场景极少（当前都是单库），暂不考虑 Saga/TCC

### 7.5 Entity 的归属争议

把所有 Entity 放在 common 中，意味着 rag 模块能看到 workflow 的 Entity，反之亦然。

**缓解措施**：
- 这是当前单体架构的现状，模块化后不会更差
- 未来如果需要进一步隔离，可以按业务域拆分 Entity 到各模块（但会增加模块间依赖）

---

## 八、迁移策略

### 8.1 渐进式迁移

不一次性拆完，每个 Phase 独立可交付：

1. **Phase 1 完成后**：common 模块独立，其他代码在 admin 中通过依赖 common 运行
2. **Phase 2 完成后**：ai 模块独立，对话引擎可独立测试
3. **Phase 3 完成后**：extension 模块独立，扩展体系可独立演进
4. 以此类推

每个 Phase 完成后都应保证：
- `mvn compile` 通过
- 现有功能不回归
- 前端无感知

### 8.2 包路径迁移规则

| 原路径 | 新路径 |
|--------|--------|
| `com.lightbot.entity.*` | `com.lightbot.common.entity.*` |
| `com.lightbot.mapper.*` | `com.lightbot.common.mapper.*` |
| `com.lightbot.workflow.*` | `com.lightbot.workflow.*`（不变） |
| `com.lightbot.tool.*` | `com.lightbot.extension.tool.*` |
| `com.lightbot.skill.*` | `com.lightbot.extension.skill.*` |
| `com.lightbot.subagent.*` | `com.lightbot.extension.subagent.*` |
| `com.lightbot.service.chat.*` | `com.lightbot.ai.chat.*` |
| `com.lightbot.service.eval.*` | `com.lightbot.rag.eval.*` |

> 包路径变更会影响所有 import 语句，建议用 IDE 的 Refactor → Move 功能批量处理。

---

## 九、决策记录

| 问题 | 决策 | 理由 |
|------|------|------|
| extension 包含 MCP/Skill/SubAgent/Tool？ | **是** | 四者互不依赖，共同职责是"扩展 Agent 能力"，合并减少模块数 |
| workflow 和 agent 放一个模块？ | **否** | workflow 有独立的 DAG 引擎和 18 种节点处理器，体量足够独立；且 workflow 通过接口调用 agent 相关服务，无强耦合 |
| 需要专门的 ai 模块？ | **是** | ModelFactory 和对话引擎（中间件链）是多个模块的共同依赖，抽离后避免 common 膨胀 |
| 评测和 Prompt 抽模块？ | **否，放入 rag** | 三者依赖完全相同（entity + model + service），体量都不大，合并更简洁；评测和 Prompt 与 RAG 有天然关联（RAG 评测、Prompt 模板） |
| Entity 放哪个模块？ | **全部放 common** | 多模块共享同一数据库表，放 common 避免循环依赖 |

---

## 十、最终架构总结

```
lightbot-admin (Spring Boot 入口)
  ├── controller/ (30 个)
  ├── service/impl/ (编排层)
  └── task/ (异步任务)
       │
       ├── lightbot-rag (知识库 + 评测 + Prompt)
       ├── lightbot-workflow (DAG 引擎 + 节点处理器)
       ├── lightbot-extension (Tool + MCP + Skill + SubAgent)
       │
       ├── lightbot-ai (ModelFactory + ChatEngine + EvalEngine)
       │
       └── lightbot-common (Entity + Mapper + DTO + Enum + Util + Service接口)
```

**6 个模块，5 层依赖，单向无环。**
