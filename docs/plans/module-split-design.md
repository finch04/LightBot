# LightBot 后端模块化拆分设计文档

> 作者：finch
> 日期：2026-06-21
> 状态：v2.0 - 修订版

---

## 一、设计思路

### 1.1 参考架构

参考 **RuoYi** 和 **Spring AI Alibaba Admin** 的模块化方案：

| 参考项目 | 模块划分 | 核心思想 |
|---------|---------|---------|
| RuoYi | common / framework / admin / 业务模块 | 三层分离：公共层、框架层、业务层 |
| Spring AI Alibaba Admin | common / admin / core / runtime | AI 能力与业务编排分离 |

### 1.2 设计原则

| 原则 | 说明 |
|------|------|
| **按功能域聚合** | 相关功能放同一模块，避免过度拆分导致模块碎片化 |
| **common 纯公共** | common 只放工具类、枚举、DTO、接口定义，不含业务方法 |
| **framework 基础设施** | 认证、任务、日志、配置等系统级能力放 framework |
| **业务模块自包含** | 每个业务模块包含自己的 Service 实现，通过接口对外暴露 |

### 1.3 与 v1.0 的主要差异

| 问题 | v1.0 方案 | v2.0 方案 |
|------|----------|----------|
| workflow 单独拆模块 | 独立 lightbot-workflow | 合并到 agent，内容更丰富 |
| ai 单独拆模块 | 独立 lightbot-ai | 合并到 agent，统一 AI 能力 |
| 评测和 Prompt 放 rag | rag 包含评测+Prompt | eval 独立，rag 只管知识库 |
| 系统管理放 admin | 认证、任务、配置都在 admin | 抽到 framework 基础设施层 |
| 无监控模块 | 无 | 新增 lightbot-monitor |
| common 包含 Service 接口 | 13+ 接口下沉到 common | 接口放各业务模块，common 只放纯公共 |

---

## 二、模块总览

### 2.1 八大模块

```
lightbot/
├── lightbot-common/       # 公共包（工具、枚举、DTO、常量）
├── lightbot-framework/    # 系统框架（认证、任务、日志、配置）
├── lightbot-agent/        # AI 能力核心（Agent + Workflow + AI + 模型）
├── lightbot-extension/    # 扩展体系（Tool + MCP + Skill + SubAgent）
├── lightbot-rag/          # 知识库（知识库 + QA + 图谱 + RAG检索）
├── lightbot-eval/         # 评测体系（Prompt + 评测 + 数据集 + 实验）
├── lightbot-monitor/      # 监控模块（Trace + 日志 + Dashboard）
└── lightbot-admin/        # 主服务入口（Controller + 启动类）
```

### 2.2 依赖关系

```
                         ┌─────────────────┐
                         │  lightbot-admin  │  ← Spring Boot 入口，Controller 聚合
                         └────────┬────────┘
                                  │ 依赖所有下层
        ┌────────┬────────┬───────┼───────┬────────┬────────┐
        ▼        ▼        ▼       ▼       ▼        ▼        ▼
   ┌────────┐┌────────┐┌──────┐┌──────┐┌──────┐┌────────┐┌────────┐
   │ agent  ││exten-  ││ rag  ││ eval ││moni- ││frame-  ││ common │
   │        ││sion    ││      ││      ││tor   ││work    ││        │
   └───┬────┘└───┬────┘└──┬───┘└──┬───┘└──┬───┘└───┬────┘└────────┘
       │         │        │       │       │        │
       └─────────┴────────┴───────┴───────┴────────┘
                          │
                    lightbot-common（最底层）
```

**规则**：
- 单向依赖：上层依赖下层，下层禁止依赖上层
- 同层不依赖：agent / extension / rag / eval / monitor 互不依赖
- admin 是唯一聚合点：只有 admin 能同时依赖所有模块

### 2.3 各模块内容概览

| 模块 | 职责 | 核心内容 | Entity 数 | Service 数 |
|------|------|---------|----------|-----------|
| common | 公共基础 | 工具类、枚举、DTO、常量、异常 | 0 | 0（只有接口） |
| framework | 系统框架 | 认证、任务、日志、配置、Dashboard | 4 | 6 |
| agent | AI 能力核心 | Agent、Workflow、AI 对话、模型管理 | 12 | 10 |
| extension | 扩展体系 | Tool、MCP、Skill、SubAgent、Sandbox | 5 | 5 |
| rag | 知识库 | 知识库、文档、向量化、QA、图谱 | 10 | 9 |
| eval | 评测体系 | Prompt、数据集、评估器、实验 | 9 | 11 |
| monitor | 监控 | LLM Trace、日志、可观测性 | 2 | 3 |
| admin | 入口聚合 | Controller、启动类 | 0 | 0 |

---

## 三、各模块详细设计

### 3.1 lightbot-common（公共包）

**职责**：所有模块的公共基础，**不包含任何业务逻辑和业务方法**。

```
com.lightbot.common/
├── result/              # 统一返回
│   ├── Result.java
│   └── PageResult.java
├── exception/           # 异常定义
│   ├── BizException.java
│   └── ErrorCode.java
├── enums/               # 全局枚举
│   ├── AgentStatus.java
│   ├── TaskStatus.java
│   └── ...
├── dto/                 # 公共 DTO（不含业务 DTO）
│   └── PageQuery.java
├── util/                # 工具类
│   ├── JsonUtil.java
│   ├── AssertUtil.java
│   └── SecurityUtil.java
├── constant/            # 常量
│   └── CommonConstant.java
└── validation/          # 校验注解
    └── EnumValue.java
```

**不包含**：
- Entity、Mapper（放各业务模块）
- Service 接口（放各业务模块）
- 中间件工具类（放 framework）
- 业务 DTO（放各业务模块）

**依赖**：无（最底层）

---

### 3.2 lightbot-framework（系统框架）

**职责**：系统级基础设施，提供认证、任务调度、日志、配置等通用能力。

```
com.lightbot.framework/
├── auth/                # 认证授权
│   ├── AuthController.java
│   ├── UserServiceImpl.java
│   ├── entity/
│   │   └── User.java
│   ├── mapper/
│   │   └── UserMapper.java
│   └── config/
│       └── SaTokenConfig.java
├── config/              # 系统配置
│   ├── SystemConfigController.java
│   ├── SystemConfigServiceImpl.java
│   ├── entity/
│   │   └── SystemConfig.java
│   └── mapper/
│       └── SystemConfigMapper.java
├── task/                # 任务框架
│   ├── TaskController.java
│   ├── TaskEventController.java
│   ├── TaskServiceImpl.java
│   ├── entity/
│   │   └── Task.java
│   ├── mapper/
│   │   └── TaskMapper.java
│   ├── executor/        # 任务执行器
│   │   ├── TaskExecutor.java          # 接口
│   │   ├── DocumentUploadExecutor.java
│   │   ├── DocumentIngestExecutor.java
│   │   ├── GraphExtractExecutor.java
│   │   ├── QaGenerateExecutor.java
│   │   ├── BenchmarkGenerateExecutor.java
│   │   ├── BenchmarkImportExecutor.java
│   │   ├── ExperimentRunExecutor.java
│   │   └── RagEvalExecutor.java
│   └── event/           # 任务事件
│       └── TaskEventPublisher.java
├── log/                 # 日志基础设施
│   ├── LogController.java
│   └── LogService.java
├── dashboard/           # 数据统计
│   ├── DashboardController.java
│   ├── DashboardServiceImpl.java
│   └── entity/
│       └── DashboardStat.java
├── util/                # 中间件工具类
│   ├── MinioUtil.java
│   ├── RedisUtil.java
│   └── OcrUtil.java
└── config/              # 框架配置
    ├── MybatisPlusConfig.java
    ├── RedisConfig.java
    ├── MinioConfig.java
    ├── MilvusConfig.java
    └── WebConfig.java
```

**包含的 Entity**：User、SystemConfig、Task、DashboardStat（4 个系统级 Entity）

**依赖**：lightbot-common

**为什么任务框架放 framework**：
- 任务框架是系统级基础设施，被 rag（文档入库、QA生成）、eval（实验运行）等多个模块使用
- 参考 RuoYi 的定时任务模块，任务调度属于框架层能力
- TaskExecutor 接口定义在 framework，各业务模块实现具体执行器

---

### 3.3 lightbot-agent（AI 能力核心）

**职责**：Agent 定义、Workflow 引擎、AI 对话、模型管理——AI 能力的核心模块。

```
com.lightbot.agent/
├── agent/               # Agent 管理
│   ├── AgentController.java
│   ├── AgentWorkflowController.java
│   ├── SubAgentController.java
│   ├── service/
│   │   ├── AgentService.java
│   │   ├── AgentVersionService.java
│   │   ├── SubAgentService.java
│   │   └── impl/
│   │       ├── AgentServiceImpl.java
│   │       ├── AgentVersionServiceImpl.java
│   │       └── SubAgentServiceImpl.java
│   ├── entity/
│   │   ├── Agent.java
│   │   ├── AgentVersion.java
│   │   └── SubAgent.java
│   └── mapper/
│       ├── AgentMapper.java
│       ├── AgentVersionMapper.java
│       └── SubAgentMapper.java
├── chat/                # 对话服务
│   ├── ChatController.java
│   ├── ChatSessionController.java
│   ├── service/
│   │   ├── ChatService.java
│   │   ├── ChatSessionService.java
│   │   ├── ChatAttachmentService.java
│   │   ├── MessageService.java
│   │   └── impl/
│   │       ├── ChatServiceImpl.java
│   │       ├── ChatSessionServiceImpl.java
│   │       ├── ChatAttachmentServiceImpl.java
│   │       └── MessageServiceImpl.java
│   ├── entity/
│   │   ├── ChatSession.java
│   │   ├── ChatAttachment.java
│   │   └── Message.java
│   └── mapper/
│       ├── ChatSessionMapper.java
│       ├── ChatAttachmentMapper.java
│       └── MessageMapper.java
├── workflow/            # Workflow 引擎
│   ├── WorkflowConfigServiceImpl.java
│   ├── GraphServiceImpl.java
│   ├── StandaloneGraphServiceImpl.java
│   ├── StandaloneGraphController.java
│   ├── executor/        # DAG 执行器
│   │   ├── WorkflowExecutorService.java
│   │   └── WorkflowConfigParser.java
│   ├── processor/       # 22 种节点处理器
│   │   ├── Node.java
│   │   ├── LlmNodeProcessor.java
│   │   ├── ToolNodeProcessor.java
│   │   ├── McpNodeProcessor.java
│   │   ├── RetrievalNodeProcessor.java
│   │   ├── ConditionNodeProcessor.java
│   │   ├── LoopNodeProcessor.java
│   │   ├── BatchNodeProcessor.java
│   │   ├── HttpRequestNodeProcessor.java
│   │   ├── ScriptNodeProcessor.java
│   │   ├── VariableNodeProcessor.java
│   │   ├── ClassifierNodeProcessor.java
│   │   ├── ParameterExtractorNodeProcessor.java
│   │   ├── QuestionClassifierNodeProcessor.java
│   │   ├── IterationNodeProcessor.java
│   │   ├── StartNodeProcessor.java
│   │   ├── EndNodeProcessor.java
│   │   ├── InputNodeProcessor.java
│   │   ├── OutputNodeProcessor.java
│   │   └── ...
│   ├── entity/
│   │   ├── WorkflowConfig.java
│   │   └── Graph.java
│   └── mapper/
│       ├── WorkflowConfigMapper.java
│       └── GraphMapper.java
├── ai/                  # AI 能力
│   ├── model/           # 模型工厂
│   │   ├── ModelFactory.java
│   │   └── ModelProvider.java
│   ├── chat/            # 对话引擎中间件链
│   │   ├── ChatMiddleware.java
│   │   ├── InitMiddleware.java
│   │   ├── ToolPrepMiddleware.java
│   │   ├── WorkflowMiddleware.java
│   │   ├── MemoryMiddleware.java
│   │   ├── SensitiveWordMiddleware.java
│   │   └── TraceMiddleware.java
│   └── rag/             # RAG 对话增强
│       └── RagChatEnhancer.java
├── model/               # 模型管理
│   ├── ModelController.java
│   ├── ModelProviderController.java
│   ├── service/
│   │   ├── ModelService.java
│   │   ├── ModelProviderService.java
│   │   └── impl/
│   │       ├── ModelServiceImpl.java
│   │       └── ModelProviderServiceImpl.java
│   ├── entity/
│   │   ├── Model.java
│   │   └── ModelProvider.java
│   └── mapper/
│       ├── ModelMapper.java
│       └── ModelProviderMapper.java
└── dto/                 # Agent 模块 DTO
    ├── AgentCreateDTO.java
    ├── ChatRequest.java
    ├── ChatResponse.java
    └── ...
```

**包含的 Entity**（12 个）：Agent、AgentVersion、SubAgent、ChatSession、ChatAttachment、Message、WorkflowConfig、Graph、Model、ModelProvider、...

**依赖**：lightbot-common, lightbot-framework（通过 TaskExecutor 接口提交异步任务）

**为什么 workflow 和 agent 放一起**：
1. workflow 是 Agent 的核心执行引擎，两者紧密耦合
2. Agent 的 systemPrompt 中可以配置 workflow，workflow 节点可以调用 LLM（Agent 核心能力）
3. 合并后模块内容丰富（Agent + Workflow + AI + 模型管理），不会碎片化
4. 参考 Spring AI Alibaba，Agent 和 Workflow 是同一个能力域

---

### 3.4 lightbot-extension（扩展体系）

**职责**：Agent 的能力扩展机制，包括工具、MCP、技能、子Agent、沙箱。

```
com.lightbot.extension/
├── tool/                # Tool 体系
│   ├── ToolController.java
│   ├── ToolCallController.java
│   ├── service/
│   │   ├── ToolService.java
│   │   ├── ToolCallService.java
│   │   └── impl/
│   │       ├── ToolServiceImpl.java
│   │       └── ToolCallServiceImpl.java
│   ├── builtin/         # 10 个内置工具
│   │   ├── AskUserTool.java
│   │   ├── CalculatorTool.java
│   │   ├── FindInDocumentTool.java
│   │   ├── ImageGenTool.java
│   │   ├── KnowledgeTools.java
│   │   ├── ListSkillFilesTool.java
│   │   ├── PgSqlTool.java
│   │   ├── QueryKnowledgeTool.java
│   │   ├── ReadSkillTool.java
│   │   └── WebSearchTool.java
│   ├── entity/
│   │   ├── Tool.java
│   │   └── ToolCall.java
│   └── mapper/
│       ├── ToolMapper.java
│       └── ToolCallMapper.java
├── mcp/                 # MCP 协议
│   ├── McpServerController.java
│   ├── service/
│   │   ├── McpServerService.java
│   │   ├── McpClientService.java
│   │   └── impl/
│   │       ├── McpServerServiceImpl.java
│   │       └── McpClientServiceImpl.java
│   ├── entity/
│   │   └── McpServer.java
│   └── mapper/
│       └── McpServerMapper.java
├── skill/               # Skill 运行时
│   ├── SkillController.java
│   ├── service/
│   │   ├── SkillService.java
│   │   └── impl/
│   │       └── SkillServiceImpl.java
│   ├── entity/
│   │   └── Skill.java
│   └── mapper/
│       └── SkillMapper.java
├── subagent/            # SubAgent 运行时
│   ├── SubAgentRuntime.java
│   └── DelegateSubAgentTool.java
└── sandbox/             # 沙箱服务
    ├── SkillStorageService.java
    └── SkillActivationStore.java
```

**包含的 Entity**（5 个）：Tool、ToolCall、McpServer、Skill、...

**依赖**：lightbot-common, lightbot-framework

**内置工具对其他模块的依赖**：
- `QueryKnowledgeTool`、`KnowledgeTools`、`FindInDocumentTool` → rag 模块的 KnowledgeService
- `ReadSkillTool` → 本模块的 SkillService

**解决方案**：内置工具通过接口调用 rag 模块，接口定义在 rag 模块中，extension 只依赖接口。

---

### 3.5 lightbot-rag（知识库模块）

**职责**：RAG 知识库全生命周期管理，包括知识库、文档、向量化、QA、图谱、检索。

```
com.lightbot.rag/
├── knowledge/           # 知识库管理
│   ├── KnowledgeController.java
│   ├── KnowledgeEvalController.java
│   ├── service/
│   │   ├── KnowledgeService.java          # 接口
│   │   ├── KnowledgeMemberService.java
│   │   ├── DocumentService.java
│   │   ├── DocumentVersionService.java
│   │   ├── DocumentEditService.java
│   │   ├── ChunkService.java
│   │   ├── EmbeddingService.java
│   │   ├── RagService.java
│   │   └── impl/
│   │       ├── KnowledgeServiceImpl.java
│   │       ├── KnowledgeMemberServiceImpl.java
│   │       ├── DocumentServiceImpl.java
│   │       ├── DocumentVersionServiceImpl.java
│   │       ├── DocumentEditServiceImpl.java
│   │       ├── ChunkServiceImpl.java
│   │       ├── EmbeddingServiceImpl.java
│   │       └── RagServiceImpl.java
│   ├── entity/
│   │   ├── Knowledge.java
│   │   ├── KnowledgeMember.java
│   │   ├── Document.java
│   │   ├── DocumentVersion.java
│   │   ├── DocumentEdit.java
│   │   ├── Chunk.java
│   │   └── Embedding.java
│   └── mapper/
│       ├── KnowledgeMapper.java
│       ├── DocumentMapper.java
│       ├── ChunkMapper.java
│       ├── EmbeddingMapper.java
│       └── ...
├── qa/                  # QA 对管理
│   ├── service/
│   │   ├── QaPairService.java
│   │   └── impl/
│   │       └── QaPairServiceImpl.java
│   ├── entity/
│   │   └── QaPair.java
│   └── mapper/
│       └── QaPairMapper.java
├── graph/               # 知识图谱
│   ├── service/
│   │   ├── GraphService.java
│   │   └── impl/
│   │       └── GraphServiceImpl.java
│   └── entity/
│       └── GraphEntity.java
├── ingest/              # 文档摄入
│   ├── IngestService.java
│   └── chunking/        # 分块策略
│       ├── ChunkingStrategy.java
│       ├── FixedSizeChunking.java
│       └── SemanticChunking.java
├── dto/
│   ├── KnowledgeCreateDTO.java
│   ├── DocumentUploadDTO.java
│   └── ...
└── config/
    └── Neo4jConfig.java
```

**包含的 Entity**（10 个）：Knowledge、KnowledgeMember、Document、DocumentVersion、DocumentEdit、Chunk、Embedding、QaPair、...

**依赖**：lightbot-common, lightbot-framework

**对外暴露的接口**：
- `KnowledgeService` — 被 extension（内置工具）、agent（WorkflowRetrievalNode）调用
- `EmbeddingService` — 被 extension（QueryKnowledgeTool）调用
- `DocumentService` — 被 extension（FindInDocumentTool）调用

---

### 3.6 lightbot-eval（评测体系）

**职责**：Prompt 工程 + LLM 评测，包括数据集、评估器、实验、评测结果。

```
com.lightbot.eval/
├── prompt/              # Prompt 工程
│   ├── PromptController.java
│   ├── service/
│   │   ├── PromptService.java
│   │   ├── PromptVersionService.java
│   │   ├── PromptBuildTemplateService.java
│   │   └── impl/
│   │       ├── PromptServiceImpl.java
│   │       ├── PromptVersionServiceImpl.java
│   │       └── PromptBuildTemplateServiceImpl.java
│   ├── entity/
│   │   ├── Prompt.java
│   │   ├── PromptVersion.java
│   │   └── PromptBuildTemplate.java
│   └── mapper/
│       ├── PromptMapper.java
│       ├── PromptVersionMapper.java
│       └── PromptBuildTemplateMapper.java
├── dataset/             # 评测数据集
│   ├── EvalDatasetController.java
│   ├── service/
│   │   ├── EvalDatasetService.java
│   │   ├── EvalDatasetItemService.java
│   │   ├── EvalDatasetVersionService.java
│   │   └── impl/
│   │       ├── EvalDatasetServiceImpl.java
│   │       ├── EvalDatasetItemServiceImpl.java
│   │       └── EvalDatasetVersionServiceImpl.java
│   ├── entity/
│   │   ├── EvalDataset.java
│   │   ├── EvalDatasetItem.java
│   │   └── EvalDatasetVersion.java
│   └── mapper/
│       ├── EvalDatasetMapper.java
│       ├── EvalDatasetItemMapper.java
│       └── EvalDatasetVersionMapper.java
├── evaluator/           # 评估器
│   ├── EvalEvaluatorController.java
│   ├── service/
│   │   ├── EvalEvaluatorService.java
│   │   ├── EvalEvaluatorTemplateService.java
│   │   ├── EvalEvaluatorVersionService.java
│   │   └── impl/
│   │       ├── EvalEvaluatorServiceImpl.java
│   │       ├── EvalEvaluatorTemplateServiceImpl.java
│   │       └── EvalEvaluatorVersionServiceImpl.java
│   ├── entity/
│   │   ├── EvalEvaluator.java
│   │   ├── EvalEvaluatorTemplate.java
│   │   └── EvalEvaluatorVersion.java
│   └── mapper/
│       ├── EvalEvaluatorMapper.java
│       ├── EvalEvaluatorTemplateMapper.java
│       └── EvalEvaluatorVersionMapper.java
├── experiment/          # 评测实验
│   ├── EvalExperimentController.java
│   ├── service/
│   │   ├── EvalExperimentService.java
│   │   ├── EvalExperimentResultService.java
│   │   ├── EvalChatService.java
│   │   └── impl/
│   │       ├── EvalExperimentServiceImpl.java
│   │       ├── EvalExperimentResultServiceImpl.java
│   │       └── EvalChatServiceImpl.java
│   ├── engine/          # 评测引擎
│   │   ├── EvalEngine.java
│   │   └── RagEvaluationEngine.java
│   ├── entity/
│   │   ├── EvalExperiment.java
│   │   └── EvalExperimentResult.java
│   └── mapper/
│       ├── EvalExperimentMapper.java
│       └── EvalExperimentResultMapper.java
├── benchmark/           # RAG Benchmark
│   ├── service/
│   │   ├── EvalRagBenchmarkService.java
│   │   ├── EvalRagResultService.java
│   │   └── impl/
│   │       ├── EvalRagBenchmarkServiceImpl.java
│   │       └── EvalRagResultServiceImpl.java
│   ├── entity/
│   │   ├── EvalRagBenchmark.java
│   │   └── EvalRagResult.java
│   └── mapper/
│       ├── EvalRagBenchmarkMapper.java
│       └── EvalRagResultMapper.java
└── dto/
    ├── EvalExperimentCreateDTO.java
    └── ...
```

**包含的 Entity**（11 个）：Prompt、PromptVersion、PromptBuildTemplate、EvalDataset、EvalDatasetItem、EvalDatasetVersion、EvalEvaluator、EvalEvaluatorTemplate、EvalEvaluatorVersion、EvalExperiment、EvalExperimentResult、EvalRagBenchmark、EvalRagResult

**依赖**：lightbot-common, lightbot-framework, lightbot-agent（通过 ModelFactory 调用 LLM）

**为什么 Prompt 放 eval**：
1. Prompt 管理主要服务于评测场景（评估器的 prompt 模板、实验的 prompt 版本）
2. 两者有天然关联：评测 = 数据集 + 评估器（含 prompt） + 实验
3. 合并后模块内容丰富（11 个 Entity + 11 个 Service）

**为什么 eval 独立于 rag**：
1. 评测是独立的能力域，不仅服务于 RAG，也服务于 Agent、Workflow 等
2. 评测有完整的闭环：数据集 → 评估器 → 实验 → 结果分析
3. 独立后可以独立演进，不被 RAG 模块的发展拖累

---

### 3.7 lightbot-monitor（监控模块）

**职责**：系统可观测性，包括 LLM 链路追踪、日志管理、数据统计。

```
com.lightbot.monitor/
├── trace/               # LLM 链路追踪
│   ├── LlmTraceController.java
│   ├── service/
│   │   ├── LlmTraceService.java
│   │   └── impl/
│   │       └── LlmTraceServiceImpl.java
│   ├── entity/
│   │   └── LlmTrace.java
│   └── mapper/
│       └── LlmTraceMapper.java
├── log/                 # 日志管理
│   ├── LogController.java
│   ├── service/
│   │   ├── OperationLogService.java
│   │   └── impl/
│   │       └── OperationLogServiceImpl.java
│   ├── entity/
│   │   └── OperationLog.java
│   └── mapper/
│       └── OperationLogMapper.java
├── dashboard/           # 数据统计
│   ├── DashboardController.java
│   ├── service/
│   │   ├── DashboardService.java
│   │   └── impl/
│   │       └── DashboardServiceImpl.java
│   └── entity/
│       └── DashboardStat.java
└── config/
    └── TraceAutoConfiguration.java
```

**包含的 Entity**（3 个）：LlmTrace、OperationLog、DashboardStat

**依赖**：lightbot-common, lightbot-framework

**为什么监控独立**：
1. 监控是横切关注点，被所有模块使用
2. 独立后可以独立扩展（如接入 Prometheus、Grafana）
3. 不依赖任何业务逻辑，只采集数据

---

### 3.8 lightbot-admin（主服务入口）

**职责**：Spring Boot 启动类 + Controller 聚合层，不含业务逻辑。

```
com.lightbot.admin/
├── LightBotApplication.java       # 启动类
├── handler/                       # 全局异常处理
│   └── GlobalExceptionHandler.java
├── config/                        # 启动配置
│   ├── WebMvcConfig.java
│   └── SwaggerConfig.java
└── doc/                           # API 文档
    └── LandingController.java
```

**依赖**：所有模块（lightbot-common, lightbot-framework, lightbot-agent, lightbot-extension, lightbot-rag, lightbot-eval, lightbot-monitor）

**注意**：admin 模块只包含启动类和全局配置，Controller 已分散到各业务模块。

---

## 四、Service 接口设计

### 4.1 接口归属原则

| 原则 | 说明 |
|------|------|
| **接口跟着业务走** | Service 接口定义在所属业务模块中，不放 common |
| **实现跟着接口走** | ServiceImpl 和接口在同一模块 |
| **跨模块通过接口调用** | 调用方只依赖接口，不依赖实现 |

### 4.2 跨模块调用清单

| 调用方模块 | 调用的接口 | 接口所在模块 | 调用场景 |
|-----------|-----------|-------------|---------|
| agent | KnowledgeService | rag | WorkflowRetrievalNode 知识检索 |
| agent | EmbeddingService | rag | WorkflowRetrievalNode 向量查询 |
| extension | KnowledgeService | rag | QueryKnowledgeTool 知识查询 |
| extension | DocumentService | rag | FindInDocumentTool 文档查找 |
| extension | QaPairService | rag | KnowledgeTools QA 查询 |
| agent | ToolService | extension | ToolPrepMiddleware 工具准备 |
| agent | McpClientService | extension | McpNodeProcessor MCP 调用 |
| agent | SkillService | extension | SkillPrepMiddleware 技能准备 |
| eval | ModelFactory | agent | EvalEngine 调用 LLM |
| eval | AgentService | agent | EvalChatService 获取 Agent 配置 |

### 4.3 接口定义示例

以 KnowledgeService 为例，接口定义在 rag 模块：

```java
// lightbot-rag 模块
package com.lightbot.rag.knowledge.service;

public interface KnowledgeService extends IService<Knowledge> {
    List<Knowledge> listByUserId(Long userId);
    Knowledge getById(Long id);
    // ...
}
```

调用方（agent、extension）通过 Maven 依赖 rag 模块获取接口：

```xml
<!-- lightbot-agent/pom.xml -->
<dependency>
    <groupId>com.lightbot</groupId>
    <artifactId>lightbot-rag</artifactId>
</dependency>
```

---

## 五、Entity / Mapper 归属

### 5.1 归属原则

| 原则 | 说明 |
|------|------|
| **Entity 跟着业务走** | 每个 Entity 属于其所属的业务模块 |
| **Mapper 跟着 Entity 走** | Mapper 和 Entity 在同一模块 |
| **共享表通过接口访问** | 多模块访问同一张表时，通过 Service 接口 |

### 5.2 Entity 分布

| 模块 | Entity 数量 | 主要 Entity |
|------|------------|------------|
| common | 0 | 无 |
| framework | 4 | User, SystemConfig, Task, DashboardStat |
| agent | 12 | Agent, AgentVersion, SubAgent, ChatSession, Message, WorkflowConfig, Graph, Model, ModelProvider |
| extension | 5 | Tool, ToolCall, McpServer, Skill, SubAgentConfig |
| rag | 10 | Knowledge, Document, DocumentVersion, Chunk, Embedding, QaPair |
| eval | 11 | Prompt, PromptVersion, EvalDataset, EvalEvaluator, EvalExperiment, EvalExperimentResult, EvalRagBenchmark, EvalRagResult |
| monitor | 3 | LlmTrace, OperationLog |

---

## 六、Task 框架设计

### 6.1 任务框架架构

任务框架放在 framework 模块，作为系统级基础设施：

```
framework/task/
├── TaskExecutor.java            # 执行器接口
├── TaskExecutorRegistry.java    # 执行器注册中心
├── TaskScheduler.java           # 任务调度器
└── executor/                    # 具体执行器
    ├── DocumentUploadExecutor.java      # 文档上传
    ├── DocumentIngestExecutor.java      # 文档入库
    ├── GraphExtractExecutor.java        # 知识图谱提取
    ├── QaGenerateExecutor.java          # QA 对生成
    ├── BenchmarkGenerateExecutor.java   # Benchmark 生成
    ├── BenchmarkImportExecutor.java     # Benchmark 导入
    ├── ExperimentRunExecutor.java       # 实验运行
    └── RagEvalExecutor.java            # RAG 评测
```

### 6.2 执行器注册机制

```java
// framework 模块定义接口
public interface TaskExecutor {
    String getType();
    void execute(Task task);
}

// 各业务模块实现执行器
@Component
public class DocumentIngestExecutor implements TaskExecutor {
    @Override
    public String getType() {
        return "DOCUMENT_INGEST";
    }

    @Override
    public void execute(Task task) {
        // 调用 rag 模块的 IngestService
    }
}
```

**执行器归属**：
- `DocumentUploadExecutor` → rag 模块
- `DocumentIngestExecutor` → rag 模块
- `GraphExtractExecutor` → rag 模块
- `QaGenerateExecutor` → rag 模块
- `BenchmarkGenerateExecutor` → eval 模块
- `BenchmarkImportExecutor` → eval 模块
- `ExperimentRunExecutor` → eval 模块
- `RagEvalExecutor` → eval 模块

**注意**：执行器接口定义在 framework，具体实现分散在各业务模块，通过 Spring 自动注册。

---

## 七、迁移策略

### 7.1 Phase 顺序

| Phase | 模块 | 工作量 | 说明 |
|-------|------|--------|------|
| Phase 1 | common | 3d | 抽离公共工具、枚举、DTO、异常 |
| Phase 2 | framework | 5d | 抽离认证、任务、日志、配置、中间件工具 |
| Phase 3 | agent | 8d | 合并 Agent + Workflow + AI + 模型管理 |
| Phase 4 | extension | 4d | 抽离 Tool + MCP + Skill + SubAgent |
| Phase 5 | rag | 5d | 抽离知识库 + QA + 图谱 + RAG |
| Phase 6 | eval | 5d | 抽离 Prompt + 评测 + 数据集 + 实验 |
| Phase 7 | monitor | 2d | 抽离 Trace + 日志 + Dashboard |
| Phase 8 | admin | 2d | 最终聚合，清理启动类 |
| **总计** | | **~34d** | |

### 7.2 Phase 1：common（公共包）

**迁移内容**：
- `result/` → Result, PageResult
- `exception/` → BizException, ErrorCode
- `enums/` → 所有全局枚举
- `dto/` → PageQuery 等公共 DTO
- `util/` → JsonUtil, AssertUtil, SecurityUtil（非中间件工具类）
- `constant/` → 公共常量
- `validation/` → 校验注解

**不迁移**：
- Entity（留在各业务模块）
- Mapper（留在各业务模块）
- Service 接口（留在各业务模块）
- 中间件工具类（放 framework）

### 7.3 Phase 2：framework（系统框架）

**迁移内容**：
- `controller/AuthController` + `service/UserServiceImpl` + `entity/User`
- `controller/SystemConfigController` + `service/SystemConfigServiceImpl` + `entity/SystemConfig`
- `controller/TaskController` + `controller/TaskEventController` + `service/TaskServiceImpl` + `entity/Task`
- `controller/LogController` + `log/LogService`
- `controller/DashboardController` + `service/DashboardServiceImpl`
- `util/MinioUtil`, `util/RedisUtil`, `util/OcrUtil`
- `config/MybatisPlusConfig`, `config/RedisConfig`, `config/MinioConfig`, `config/MilvusConfig`
- `task/executor/` → TaskExecutor 接口

**难点处理**：
- TaskExecutor 的具体实现分散在 rag、eval 模块 → Phase 5/6 迁移
- DashboardServiceImpl 可能查询多个模块的数据 → 通过接口调用

### 7.4 Phase 3：agent（AI 能力核心）

**迁移内容**：
- `service/AgentServiceImpl` + `service/AgentVersionServiceImpl` + `entity/Agent`, `entity/AgentVersion`
- `controller/ChatController` + `service/ChatServiceImpl` + `entity/ChatSession`, `entity/Message`
- `workflow/` 整个包（22 种节点处理器）
- `service/WorkflowConfigServiceImpl` + `entity/WorkflowConfig`
- `model/ModelFactory` + `model/ModelProvider`
- `service/ModelServiceImpl` + `service/ModelProviderServiceImpl` + `entity/Model`, `entity/ModelProvider`
- `service/chat/` 整个包（中间件链）

**难点处理**：
- `ToolPrepMiddleware` 依赖 `DelegateSubAgentTool` → 通过接口解耦
- `WorkflowRetrievalNode` 依赖 `KnowledgeService` → 通过 rag 模块的接口调用

### 7.5 Phase 4：extension（扩展体系）

**迁移内容**：
- `tool/` 整个包
- `skill/` 整个包
- `subagent/` 整个包
- `service/sandbox/` 整个包
- 相关 Entity 和 Mapper

### 7.6 Phase 5：rag（知识库模块）

**迁移内容**：
- 知识库相关 Service（Knowledge, Document, Chunk, Embedding, QA, Graph）
- 相关 Entity 和 Mapper
- `ingest/` 文档摄入
- `DocumentUploadExecutor`, `DocumentIngestExecutor`, `GraphExtractExecutor`, `QaGenerateExecutor`

### 7.7 Phase 6：eval（评测体系）

**迁移内容**：
- Prompt 相关 Service（Prompt, PromptVersion, PromptBuildTemplate）
- 评测相关 Service（EvalDataset, EvalEvaluator, EvalExperiment, EvalRagBenchmark）
- `EvalChatService` + `RagEvaluationEngine`
- 相关 Entity 和 Mapper
- `BenchmarkGenerateExecutor`, `BenchmarkImportExecutor`, `ExperimentRunExecutor`, `RagEvalExecutor`

### 7.8 Phase 7：monitor（监控模块）

**迁移内容**：
- `LlmTraceService` + `entity/LlmTrace`
- `LogService` + `entity/OperationLog`
- `DashboardService` + `entity/DashboardStat`

### 7.9 Phase 8：admin（最终聚合）

**迁移内容**：
- `LightBotApplication.java`
- `GlobalExceptionHandler.java`
- 启动配置类
- `LandingController.java`

---

## 八、包路径映射

| 原路径 | 新路径 |
|--------|--------|
| `com.lightbot.common.*` | `com.lightbot.common.*`（不变） |
| `com.lightbot.enums.*` | `com.lightbot.common.enums.*` |
| `com.lightbot.exception.*` | `com.lightbot.common.exception.*` |
| `com.lightbot.util.MinioUtil` | `com.lightbot.framework.util.MinioUtil` |
| `com.lightbot.util.RedisUtil` | `com.lightbot.framework.util.RedisUtil` |
| `com.lightbot.entity.User` | `com.lightbot.framework.auth.entity.User` |
| `com.lightbot.entity.SystemConfig` | `com.lightbot.framework.config.entity.SystemConfig` |
| `com.lightbot.entity.Task` | `com.lightbot.framework.task.entity.Task` |
| `com.lightbot.controller.AuthController` | `com.lightbot.framework.auth.AuthController` |
| `com.lightbot.controller.TaskController` | `com.lightbot.framework.task.TaskController` |
| `com.lightbot.entity.Agent` | `com.lightbot.agent.agent.entity.Agent` |
| `com.lightbot.service.AgentService` | `com.lightbot.agent.agent.service.AgentService` |
| `com.lightbot.workflow.*` | `com.lightbot.agent.workflow.*` |
| `com.lightbot.model.*` | `com.lightbot.agent.ai.model.*` |
| `com.lightbot.service.chat.*` | `com.lightbot.agent.ai.chat.*` |
| `com.lightbot.tool.*` | `com.lightbot.extension.tool.*` |
| `com.lightbot.skill.*` | `com.lightbot.extension.skill.*` |
| `com.lightbot.subagent.*` | `com.lightbot.extension.subagent.*` |
| `com.lightbot.entity.Knowledge` | `com.lightbot.rag.knowledge.entity.Knowledge` |
| `com.lightbot.service.KnowledgeService` | `com.lightbot.rag.knowledge.service.KnowledgeService` |
| `com.lightbot.entity.Prompt` | `com.lightbot.eval.prompt.entity.Prompt` |
| `com.lightbot.entity.EvalDataset` | `com.lightbot.eval.dataset.entity.EvalDataset` |
| `com.lightbot.entity.LlmTrace` | `com.lightbot.monitor.trace.entity.LlmTrace` |

---

## 九、风险与应对

### 9.1 循环依赖风险

**场景**：agent 模块的 `ToolPrepMiddleware` 需要调用 extension 模块的 `DelegateSubAgentTool`。

**应对**：
- 在 agent 模块定义 `SubAgentDelegate` 接口
- extension 模块实现 `DelegateSubAgentTool implements SubAgentDelegate`
- agent 通过接口调用，运行时注入实现

### 9.2 跨模块事务

**场景**：创建 Agent 时同时创建工作流配置，需要事务保证。

**应对**：
- 事务注解放在 agent 模块的 Service 实现中
- Agent 和 WorkflowConfig 都在 agent 模块，可以使用本地事务
- 如果涉及跨模块（如同时创建 Agent 和关联的 Tool），使用 Spring 事务传播机制

### 9.3 Maven 依赖管理

**场景**：模块间依赖可能导致版本冲突。

**应对**：
- 父 pom 统一管理所有依赖版本
- 使用 `<dependencyManagement>` 集中管理
- 每个模块只声明自己需要的依赖

### 9.4 前端无感知

模块化只影响后端内部结构：
- Controller URL 路径不变
- 请求/响应格式不变
- 前端零改动

---

## 十、决策记录

| 问题 | 决策 | 理由 |
|------|------|------|
| workflow 放哪里？ | **放 agent** | workflow 是 Agent 的核心执行引擎，合并后内容丰富，避免碎片化 |
| ai 要单独拆吗？ | **否，放 agent** | ModelFactory 和对话引擎是 Agent 核心能力，合并更合理 |
| Prompt 放哪里？ | **放 eval** | Prompt 主要服务于评测场景，两者有天然关联 |
| 评测独立还是放 rag？ | **独立 eval** | 评测是独立能力域，不仅服务 RAG，也服务 Agent/Workflow |
| 系统管理放哪里？ | **放 framework** | 认证、配置、任务是系统级基础设施 |
| 监控要独立吗？ | **是，独立 monitor** | 监控是横切关注点，独立后可独立扩展 |
| common 放 Service 接口？ | **否，接口跟着业务走** | 避免 common 膨胀，各模块自包含 |
| Entity 放哪里？ | **跟着业务走** | 每个 Entity 属于其业务模块，通过接口跨模块访问 |
| 任务框架放哪里？ | **放 framework** | 任务调度是系统级基础设施，被多模块使用 |

---

## 十一、最终架构总结

```
lightbot-admin (Spring Boot 入口)
  ├── LightBotApplication.java
  └── GlobalExceptionHandler.java

lightbot-monitor (监控)
  ├── trace/ (LLM 链路追踪)
  ├── log/ (日志管理)
  └── dashboard/ (数据统计)

lightbot-eval (评测体系)
  ├── prompt/ (Prompt 工程)
  ├── dataset/ (评测数据集)
  ├── evaluator/ (评估器)
  ├── experiment/ (评测实验)
  └── benchmark/ (RAG Benchmark)

lightbot-rag (知识库)
  ├── knowledge/ (知识库管理)
  ├── qa/ (QA 对)
  ├── graph/ (知识图谱)
  └── ingest/ (文档摄入)

lightbot-extension (扩展体系)
  ├── tool/ (Tool + 内置工具)
  ├── mcp/ (MCP 协议)
  ├── skill/ (Skill 运行时)
  ├── subagent/ (SubAgent)
  └── sandbox/ (沙箱)

lightbot-agent (AI 能力核心)
  ├── agent/ (Agent 管理)
  ├── chat/ (对话服务)
  ├── workflow/ (Workflow 引擎)
  ├── ai/ (AI 能力 + 模型工厂)
  └── model/ (模型管理)

lightbot-framework (系统框架)
  ├── auth/ (认证授权)
  ├── config/ (系统配置)
  ├── task/ (任务框架)
  ├── log/ (日志基础设施)
  ├── dashboard/ (数据统计)
  └── util/ (中间件工具类)

lightbot-common (公共包)
  ├── result/ (统一返回)
  ├── exception/ (异常定义)
  ├── enums/ (全局枚举)
  ├── dto/ (公共 DTO)
  ├── util/ (工具类)
  └── constant/ (常量)
```

**8 个模块，单向依赖，无环设计。**
