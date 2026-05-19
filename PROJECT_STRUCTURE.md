# LightBot - Monorepo 项目结构

> 轻量级 Java AI Agent 平台
>
> Tech Stack: SpringBoot + SpringAI + Vue3

---

## 顶层结构

```
LightBot/
├── lightbot-backend/                  # 后端主工程
│   ├── pom.xml                        # 父 POM
│   ├── lightbot-common/               # 公共模块
│   ├── lightbot-ai/                   # AI Framework
│   ├── lightbot-agent/                # Agent 引擎
│   ├── lightbot-workflow/             # Workflow 引擎
│   ├── lightbot-tool/                 # Tool 体系
│   ├── lightbot-mcp/                  # MCP 协议
│   ├── lightbot-plugin/               # Plugin 体系
│   ├── lightbot-rag/                  # RAG / Knowledge Base
│   ├── lightbot-server/               # 主服务入口
│   └── lightbot-api/                  # 对外 API 定义
│
├── lightbot-frontend/                 # 前端主工程
├── lightbot-docs/                     # 项目文档
├── lightbot-deploy/                   # 部署配置
├── ROADMAP.md
├── CLAUDE.md
└── PROJECT_STRUCTURE.md
```

---

## 后端模块

### lightbot-common

**职责**：公共基础设施，零业务逻辑

```
lightbot-common/
├── pom.xml
└── src/main/java/com/lightbot/common/
    ├── constant/                      # 全局常量
    │   ├── CommonConstant.java
    │   └── CacheKeyConstant.java
    ├── enums/                         # 公共枚举
    │   ├── StatusEnum.java
    │   └── YesNoEnum.java
    ├── exception/                     # 异常体系
    │   ├── BizException.java          # 业务异常
    │   ├── SysException.java          # 系统异常
    │   └── ErrorCode.java             # 错误码枚举
    ├── model/                         # 公共模型
    │   ├── Result.java                # 统一返回
    │   ├── PageQuery.java             # 分页查询
    │   └── PageVO.java                # 分页返回
    ├── util/                          # 工具类
    │   ├── JsonUtil.java
    │   ├── AssertUtil.java
    │   └── ThreadLocalUtil.java
    ├── config/                        # 公共配置
    │   ├── JacksonConfig.java
    │   └── ValidatorConfig.java
    └── log/                           # 日志增强
        └── LogAspect.java
```

**依赖**：无（最底层模块）

---

### lightbot-ai

**职责**：AI 能力抽象层，统一模型调用接口

```
lightbot-ai/
├── pom.xml
└── src/main/java/com/lightbot/ai/
    ├── client/                        # 模型客户端抽象
    │   ├── LlmClient.java            # LLM 统一接口
    │   ├── LlmClientFactory.java     # 客户端工厂
    │   └── impl/
    │       ├── OpenAiClient.java
    │       ├── QwenClient.java
    │       └── DeepSeekClient.java
    ├── model/                         # AI 模型定义
    │   ├── ChatModel.java            # 聊天模型
    │   ├── EmbeddingModel.java       # 向量模型
    │   └── ImageModel.java           # 图片模型（预留）
    ├── prompt/                        # Prompt 管理
    │   ├── PromptTemplate.java       # Prompt 模板
    │   ├── PromptManager.java        # Prompt 管理器
    │   └── PromptVariable.java       # 变量定义
    ├── memory/                        # 记忆抽象
    │   ├── Memory.java               # 记忆接口
    │   ├── WindowMemory.java         # 滑动窗口记忆
    │   └── SummaryMemory.java        # 摘要记忆（预留）
    ├── config/                        # AI 配置
    │   ├── AiProperties.java
    │   ├── ModelConfig.java
    │   └── SpringAiConfig.java
    └── constant/                      # AI 常量
        ├── ModelConstant.java
        └── RoleConstant.java
```

**依赖**：lightbot-common

---

### lightbot-tool

**职责**：Tool 定义、注册、执行

```
lightbot-tool/
├── pom.xml
└── src/main/java/com/lightbot/tool/
    ├── core/                          # Tool 核心
    │   ├── Tool.java                  # Tool 接口
    │   ├── ToolInput.java             # 输入定义
    │   ├── ToolResult.java            # 输出定义
    │   └── ToolDefinition.java        # 元数据定义
    ├── registry/                      # Tool 注册
    │   ├── ToolRegistry.java          # 注册中心
    │   ├── ToolScanner.java           # 自动扫描
    │   └── ToolRouter.java            # 路由分发
    ├── executor/                      # Tool 执行
    │   ├── ToolExecutor.java          # 执行器
    │   ├── ToolChainExecutor.java     # 链式执行
    │   └── ToolContext.java           # 执行上下文
    ├── builtin/                       # 内置 Tool
    │   ├── HttpRequestTool.java
    │   ├── JsonParseTool.java
    │   ├── DateTimeTool.java
    │   └── FileReadTool.java
    └── config/                        # Tool 配置
        └── ToolProperties.java
```

**依赖**：lightbot-common

---

### lightbot-mcp

**职责**：MCP（Model Context Protocol）协议实现

```
lightbot-mcp/
├── pom.xml
└── src/main/java/com/lightbot/mcp/
    ├── server/                        # MCP Server
    │   ├── McpServer.java            # 服务端实现
    │   ├── McpServerManager.java     # 服务管理
    │   └── McpServerConfig.java      # 服务配置
    ├── client/                        # MCP Client
    │   ├── McpClient.java            # 客户端实现
    │   └── McpClientPool.java        # 连接池
    ├── protocol/                      # 协议定义
    │   ├── McpRequest.java
    │   ├── McpResponse.java
    │   ├── McpMethod.java            # 方法枚举
    │   └── McpResource.java          # 资源定义
    ├── handler/                       # 请求处理
    │   ├── RequestHandler.java
    │   ├── ToolHandler.java
    │   └── ResourceHandler.java
    └── transport/                     # 传输层
        ├── Transport.java            # 传输接口
        ├── StdioTransport.java       # 标准输入输出
        └── SseTransport.java         # SSE 传输
```

**依赖**：lightbot-common, lightbot-tool

---

### lightbot-agent

**职责**：Agent 定义、运行时、生命周期管理

```
lightbot-agent/
├── pom.xml
└── src/main/java/com/lightbot/agent/
    ├── core/                          # Agent 核心
    │   ├── Agent.java                 # Agent 接口
    │   ├── AgentDefinition.java       # Agent 定义
    │   ├── AgentConfig.java           # Agent 配置
    │   └── AgentState.java            # Agent 状态
    ├── runtime/                       # 运行时
    │   ├── AgentRuntime.java          # 运行时引擎
    │   ├── AgentExecutor.java         # 执行器
    │   ├── AgentContext.java          # 运行上下文
    │   └── AgentCallback.java         # 回调钩子
    ├── chain/                         # 推理链
    │   ├── Chain.java                 # 推理链接口
    │   ├── ReActChain.java           # ReAct 推理
    │   └── PlanAndExecuteChain.java   # 计划执行（预留）
    ├── planner/                       # 规划器（预留）
    │   ├── Planner.java
    │   └── StepPlanner.java
    ├── session/                       # 会话管理
    │   ├── Session.java
    │   ├── SessionManager.java
    │   └── Conversation.java
    ├── handler/                       # 响应处理
    │   ├── ResponseParser.java
    │   ├── ToolCallParser.java
    │   └── StreamHandler.java
    ├── facade/                        # 对外接口
    │   └── AgentFacade.java
    ├── service/                       # 业务服务
    │   ├── AgentService.java
    │   └── impl/AgentServiceImpl.java
    ├── repository/                    # 数据访问
    │   └── AgentRepository.java
    └── config/                        # 配置
        └── AgentProperties.java
```

**依赖**：lightbot-common, lightbot-ai, lightbot-tool

---

### lightbot-workflow

**职责**：Workflow DAG 引擎、节点编排、执行调度

```
lightbot-workflow/
├── pom.xml
└── src/main/java/com/lightbot/workflow/
    ├── engine/                        # 执行引擎
    │   ├── WorkflowEngine.java        # 引擎接口
    │   ├── DagExecutor.java           # DAG 执行器
    │   ├── NodeScheduler.java         # 节点调度
    │   └── ParallelExecutor.java      # 并行执行
    ├── graph/                         # 图结构
    │   ├── WorkflowGraph.java         # 工作流图
    │   ├── Edge.java                  # 边
    │   └── TopoSort.java              # 拓扑排序
    ├── node/                          # 节点定义
    │   ├── Node.java                  # 节点接口
    │   ├── NodeType.java              # 节点类型枚举
    │   ├── NodeDefinition.java        # 节点定义
    │   ├── NodeResult.java            # 节点结果
    │   └── NodeContext.java           # 节点上下文
    ├── node/impl/                     # 内置节点
    │   ├── LlmNode.java               # LLM 节点
    │   ├── ToolNode.java              # Tool 节点
    │   ├── ConditionNode.java         # 条件节点
    │   ├── CodeNode.java              # 代码节点
    │   ├── StartNode.java             # 开始节点
    │   └── EndNode.java               # 结束节点
    ├── state/                         # 状态管理
    │   ├── WorkflowState.java         # 工作流状态
    │   ├── NodeState.java             # 节点状态
    │   └── StateStore.java            # 状态存储
    ├── variable/                      # 变量管理
    │   ├── VariableStore.java         # 变量存储
    │   └── VariableResolver.java      # 变量解析
    ├── facade/                        # 对外接口
    │   └── WorkflowFacade.java
    ├── service/                       # 业务服务
    │   ├── WorkflowService.java
    │   └── impl/WorkflowServiceImpl.java
    ├── repository/                    # 数据访问
    │   └── WorkflowRepository.java
    └── config/                        # 配置
        └── WorkflowProperties.java
```

**依赖**：lightbot-common, lightbot-ai, lightbot-tool

---

### lightbot-rag

**职责**：RAG 知识库、文档处理、向量检索

```
lightbot-rag/
├── pom.xml
└── src/main/java/com/lightbot/rag/
    ├── document/                      # 文档处理
    │   ├── Document.java              # 文档模型
    │   ├── DocumentParser.java        # 解析器接口
    │   ├── TextParser.java            # 纯文本解析
    │   ├── MarkdownParser.java        # Markdown 解析
    │   └── PdfParser.java             # PDF 解析（预留）
    ├── splitter/                      # 文本分割
    │   ├── TextSplitter.java          # 分割器接口
    │   ├── TokenSplitter.java         # 按 Token 分割
    │   └── SentenceSplitter.java      # 按句子分割
    ├── embedding/                     # 向量化
    │   ├── EmbeddingService.java      # 向量服务
    │   └── EmbeddingCache.java        # 向量缓存
    ├── vectorstore/                   # 向量存储
    │   ├── VectorStore.java           # 存储接口
    │   ├── PgVectorStore.java         # PgVector 实现
    │   └── MilvusVectorStore.java     # Milvus 实现（预留）
    ├── retriever/                     # 检索器
    │   ├── Retriever.java             # 检索接口
    │   ├── VectorRetriever.java       # 向量检索
    │   └── HybridRetriever.java       # 混合检索（预留）
    ├── knowledge/                     # 知识库管理
    │   ├── KnowledgeBase.java         # 知识库模型
    │   ├── KnowledgeService.java      # 知识库服务
    │   └── KnowledgeQuery.java        # 查询模型
    ├── facade/                        # 对外接口
    │   └── RagFacade.java
    ├── service/                       # 业务服务
    │   ├── RagService.java
    │   └── impl/RagServiceImpl.java
    ├── repository/                    # 数据访问
    │   ├── KnowledgeRepository.java
    │   └── DocumentRepository.java
    └── config/                        # 配置
        └── RagProperties.java
```

**依赖**：lightbot-common, lightbot-ai

---

### lightbot-plugin

**职责**：插件体系、扩展点、插件生命周期

```
lightbot-plugin/
├── pom.xml
└── src/main/java/com/lightbot/plugin/
    ├── core/                          # 插件核心
    │   ├── Plugin.java                # 插件接口
    │   ├── PluginContext.java         # 插件上下文
    │   ├── PluginConfig.java          # 插件配置
    │   └── PluginState.java           # 插件状态
    ├── lifecycle/                     # 生命周期
    │   ├── PluginLifecycle.java       # 生命周期接口
    │   ├── PluginLoader.java          # 插件加载
    │   └── PluginManager.java         # 插件管理
    ├── extension/                     # 扩展点
    │   ├── ExtensionPoint.java        # 扩展点定义
    │   ├── ExtensionRegistry.java     # 扩展注册
    │   └── ExtensionInvoker.java      # 扩展调用
    ├── event/                         # 事件机制
    │   ├── PluginEvent.java
    │   └── PluginEventPublisher.java
    ├── builtin/                       # 内置插件
    │   ├── LoggingPlugin.java
    │   └── AuthPlugin.java
    └── config/                        # 配置
        └── PluginProperties.java
```

**依赖**：lightbot-common

---

### lightbot-api

**职责**：对外 API 定义、DTO、Feign 接口

```
lightbot-api/
├── pom.xml
└── src/main/java/com/lightbot/api/
    ├── agent/                         # Agent API
    │   ├── AgentApi.java              # 接口定义
    │   ├── dto/
    │   │   ├── AgentCreateDTO.java
    │   │   ├── AgentUpdateDTO.java
    │   │   └── AgentQueryDTO.java
    │   └── vo/
    │       ├── AgentVO.java
    │       └── AgentDetailVO.java
    ├── workflow/                      # Workflow API
    │   ├── WorkflowApi.java
    │   ├── dto/
    │   └── vo/
    ├── tool/                          # Tool API
    │   ├── ToolApi.java
    │   ├── dto/
    │   └── vo/
    ├── knowledge/                     # Knowledge API
    │   ├── KnowledgeApi.java
    │   ├── dto/
    │   └── vo/
    └── chat/                          # Chat API
        ├── ChatApi.java
        ├── dto/
        └── vo/
```

**依赖**：lightbot-common

---

### lightbot-server

**职责**：主服务入口、Controller、配置聚合

```
lightbot-server/
├── pom.xml
├── src/main/java/com/lightbot/server/
│   ├── LightBotApplication.java      # 启动类
│   ├── controller/                    # 接口层
│   │   ├── agent/
│   │   │   └── AgentController.java
│   │   ├── workflow/
│   │   │   └── WorkflowController.java
│   │   ├── tool/
│   │   │   └── ToolController.java
│   │   ├── knowledge/
│   │   │   └── KnowledgeController.java
│   │   ├── chat/
│   │   │   └── ChatController.java
│   │   └── mcp/
│   │       └── McpController.java
│   ├── config/                        # 服务配置
│   │   ├── WebConfig.java
│   │   ├── SecurityConfig.java
│   │   └── AiConfig.java
│   └── handler/                       # 全局处理
│       ├── GlobalExceptionHandler.java
│       └── ResponseAdvice.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/                  # Flyway 迁移
│       ├── V0.1__init_schema.sql
│       └── V0.2__add_workflow.sql
└── src/test/
```

**依赖**：所有业务模块

---

## 后端依赖关系

```
                    ┌─────────────┐
                    │   server    │ ← 入口，聚合所有模块
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
   ┌─────────┐      ┌──────────┐      ┌──────────┐
   │  agent  │      │ workflow │      │   rag    │
   └────┬────┘      └────┬─────┘      └────┬─────┘
        │                │                  │
        ├────────────────┤                  │
        ▼                ▼                  ▼
   ┌─────────┐      ┌──────────┐      ┌──────────┐
   │   ai    │      │   tool   │      │   ai     │
   └────┬────┘      └────┬─────┘      └────┬─────┘
        │                │                  │
        └────────────────┼──────────────────┘
                         ▼
                   ┌──────────┐
                   │  common  │ ← 最底层
                   └──────────┘

   ┌─────────┐      ┌──────────┐
   │   mcp   │ ──── │   tool   │
   └─────────┘      └──────────┘

   ┌──────────┐
   │  plugin  │ ← 独立扩展层
   └──────────┘
```

**依赖矩阵**：

| 模块 | common | ai | tool | mcp | plugin |
|------|--------|----|----|-----|--------|
| common | - | | | | |
| ai | ✅ | - | | | |
| tool | ✅ | | - | | |
| mcp | ✅ | | ✅ | - | |
| agent | ✅ | ✅ | ✅ | | |
| workflow | ✅ | ✅ | ✅ | | |
| rag | ✅ | ✅ | | | |
| plugin | ✅ | | | | - |
| api | ✅ | | | | |
| server | ✅ | ✅ | ✅ | ✅ | ✅ |

---

## 前端模块

### lightbot-frontend

**职责**：Vue3 前端主应用

```
lightbot-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── index.html
│
├── src/
│   ├── main.ts                        # 入口
│   ├── App.vue                        # 根组件
│   │
│   ├── api/                           # API 请求层
│   │   ├── request.ts                 # Axios 封装
│   │   ├── agent.ts                   # Agent API
│   │   ├── workflow.ts                # Workflow API
│   │   ├── tool.ts                    # Tool API
│   │   ├── knowledge.ts               # Knowledge API
│   │   └── chat.ts                    # Chat API
│   │
│   ├── assets/                        # 静态资源
│   │   ├── icons/
│   │   └── images/
│   │
│   ├── components/                    # 公共组件
│   │   ├── common/                    # 通用组件
│   │   │   ├── AppHeader.vue
│   │   │   ├── AppSidebar.vue
│   │   │   ├── AppLayout.vue
│   │   │   └── AppBreadcrumb.vue
│   │   ├── ui/                        # UI 组件
│   │   │   ├── BaseButton.vue
│   │   │   ├── BaseInput.vue
│   │   │   ├── BaseModal.vue
│   │   │   ├── BaseTable.vue
│   │   │   ├── BaseForm.vue
│   │   │   └── BaseCard.vue
│   │   └── business/                  # 业务组件
│   │       ├── ChatBox.vue            # 对话框
│   │       ├── MessageBubble.vue      # 消息气泡
│   │       ├── ToolCard.vue           # Tool 卡片
│   │       └── ModelSelector.vue      # 模型选择器
│   │
│   ├── composables/                   # 组合函数
│   │   ├── useChat.ts                 # 对话逻辑
│   │   ├── useSSE.ts                  # SSE 流式
│   │   ├── useAgent.ts                # Agent 操作
│   │   ├── useWorkflow.ts             # Workflow 操作
│   │   └── useKnowledge.ts            # 知识库操作
│   │
│   ├── stores/                        # Pinia 状态管理
│   │   ├── index.ts
│   │   ├── app.ts                     # 应用状态
│   │   ├── user.ts                    # 用户状态
│   │   ├── chat.ts                    # 对话状态
│   │   └── workflow.ts                # Workflow 状态
│   │
│   ├── views/                         # 页面视图
│   │   ├── home/
│   │   │   └── index.vue              # 首页
│   │   ├── chat/
│   │   │   ├── index.vue              # 对话页
│   │   │   └── components/
│   │   │       ├── ChatPanel.vue
│   │   │       └── ChatHistory.vue
│   │   ├── agent/
│   │   │   ├── index.vue              # Agent 列表
│   │   │   ├── detail.vue             # Agent 详情
│   │   │   └── components/
│   │   │       ├── AgentForm.vue
│   │   │       ├── AgentTest.vue
│   │   │       └── AgentToolConfig.vue
│   │   ├── workflow/
│   │   │   ├── index.vue              # Workflow 列表
│   │   │   ├── editor.vue             # Workflow 编辑器
│   │   │   └── components/
│   │   │       ├── WorkflowCanvas.vue # 画布（Vue Flow）
│   │   │       ├── NodePanel.vue      # 节点面板
│   │   │       ├── NodeConfig.vue     # 节点配置
│   │   │       ├── VariablePanel.vue  # 变量面板
│   │   │       └── RunLog.vue         # 运行日志
│   │   ├── tool/
│   │   │   ├── index.vue              # Tool 列表
│   │   │   ├── detail.vue             # Tool 详情
│   │   │   └── components/
│   │   │       ├── ToolForm.vue
│   │   │       └── ToolTest.vue
│   │   ├── knowledge/
│   │   │   ├── index.vue              # 知识库列表
│   │   │   ├── detail.vue             # 知识库详情
│   │   │   └── components/
│   │   │       ├── DocumentUpload.vue
│   │   │       ├── DocumentList.vue
│   │   │       └── RetrievalTest.vue
│   │   └── settings/
│   │       ├── index.vue              # 设置页
│   │       ├── ModelConfig.vue
│   │       └── ApiKeyConfig.vue
│   │
│   ├── router/                        # 路由配置
│   │   ├── index.ts
│   │   └── routes.ts
│   │
│   ├── styles/                        # 全局样式
│   │   ├── index.css
│   │   └── variables.css
│   │
│   └── utils/                         # 工具函数
│       ├── format.ts
│       ├── validate.ts
│       └── storage.ts
│
├── public/
├── .env
├── .env.development
└── .env.production
```

**技术选型**：

| 类别 | 选型 |
|------|------|
| 框架 | Vue 3 + TypeScript |
| 构建 | Vite |
| 状态管理 | Pinia |
| 路由 | Vue Router |
| UI 组件库 | Element Plus / Ant Design Vue |
| Workflow 画布 | Vue Flow |
| HTTP | Axios |
| 样式 | Tailwind CSS |
| Markdown | markdown-it |
| 代码高亮 | highlight.js |

---

## 部署配置

```
lightbot-deploy/
├── docker/
│   ├── Dockerfile.backend              # 后端镜像
│   ├── Dockerfile.frontend             # 前端镜像
│   └── nginx.conf                      # Nginx 配置
├── docker-compose.yml                  # 开发环境
├── docker-compose.prod.yml             # 生产环境
├── scripts/
│   ├── init-db.sh                      # 数据库初始化
│   └── deploy.sh                       # 部署脚本
└── config/
    ├── application-docker.yml          # Docker 配置
    └── application-prod.yml            # 生产配置
```

---

## 项目文档

```
lightbot-docs/
├── architecture/                       # 架构文档
│   ├── overview.md                     # 架构概览
│   └── module-design.md                # 模块设计
├── api/                                # API 文档
│   └── openapi.yaml                    # OpenAPI 规范
├── guides/                             # 开发指南
│   ├── getting-started.md
│   ├── development.md
│   └── deployment.md
└── design/                             # 设计文档
    ├── workflow-design.md
    └── agent-design.md
```

---

## 模块职责总览

| 模块 | 职责 | 核心类 |
|------|------|--------|
| **common** | 基础设施、公共工具 | Result, BizException, JsonUtil |
| **ai** | AI 能力抽象、模型调用 | LlmClient, PromptTemplate, Memory |
| **tool** | Tool 注册、执行、路由 | Tool, ToolRegistry, ToolExecutor |
| **mcp** | MCP 协议实现 | McpServer, McpClient |
| **agent** | Agent 定义、推理、运行时 | Agent, AgentRuntime, ReActChain |
| **workflow** | DAG 引擎、节点编排 | WorkflowEngine, Node, DagExecutor |
| **rag** | 文档处理、向量检索 | Document, VectorStore, Retriever |
| **plugin** | 插件体系、扩展点 | Plugin, ExtensionPoint |
| **api** | 对外 API 定义 | AgentApi, WorkflowApi |
| **server** | 服务入口、Controller | AgentController, ChatController |
