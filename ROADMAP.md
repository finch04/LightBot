# LightBot Roadmap

轻量级现代化 Java Agent 平台。

技术栈：SpringBoot + SpringAI + Vue3

参考项目：Dify / Spring AI Alibaba Admin / Yuxi

---

## v0.1 MVP

> 核心目标：跑通最小闭环，验证架构可行性

### 核心功能

| 模块 | 功能 |
|------|------|
| Chat | 单轮对话、多轮对话、流式输出 |
| Agent | 基础 Agent 定义（系统提示词 + 模型配置） |
| Tool | 内置工具：HTTP 请求、当前时间 |
| 模型 | OpenAI / 通义千问 接入 |
| 存储 | 会话历史持久化（MySQL） |

### 技术重点

- SpringAI 对接 LLM，流式 SSE 输出
- Vue3 对话界面，Markdown 渲染
- 基础 Tool 定义与调用协议

### 非目标

- Workflow
- RAG
- 用户体系
- 权限管理

---

## v0.2 Agent + Tool 体系

> 核心目标：建立可扩展的 Agent 和 Tool 体系

### 核心功能

| 模块 | 功能 |
|------|------|
| Agent | Agent 模板、变量注入、多模型切换 |
| Tool | 自定义 Tool（Java 注册）、Tool 编排 |
| RAG | 文档上传、向量检索、知识库管理 |
| 前端 | Agent 编辑器、知识库管理页 |

### 技术重点

- Tool 协议标准化，支持热插拔
- 向量数据库接入（PgVector / Milvus）
- 文档解析（PDF / Word / Markdown）

### 非目标

- Workflow 可视化编排
- 多 Agent 协同
- OCR
- 沙盒执行

---

## v0.3 Workflow 引擎

> 核心目标：实现可视化 Workflow 编排

### 核心功能

| 模块 | 功能 |
|------|------|
| Workflow | 可视化画布（Vue Flow）、节点拖拽 |
| 节点类型 | LLM 节点、Tool 节点、条件分支、变量赋值 |
| 运行时 | Workflow 执行引擎、节点状态追踪 |
| 调试 | 单节点调试、运行日志、变量查看 |

### 技术重点

- Workflow DAG 执行引擎（拓扑排序 + 并发执行）
- Vue Flow 画布组件集成
- 节点间数据传递协议

### 非目标

- 微服务拆分
- Kubernetes 部署
- Prompt 优化
- AI 监控

---

## v1.0 生产可用

> 核心目标：生产级稳定性，开放使用

### 核心功能

| 模块 | 功能 |
|------|------|
| 用户体系 | 注册 / 登录、API Key 管理 |
| 权限 | 多租户、资源隔离 |
| 部署 | Docker 镜像、一键部署脚本 |
| API | RESTful API、SDK（Java / Python） |
| 稳定性 | 限流、熔断、异常兜底 |

### 技术重点

- Saas 多租户数据隔离
- API Key 鉴权 + 限流
- Docker Compose 一键部署

### 非目标

- Kubernetes 编排
- 多 Agent 协同
- OCR / 沙盒
- Prompt 优化 / AI 监控

---

## 暂不规划

以下特性在当前阶段暂不考虑：

| 特性 | 原因 |
|------|------|
| 微服务 | 单体足够，过早拆分增加复杂度 |
| Kubernetes | Docker Compose 满足初期部署 |
| 多 Agent 协同 | 单 Agent + Workflow 已覆盖多数场景 |
| OCR | 依赖重，可用第三方 API 替代 |
| 沙盒 | 安全风险高，Tool 层面做限制即可 |
| Prompt 优化 | 业务场景驱动，不做通用优化 |
| AI 监控 | 基础日志足够，后期按需扩展 |
