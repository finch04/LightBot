# LightBot Roadmap

> 轻量级 Java AI Agent 平台
>
> Tech Stack: SpringBoot 3.3.6 + SpringAI 1.0.0 + Vue3 + PostgreSQL 15 + pgvector

---

## 项目概览

LightBot 是一个面向 AI 场景的全栈 Agent 平台，支持对话、工作流编排、RAG 知识库、工具调用、MCP 协议等核心能力。

**架构特点：**
- 单体架构，渐进式演进
- AI Native，围绕 AI 能力设计
- Java First，后端统一 Java
- 模块化，边界清晰

---

## 一、已完成功能

### 1.1 用户体系

| 功能 | 状态 | 说明 |
|------|------|------|
| 用户注册/登录 | ✅ | 用户名+密码，Sa-Token 鉴权 |
| 用户管理 | ✅ | 管理员 CRUD，角色/状态管理 |
| 个人资料 | ✅ | 昵称/头像/密码修改 |
| 健康检查 | ✅ | 免鉴权端点 |

### 1.2 模型提供商

| 功能 | 状态 | 说明 |
|------|------|------|
| 多提供商支持 | ✅ | DashScope/OpenAI/DeepSeek/Ollama |
| 动态模型路由 | ✅ | ModelFactory 按 providerId 动态创建 ChatModel |
| API Key 加密 | ✅ | 敏感信息加密存储 |
| 模型缓存 | ✅ | Redis 缓存模型配置 |

### 1.3 AI 对话

| 功能 | 状态 | 说明 |
|------|------|------|
| 流式对话 | ✅ | SSE 流式输出，支持中断恢复 |
| 会话管理 | ✅ | 创建/查询/归档/标题异步生成 |
| 消息历史 | ✅ | 自动加载最近 20 条历史 |
| 文件附件 | ✅ | 图片/文件上传，多模态对话 |
| 对话缓存 | ✅ | 会话列表 Redis 缓存 |

### 1.4 Agent 体系

| 功能 | 状态 | 说明 |
|------|------|------|
| Agent CRUD | ✅ | 创建/编辑/删除/复制 |
| Agent 类型 | ✅ | 对话型/工作流型 |
| 系统提示词 | ✅ | 支持 AI 生成 |
| 推荐问题 | ✅ | 支持 AI 生成 |
| 版本管理 | ✅ | 发布/预览/恢复历史版本 |
| 默认 Agent | ✅ | 系统级默认 Agent |
| 头像上传 | ✅ | MinIO 存储 |

### 1.5 工作流引擎

| 功能 | 状态 | 说明 |
|------|------|------|
| DAG 执行 | ✅ | 串行/条件分支执行 |
| 可视化编排 | ✅ | Vue Flow 画布 |
| 节点类型 | ✅ | LLM/Tool/Condition/Variable/Knowledge/Code/Start/End |
| 变量透传 | ✅ | `{{nodeId.output}}` 语法 |
| 18 种节点 | ✅ | 完整节点库 |
| 工作流示例 | ✅ | 内置多种模板 |

### 1.6 Tool 体系

| 功能 | 状态 | 说明 |
|------|------|------|
| Tool CRUD | ✅ | 创建/编辑/删除 |
| Tool 类型 | ✅ | HTTP/Code/内置 |
| 认证支持 | ✅ | None/API Key/Bearer |
| 标签管理 | ✅ | Tool 分类标签 |
| 输入输出 Schema | ✅ | JSON Schema 定义 |

### 1.7 MCP 协议

| 功能 | 状态 | 说明 |
|------|------|------|
| MCP Client | ✅ | 连接外部 MCP Server |
| 传输方式 | ✅ | stdio/SSE/Streamable HTTP |
| Tool 适配 | ✅ | MCPToolAdapter 统一注册 |
| 服务管理 | ✅ | 安装/卸载/启用/禁用 |
| 全局搜索 | ✅ | npx skills find 集成 |

### 1.8 Skill 体系

| 功能 | 状态 | 说明 |
|------|------|------|
| Skill CRUD | ✅ | 创建/编辑/删除 |
| 轻量编排 | ✅ | 注入 prompt + 合并依赖 Tool/MCP |
| 作用域 | ✅ | 全局/Agent 私有 |
| 远程安装 | ✅ | 从 GitHub/skills.sh 安装 |

### 1.9 SubAgent 体系

| 功能 | 状态 | 说明 |
|------|------|------|
| SubAgent CRUD | ✅ | 创建/编辑/删除 |
| 独立推理 | ✅ | 独立系统提示词 + 工具集 |
| 委派调用 | ✅ | `delegate_to_subagent` 工具 |

### 1.10 RAG 知识库

| 功能 | 状态 | 说明 |
|------|------|------|
| 知识库 CRUD | ✅ | 创建/编辑/删除 |
| 成员权限 | ✅ | Creator/Manager/Developer/Viewer |
| 文档管理 | ✅ | 上传/预览/删除 |
| 文档解析 | ✅ | Tika + OCR（扫描件） |
| 分块策略 | ✅ | 按标题拆分 + 按大小切分 |
| 向量化 | ✅ | pgvector/Milvus 双引擎 |
| RAG 问答 | ✅ | 语义检索 + 上下文注入 |
| 在线编辑 | ✅ | Markdown 编辑器 + 增量更新 |
| 思维导图 | ✅ | AI 生成知识结构 |
| 示例问题 | ✅ | AI 生成示例问题 |

### 1.11 问答对（QA Pair）

| 功能 | 状态 | 说明 |
|------|------|------|
| QA Pair CRUD | ✅ | 创建/编辑/删除 |
| 批量导入 | ✅ | JSONL 格式 |
| AI 提取 | ✅ | 从文档自动生成 |
| 高优先级检索 | ✅ | 相似度 >= 0.85 直接返回答案 |
| Agent 集成 | ✅ | 自动注入对话上下文 |

### 1.12 知识图谱

| 功能 | 状态 | 说明 |
|------|------|------|
| 图谱构建 | ✅ | LLM 自动抽取 + 手动导入 |
| Neo4j 存储 | ✅ | 每个知识库独立 Label |
| 图谱检索 | ✅ | 实体识别 + 1-2 跳子图展开 |
| 可视化 | ✅ | @antv/g6 力导向图 |
| 手动编辑 | ✅ | 添加/删除实体关系 |

### 1.13 评测体系

| 功能 | 状态 | 说明 |
|------|------|------|
| 评测集管理 | ✅ | CRUD + 版本管理 |
| 评测基准 | ✅ | AI 生成 + JSONL 导入 |
| 评估器 | ✅ | 自定义评估逻辑 |
| 实验运行 | ✅ | 批量评测 + 结果统计 |

### 1.14 Prompt 管理

| 功能 | 状态 | 说明 |
|------|------|------|
| Prompt CRUD | ✅ | 创建/编辑/删除 |
| Prompt 模板 | ✅ | 变量模板 + 版本管理 |
| 标签管理 | ✅ | Prompt 分类 |

### 1.15 可观测性

| 功能 | 状态 | 说明 |
|------|------|------|
| LLM Trace | ✅ | 调用链路记录 |
| Tool Call 记录 | ✅ | 工具调用追踪 |
| 仪表盘 | ✅ | 统计概览 |
| 日志监控 | ✅ | SSE 实时日志流 |
| 任务中心 | ✅ | 异步任务状态跟踪 |

### 1.16 前端能力

| 功能 | 状态 | 说明 |
|------|------|------|
| Landing 页 | ✅ | 可配置落地页 |
| 暗色主题 | ✅ | 主题切换 |
| 响应式布局 | ✅ | 移动端适配 |
| 虚拟滚动 | ✅ | @tanstack/vue-virtual |
| SSE 重连 | ✅ | 指数退避重连 |

---

## 二、Phase 1：生产环境优化（已完成）

> 目标：单体架构下完成核心性能优化，具备上线能力

| 优化项 | 状态 | 说明 |
|--------|------|------|
| 数据库索引优化 | ✅ | 复合索引 + Trace 概览 SQL 聚合 |
| 向量检索调优 | ✅ | 统一 HNSW 索引 + ef_search 提升 |
| Redis 缓存体系 | ✅ | Spring Cache + 7 域缓存 + 预热 |
| 文件存储优化 | ✅ | ensureBucket 单次调用 + 流式下载 |
| SSE 重连机制 | ✅ | 对话/任务/日志三场景指数退避 |
| 大列表虚拟滚动 | ✅ | 消息列表虚拟滚动 |
| 会话标题优化 | ✅ | 专用轮询端点 + 系统默认模型生成 |

**关键指标提升：**
- Trace 概览查询：秒级 → 毫秒级
- 向量检索召回率：~90% → ~98%
- 缓存命中后 DB 查询：100+ QPS → 0 QPS
- 长对话滚动性能：100+ 消息无卡顿

---

## 三、Phase 2：性能与稳定性（规划中）

> 目标：引入中间件提升系统稳定性和可维护性

### 3.1 API 网关与限流（P1，8.5d）

| 项目 | 说明 |
|------|------|
| Spring Cloud Gateway | 统一入口、路由、负载均衡 |
| Sentinel 限流 | 用户级 QPS 限制、熔断降级 |
| LLM 成本控制 | Token 预算、日用量限制 |

### 3.2 消息队列解耦（P1，8.5d）

| 项目 | 说明 |
|------|------|
| RocketMQ 集成 | 替代 Redis List 任务队列 |
| 重试机制 | 自动重试 + 死信队列 |
| Trace 异步写入 | MQ 保障持久化 |

### 3.3 LLM 调用治理（P1，8.5d）

| 项目 | 说明 |
|------|------|
| HTTP 连接池 | OkHttp/Reactor 连接池配置 |
| 超时控制 | LLM 调用整体超时 |
| 响应式重试 | 替代 Thread.sleep |

### 3.4 文档处理优化（P1，6d）

| 项目 | 说明 |
|------|------|
| 批量 INSERT | Chunk 批量保存 |
| Embedding 并行 | CompletableFuture 并行调用 |
| 动态线程池 | 按负载扩缩容 |

### 3.5 缓存一致性（P2，3d）

| 项目 | 说明 |
|------|------|
| 布隆过滤器 | 防穿透 |
| 分布式锁 | 防击穿 |
| 随机 TTL | 防雪崩 |

### 3.6 可观测性增强（P2，11d）

| 项目 | 说明 |
|------|------|
| Prometheus + Grafana | Metrics 指标监控 |
| SkyWalking | 分布式链路追踪 |
| ELK | 日志聚合检索 |
| 告警规则 | 主动告警通知 |

### 3.7 安全加固（P0，7.5d）

| 项目 | 说明 |
|------|------|
| 密码策略 | 首次登录强制修改 |
| 审计日志 | 操作记录追踪 |
| 敏感信息脱敏 | Trace 中遮蔽系统提示词 |
| PgSqlTool 限制 | 禁止危险 SQL |

---

## 四、Phase 3：架构升级（远期）

> 目标：微服务化 + 企业级能力

### 4.1 微服务拆分（P3，31d）

| 服务 | 职责 |
|------|------|
| lightbot-gateway | API 网关、认证、限流 |
| lightbot-chat | 对话核心、会话管理 |
| lightbot-agent | Agent/Version/Workflow |
| lightbot-rag | 知识库/文档/向量检索 |
| lightbot-tool | Tool/MCP/Skill |
| lightbot-obs | 可观测性 |
| lightbot-eval | 评测体系 |

**技术选型：** Nacos 注册中心 + OpenFeign + SkyWalking

### 4.2 工作流并行执行（P3，12d）

| 项目 | 说明 |
|------|------|
| DAG 拓扑排序 | Kahn 算法计算入度 |
| 并行执行器 | WorkStealingPool 并行节点 |
| 变量上下文安全 | ConcurrentHashMap |

### 4.3 多租户与权限（P3，19d）

| 项目 | 说明 |
|------|------|
| 租户隔离 | tenant_id 字段 + TenantLineHandler |
| RBAC 权限 | 角色 + 权限 + 数据权限 |
| 资源配额 | Token/存储/Agent 数量限制 |

### 4.4 配置中心（P1，5d）

| 项目 | 说明 |
|------|------|
| Nacos Config | 多环境配置隔离 |
| 密钥管理 | 环境变量/Vault |
| 动态配置 | 运行时热更新 |

### 4.5 数据库迁移（P1，2d）

| 项目 | 说明 |
|------|------|
| Flyway 集成 | 自动迁移 + 版本管理 |
| 基线脚本 | 现有 SQL 整理为 V1 |

---

## 五、技术架构

### 5.1 后端架构

```
┌─────────────────────────────────────────────────────────┐
│                    lightbot-server                      │
├─────────────────────────────────────────────────────────┤
│  Controller → Service(Interface) → ServiceImpl → Mapper │
│                      ↓                                  │
│                  Util（MinIO/Redis）                     │
│                      ↓                                  │
│  SpringAI ← ModelFactory ← ModelProvider               │
└─────────────────────────────────────────────────────────┘
```

### 5.2 前端架构

```
Vue3 + Vite + Ant Design Vue + Pinia + Vue Router
    ↓
API 层（axios） → 后端接口
    ↓
页面组件（views/） → 布局组件（layouts/）
    ↓
虚拟滚动 + SSE 重连 + 主题切换
```

### 5.3 数据层

| 组件 | 用途 |
|------|------|
| PostgreSQL 15 | 主数据库（38 张表） |
| pgvector | 向量存储（HNSW 索引） |
| Redis | 缓存 + 会话 + 任务队列 |
| MinIO | 文件存储 |
| Neo4j | 知识图谱 |
| Milvus | 向量检索（可选） |

### 5.4 认证鉴权

- Sa-Token 1.39.0 Token 模式
- 路由级鉴权 + 排除健康检查端点
- 会话管理 + 自动续期

---

## 六、数据库概览

| 域 | 核心表 | 说明 |
|----|--------|------|
| 用户 | users, system_config | 用户管理、系统配置 |
| Agent | agent, agent_version, subagent, skill | Agent 及其组件 |
| 对话 | chat_session, message | 会话和消息 |
| 知识库 | knowledge, document, chunk, embedding, qa_pair, knowledge_graph | RAG 全链路 |
| 工具 | tool, mcp_server, tool_call | 工具体系 |
| 工作流 | workflow_node, workflow_edge, workflow_variable | 工作流引擎 |
| 评测 | eval_dataset, eval_benchmark, eval_experiment, eval_evaluator | 评测体系 |
| 可观测 | llm_trace, llm_trace_event | 调用追踪 |
| Prompt | prompt, prompt_version, prompt_build_template | Prompt 管理 |

---

## 七、开发规范

| 规范 | 说明 |
|------|------|
| 包结构 | controller/service/entity/dto/mapper/util/config/constant/enums/exception |
| DTO 规范 | @Valid 校验 + @Size 限制（name=50, desc=50） |
| Service 规范 | Interface + ServiceImpl 分离 |
| Entity 规范 | @TableField + @Schema + JSONB TypeHandler |
| 数据库 | MyBatis-Plus + 不使用 t_ 前缀 |
| 异常处理 | ErrorCode 枚举 + BizException |
| 缓存 | Spring Cache + RedisCacheManager |
| 文件存储 | MinIO Util 封装 |

---

## 八、部署架构

### 8.1 单体部署（当前）

```
┌──────────────────────────────────────┐
│           Nginx (反向代理)            │
├──────────────────────────────────────┤
│  lightbot-server (Spring Boot)       │
│  ├── PostgreSQL + pgvector           │
│  ├── Redis                           │
│  ├── MinIO                           │
│  └── Neo4j                           │
└──────────────────────────────────────┘
```

### 8.2 微服务部署（Phase 3）

```
┌──────────────────────────────────────┐
│     Spring Cloud Gateway             │
├──────────────────────────────────────┤
│  ├── lightbot-chat                   │
│  ├── lightbot-agent                  │
│  ├── lightbot-rag                    │
│  ├── lightbot-tool                   │
│  ├── lightbot-obs                    │
│  └── lightbot-eval                   │
├──────────────────────────────────────┤
│  Nacos (注册中心 + 配置中心)          │
│  RocketMQ (消息队列)                  │
│  Prometheus + Grafana (监控)          │
└──────────────────────────────────────┘
```

---

## 九、版本历史

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| v1.0 | 2026-05 | 基础框架：用户/模型/对话/Agent |
| v1.1 | 2026-05 | RAG 知识库 + 文档管理 |
| v1.2 | 2026-05 | 工作流引擎 + 可视化编排 |
| v1.3 | 2026-05 | Tool/MCP/Skill/SubAgent 体系 |
| v1.4 | 2026-06 | QA Pair + 知识图谱 + 评测体系 |
| v1.5 | 2026-06 | Phase 1 性能优化（缓存/索引/SSE） |
| v2.0 | 规划中 | API 网关 + 消息队列 + 可观测性 |
| v3.0 | 规划中 | 微服务化 + 多租户 |
