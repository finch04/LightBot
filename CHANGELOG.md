# Changelog

本项目所有显著变更均记录在此文件。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

---

## [Unreleased]

### Added
- API 工具执行链路：支持 HTTP API 工具绑定到 Agent 并在对话中自动调用
  - `ApiToolCallback`：实现 Spring AI ToolCallback 接口，包装数据库 API 工具
  - `ApiToolExecutionService`：HTTP 执行引擎，支持参数校验、认证注入、参数路由、SSRF 防护
  - 支持 `x-location` 扩展字段指定参数位置（path/query/header/body）
- Token 预算管理
  - 全局/用户级日 Token 限额配置
  - 单次调用 Token 上限
  - 今日 Token 消耗统计、全局统计
  - 用户 Token 消耗排行榜（含头像和用户名）
- 沙箱代码执行工具：支持 Python 脚本在沙箱环境中执行
- 消息编辑重发功能：支持编辑已发送消息并重新提交
- 工具调用元数据展示：对话中展示工具调用参数详情和执行结果
- Skill 管理界面增强：Skill 详情页、文件树浏览

### Changed
- ToolType 枚举清理：移除 CUSTOM（合并到 API）和 MCP，统一为 BUILTIN/API/KNOWLEDGE
- 工具选择界面简化：AgentDetail 工具绑定弹窗移除 CUSTOM/MCP 类型筛选
- 对话服务重构：中间件链架构，提升可维护性

### Fixed
- 修复 Reactor 异步链路中 Sa-Token ThreadLocal 丢失导致 Token 使用统计始终为 0
- 修复工具结果显示组件的纯文本渲染问题
- 修复知识库工具绑定校验和界面组件样式问题
- 修复多个安全漏洞并完善权限控制
- 消除 N+1 查询问题，优化数据库查询性能

### Removed
- 移除 MCP 工具类型支持（数据库中已有数据迁移到 API 类型）
- 移除 CUSTOM 工具类型（合并到 API）

---

## [1.4.0] - 2026-06-01

### Added
- 对话界面工具调用展示：实时显示工具调用状态、参数和结果
- 用户问答功能（ask_user 工具）：Agent 对话中主动向用户提问
- 图片生成功能：支持 AI 图片生成并优化前端展示
- 观测功能界面：增强数据追踪和 LLM 调用链路追踪
- 评估器配置：支持多评估器配置和优化加载性能

### Changed
- 重构聊天服务实现，提升代码质量和可维护性
- 替换自动创建管理员为手动初始化引导
- 优化实体展示界面的图标和数据加载逻辑

### Fixed
- 修复聊天界面和会话管理功能问题
- 修复消息关联和文件清理问题

---

## [1.3.0] - 2026-05-15

### Added
- 沙箱代码执行工具：支持在隔离环境中执行自定义代码
- Skill 依赖管理：技能可声明依赖的工具和其他技能
- MarkdownPreview 组件增强：支持代码高亮、表格渲染

### Changed
- 重构绑定管理逻辑并优化组件结构
- 优化对话界面体验并改进工具功能

### Fixed
- 修复工具选择界面的详情弹窗显示问题

---

## [1.2.0] - 2026-05-01

### Added
- 扩展管理页面：统一管理工具、MCP、技能、子智能体
- RAG 知识库检索增强：支持多知识库联合检索
- 对话上下文管理：支持上下文消息条数限制和摘要压缩

### Changed
- 优化多个性能问题提升系统稳定性

---

## [1.1.0] - 2026-04-15

### Added
- Workflow 工作流引擎：支持 DAG 节点编排
- MCP Server 管理：支持外部 MCP 服务接入
- 子智能体委派：Agent 可委派任务给其他 Agent

### Changed
- 优化聊天界面体验

---

## [1.0.0] - 2026-04-01

### Added
- Agent 创建与管理：支持多 Agent 实例
- 对话系统：基于 Spring AI 的流式对话
- 工具体系：内置工具 + 自定义工具 + 知识库工具
- RAG 知识库：文档上传、向量化、检索增强
- 用户体系：注册、登录、权限管理
- 系统配置：模型配置、敏感词过滤、Token 限额
