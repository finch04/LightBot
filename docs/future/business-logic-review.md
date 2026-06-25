# LightBot 业务逻辑审查报告 v1.0

> **审查日期**：2026-06-25
> **审查范围**：全量后端（33 Controller + 中间件链 + 工作流引擎 + 任务系统）+ 全量前端（75 Views + 60 Components + 28 API 模块）
> **审查标准**：业务合理性、用户体验、功能完整性、扩展性
> **前置版本**：v0.9（聚焦安全/可靠性/性能/代码质量，已归档为 `business-logic-review-0.9.md`）

---

## 目录

- [一、对话交互业务](#一对话交互业务)
- [二、Agent 配置与管理](#二agent-配置与管理)
- [三、知识库与 RAG](#三知识库与-rag)
- [四、工作流引擎](#四工作流引擎)
- [五、工具与 MCP 体系](#五工具与-mcp-体系)
- [六、Skill 体系](#六skill-体系)
- [七、评估与实验系统](#七评估与实验系统)
- [八、可观测性与运维](#八可观测性与运维)
- [九、用户与权限体系](#九用户与权限体系)
- [十、前端交互体验](#十前端交互体验)
- [附录：优先级排序](#附录优先级排序)

---

## 一、对话交互业务

### 1.1 对话无消息引用/回复功能

**位置**：`Chat.vue` + `ChatController.java` + `Message` entity

**问题**：
当前对话是纯线性消息流，用户无法引用某条历史消息进行回复或追问。在长对话中，用户想针对 AI 某段具体回答提问时，只能手动粘贴原文，体验差。

**技术设计**：
- 后端：`Message` entity 新增 `replyToMessageId` 字段；`ChatRequest` 新增 `replyToMessageId`；`MessageMiddleware` 保存时关联引用
- 前端：用户消息气泡增加"引用回复"入口，发送时携带 `replyToMessageId`；渲染时在消息上方显示被引用内容的摘要（截取前 100 字）
- 引用消息被删除时，引用关系置空，显示"原消息已删除"

**难点**：虚拟滚动下的引用消息定位与跳转

**工作量**：2 天

**影响范围**：Message entity、ChatRequest DTO、MessageMiddleware、Chat.vue

---

### 1.2 对话无消息搜索功能

**位置**：`ChatSessionController.java` + `Chat.vue`

**问题**：
用户无法在当前对话或历史对话中搜索特定消息内容。当对话量大时，查找历史信息只能手动翻页，效率极低。

**技术设计**：
- 后端：新增 `GET /api/chat/sessions/{id}/messages/search?keyword=xxx` 接口，使用 PostgreSQL `ILIKE` 或 `tsvector` 全文检索
- 前端：对话工具栏增加搜索图标，点击展开搜索框，搜索结果高亮显示并自动滚动到匹配位置
- 支持全局搜索：`GET /api/chat/messages/search?keyword=xxx`，跨会话搜索，返回消息列表 + 所属会话信息

**难点**：虚拟滚动下的搜索结果定位；全局搜索的性能（需加索引）

**工作量**：3 天

**影响范围**：新增 API、MessageMapper、Chat.vue、MainLayout.vue

---

### 1.3 对话无消息收藏/标记功能

**位置**：`Chat.vue` + `Message` entity

**问题**：
用户无法收藏重要的 AI 回复。有价值的信息淹没在大量对话中，后续难以快速找到。

**技术设计**：
- 后端：`Message` entity 新增 `starred` 字段（Boolean）；新增 `PUT /api/chat/sessions/{sessionId}/messages/{messageId}/star` 切换收藏状态；新增 `GET /api/chat/messages/starred` 获取所有收藏消息
- 前端：助手消息 action bar 增加收藏图标（星标）；侧边栏或独立页面展示收藏列表

**难点**：收藏列表的跨会话聚合查询

**工作量**：1.5 天

**影响范围**：Message entity、MessageMapper、ChatSessionController、Chat.vue

---

### 1.4 会话无标签/分组管理

**位置**：`ChatSession` entity + `ChatSessionController.java`

**问题**：
会话列表只有"置顶"和"归档"两种状态，无法按项目/主题分组管理。当会话数量增多后，查找困难。

**技术设计**：
- 后端：新增 `session_tag` 表（id, userId, name, color）；`chat_session` 新增 `tagId` 字段；新增 Tag CRUD 接口
- 前端：会话列表增加按标签筛选；会话设置中增加标签选择；侧边栏标签分组展示

**难点**：标签与会话的多对一关系管理

**工作量**：2 天

**影响范围**：新增 entity/mapper/controller、ChatSession entity、MainLayout.vue

---

### 1.5 对话导出功能缺失

**位置**：`ChatSessionController.java`

**问题**：
用户无法导出对话记录。对于有价值的对话（如技术方案讨论、调试过程），用户需要离线保存或分享。

**技术设计**：
- 后端：新增 `GET /api/chat/sessions/{id}/export?format=markdown|json` 接口，将完整对话（含工具调用、RAG 引用）格式化为 Markdown 或 JSON
- 前端：会话操作菜单增加"导出"选项，支持下载为 `.md` 或 `.json` 文件

**难点**：工具调用结果的格式化（部分工具结果是 JSON，需转换为可读格式）

**工作量**：1.5 天

**影响范围**：ChatSessionController、ChatSessionService、Chat.vue

---

### 1.6 多模态输入不完整 — 图片理解能力未暴露

**位置**：`ToolPrepMiddleware.java` + `Chat.vue`

**问题**：
系统已支持图片上传（`ChatAttachmentDTO`），但图片仅作为附件展示，未作为视觉内容传入 LLM 进行理解。支持视觉能力的模型（如 GPT-4o、Qwen-VL）的图片理解能力被浪费。

**技术设计**：
- 后端：`ChatServiceCore` 构建消息时，将图片附件转换为 Spring AI 的 `Media` 对象，附加到用户消息中；需判断当前模型是否支持 vision（从 `Model` entity 的 capabilities JSONB 读取）
- 前端：图片上传后显示缩略图预览；发送时图片作为多模态内容传入
- 不支持 vision 的模型：图片仍作为文件附件展示，提示用户"当前模型不支持图片理解"

**难点**：不同模型的多模态 API 格式差异（OpenAI vs DashScope）

**工作量**：2 天

**影响范围**：ChatServiceCore、Model entity、ToolPrepMiddleware、Chat.vue

---

### 1.7 会话列表无分页 — 固定 50 条

**位置**：`MainLayout.vue` L1680 附近

**问题**：
侧边栏会话列表固定加载 50 条，无分页或无限滚动。会话超过 50 条后，旧会话无法在侧边栏找到，只能通过搜索。

**技术设计**：
- 后端：`getSessions` 已支持分页参数，无需改动
- 前端：侧边栏会话列表改为无限滚动（IntersectionObserver），滚动到底部自动加载下一页；增加 `totalCount` 显示

**难点**：虚拟列表与无限滚动的结合

**工作量**：1 天

**影响范围**：MainLayout.vue

---

## 二、Agent 配置与管理

### 2.1 Agent 无复制/克隆功能

**位置**：`AgentController.java` + `AgentManage.vue`

**问题**：
用户无法基于现有 Agent 快速创建副本。当需要创建相似配置的 Agent（如不同模型版本对比）时，必须手动重新配置所有内容。

**技术设计**：
- 后端：新增 `POST /api/agents/{id}/clone` 接口，深拷贝 Agent 配置（含绑定的知识库、工具、MCP、SubAgent、Skill），名称加"(副本)"后缀，状态重置为 draft
- 前端：Agent 卡片操作菜单增加"复制"选项

**难点**：绑定关系的深拷贝（需要复制关联表记录）

**工作量**：1 天

**影响范围**：AgentController、AgentService、AgentManage.vue

---

### 2.2 Agent 无导入/导出功能

**位置**：`AgentController.java`

**问题**：
用户无法将 Agent 配置导出为文件分享给他人，也无法从文件导入 Agent 配置。团队协作时只能口头描述配置。

**技术设计**：
- 后端：新增 `GET /api/agents/{id}/export` 导出为 JSON（含 system prompt、config、绑定 ID 列表）；新增 `POST /api/agents/import` 从 JSON 导入（绑定 ID 需映射到当前环境）
- 前端：Agent 列表增加"导入"按钮；Agent 操作菜单增加"导出"选项

**难点**：跨环境导入时 ID 映射问题（知识库/工具/MCP 在目标环境可能不存在）

**工作量**：2 天

**影响范围**：AgentController、AgentService、AgentManage.vue

---

### 2.3 Agent 系统提示词无变量预览

**位置**：`AgentDetail.vue`

**问题**：
系统提示词支持 `{{变量名}}` 占位符（通过 `bizParams` 替换），但编辑时无法预览替换后的效果。用户不知道变量是否正确替换。

**技术设计**：
- 前端：在系统提示词编辑区增加"预览"按钮，点击后展示变量输入表单 + 替换后的提示词预览
- 复用 Playground 的变量替换逻辑

**难点**：变量类型推断（字符串/数字/布尔）

**工作量**：1 天

**影响范围**：AgentDetail.vue

---

### 2.4 Agent 版本对比功能缺失

**位置**：`AgentDetail.vue` 版本管理抽屉

**问题**：
版本管理支持查看历史版本和恢复，但无法对比两个版本之间的差异。用户不知道某个版本改了什么。

**技术设计**：
- 后端：`GET /api/agents/{id}/versions/{v1}/diff/{v2}` 返回两个版本的字段级差异
- 前端：版本列表增加"对比"入口，左右分栏展示两个版本的 system prompt、config、绑定差异

**难点**：JSONB 字段的结构化 diff

**工作量**：2 天

**影响范围**：AgentController、AgentService、AgentDetail.vue

---

### 2.5 SubAgent 执行不可观测

**位置**：`SubAgentRuntime.java` + `Chat.vue`

**问题**：
当主 Agent 调用 SubAgent 时，用户只能看到"正在调用 SubAgent: xxx"的工具事件，无法看到 SubAgent 的内部推理过程（中间工具调用、思考过程）。对于复杂的 SubAgent 任务，用户完全黑盒。

**技术设计**：
- 后端：`SubAgentRuntime.run()` 执行过程中，将中间事件（工具调用、中间结果）通过回调推送给主 Agent 的 SSE 流，使用新的事件类型 `subagent_step`
- 前端：`AgentCapabilityPanel` 中 SubAgent 事件支持展开查看内部步骤，类似工作流节点展开

**难点**：SubAgent 的流式回调与主 Agent SSE 流的合并

**工作量**：3 天

**影响范围**：SubAgentRuntime、ChatServiceCore、AgentCapabilityPanel.vue、Chat.vue

---

## 三、知识库与 RAG

### 3.1 知识库无增量更新机制

**位置**：`DocumentIngestExecutor.java` + `KnowledgeDocController.java`

**问题**：
文档内容变更后，必须删除旧文档重新上传。对于 URL 类型的文档（如在线 Wiki），无法自动检测更新并重新导入。

**技术设计**：
- 后端：URL 文档增加 `lastFetchedAt` 和 `contentHash` 字段；新增定时任务（可配置周期）自动抓取 URL 内容，对比 hash，变化时触发重新导入
- 前端：URL 文档详情显示"最后同步时间"和"手动同步"按钮

**难点**：内容变更检测的准确性（hash 对比 vs 语义对比）

**工作量**：3 天

**影响范围**：Document entity、新增定时任务、KnowledgeDocController、KnowledgeDetail.vue

---

### 3.2 知识库无重复文档检测

**位置**：`DocumentIngestExecutor.java`

**问题**：
用户上传相同文档时，系统不会检测重复，导致同一文档被多次导入，浪费存储和向量空间。

**技术设计**：
- 后端：上传时计算文件 MD5/SHA256，与同知识库下已有文档对比；发现重复时返回提示（允许强制导入或跳过）
- 前端：上传组件显示"检测到重复文档"提示，提供覆盖/跳过/保留两个版本三个选项

**难点**：内容相同但文件名不同的场景（需按内容 hash 判断）

**工作量**：1.5 天

**影响范围**：Document entity、DocumentIngestExecutor、KnowledgeDetail.vue

---

### 3.3 RAG 检索质量无反馈闭环

**位置**：`QueryKnowledgeTool.java` + `Chat.vue`

**问题**：
RAG 检索返回的文档片段质量无法评估。用户无法对检索结果给出"有用/无用"反馈，系统无法基于反馈优化检索策略。

**技术设计**：
- 后端：新增 `rag_feedback` 表（messageId, chunkId, feedbackType: positive/negative, userId）；新增 `POST /api/chat/messages/{messageId}/rag-feedback` 接口
- 前端：RAG 引用区域每个引用增加 👍/👎 按钮
- 后续可基于反馈数据调整 rerank 权重或检索参数

**难点**：反馈数据的积累需要时间才能产生价值

**工作量**：1.5 天

**影响范围**：新增 entity/mapper/controller、Chat.vue

---

### 3.4 知识库查询参数配置对用户不友好

**位置**：`KnowledgeDetail.vue` 查询参数配置

**问题**：
查询参数（topK、similarityThreshold、rerank 等）对普通用户来说太技术化。用户不知道这些参数的含义和调优方向。

**技术设计**：
- 前端：参数配置页增加"推荐配置"预设（精确模式/平衡模式/广泛模式），每个预设对应一组参数值；增加参数说明气泡和示例
- 后端：无改动，前端预设值直接映射到现有参数

**难点**：推荐配置的默认值需要根据实际场景调优

**工作量**：1 天

**影响范围**：KnowledgeDetail.vue

---

### 3.5 知识图谱与 RAG 检索未融合

**位置**：`QueryKnowledgeTool.java` + `KnowledgeGraphService.java`

**问题**：
知识图谱和向量检索是两套独立系统。当知识库同时开启了图谱和向量检索时，查询工具只做向量检索，图谱中的结构化关系知识未被利用。

**技术设计**：
- 后端：`QueryKnowledgeTool` 增加图谱检索路径：先从用户 query 中提取实体（NER），在 Neo4j 中查找相关三元组，将图谱结果与向量检索结果合并排序
- 新增 `graphRetrievalEnabled` 配置项，控制是否启用混合检索
- 结果中区分来源（向量/图谱），便于 LLM 引用

**难点**：NER 实体提取的准确性；图谱结果与向量结果的融合排序策略

**工作量**：5 天

**影响范围**：QueryKnowledgeTool、KnowledgeGraphService、ToolCallRenderer.vue

---

## 四、工作流引擎

### 4.1 工作流无条件分支默认路径校验

**位置**：`WorkflowConfigServiceImpl.java` + `ConditionNode.vue`

**问题**：
条件分支节点（Condition）可以不配置默认分支（else）。当所有条件都不满足时，工作流直接结束，无任何提示。这在生产环境中是隐患。

**技术设计**：
- 后端：`validateGraph()` 中对 Condition 节点校验：必须至少有一个 outgoing edge 的 `conditionType = DEFAULT`，否则校验失败
- 前端：Condition 节点配置面板增加"默认分支"开关，未配置时显示黄色警告

**难点**：无

**工作量**：0.5 天

**影响范围**：WorkflowConfigServiceImpl、ConditionNode.vue

---

### 4.2 工作流无人工审批节点

**位置**：工作流引擎

**问题**：
工作流完全自动化执行，无法在关键步骤插入人工审批/确认。对于需要人工介入的业务流程（如内容审核、决策确认），只能通过外部系统实现。

**技术设计**：
- 新增 `HumanApprovalNode` 节点类型
- 执行到该节点时，工作流暂停，推送通知给指定用户（SSE/站内消息）
- 用户审批后（通过/驳回 + 意见），工作流从暂停点恢复执行
- `Task` entity 增加 `HUMAN_APPROVAL` 类型，存储审批状态

**难点**：工作流暂停/恢复的状态持久化；审批通知机制

**工作量**：5 天

**影响范围**：工作流引擎核心、新增节点类型、Task 系统、前端审批 UI

---

### 4.3 工作流无变量面板 — 调试困难

**位置**：`WorkflowTestDrawer.vue`

**问题**：
工作流调试时，用户只能看到每个节点的输入输出，无法查看中间变量的实时状态。调试复杂工作流时需要逐个节点查看，效率低。

**技术设计**：
- 后端：`WorkflowExecutorService` 在执行过程中，将每个节点的输出写入 `variables` map；测试执行完成后返回完整的变量快照
- 前端：测试抽屉增加"变量面板"标签页，实时展示所有变量的当前值（树形结构，支持 JSON 展开）

**难点**：变量类型的多样性（字符串/JSON/文件路径）的展示

**工作量**：2 天

**影响范围**：WorkflowExecutorService、WorkflowTestDrawer.vue

---

### 4.4 工作流无子流程/模块化支持

**位置**：工作流引擎

**问题**：
复杂工作流只能在一个画布上实现，导致画布过于庞大。无法将常用的节点组合封装为可复用的子流程。

**技术设计**：
- 新增 `SubWorkflowNode` 节点类型，引用另一个已发布的工作流
- 执行时递归调用子工作流的 `WorkflowExecutorService`
- 子工作流的输入输出映射到主工作流的变量

**难点**：递归执行的深度限制和超时控制；循环引用检测

**工作量**：5 天

**影响范围**：工作流引擎核心、新增节点类型、WorkflowEdit.vue

---

## 五、工具与 MCP 体系

### 5.1 API 工具不支持自动执行

**位置**：`ToolServiceImpl.java` L247

**问题**：
`ToolType.API` 类型的工具在 `resolveToolCallbacks()` 中被跳过（仅打印日志），无法被 LLM 自动调用。用户创建 API 工具后发现无法使用，体验差。

**技术设计**：
- 后端：实现 `ApiToolCallback`，根据 `endpointUrl`、`authType`、`authConfig`、`inputSchema` 构建 HTTP 请求并执行
- 需要处理：请求参数映射（LLM 参数 → HTTP 参数）、响应解析（JSON → 字符串）、超时控制、错误处理
- 安全：URL 白名单校验（复用 ApiNodeProcessor 的 SSRF 防护）

**难点**：HTTP 响应格式的多样性（JSON/XML/HTML/纯文本）；认证方式的多样性（Bearer/Basic/API Key/自定义 Header）

**工作量**：5 天

**影响范围**：ToolServiceImpl、新增 ApiToolCallback、ToolManage.vue

---

### 5.2 MCP 工具热更新缺失

**位置**：`McpClientServiceImpl.java`

**问题**：
MCP Server 的工具列表在 Agent 配置时加载一次，之后不会自动更新。当 MCP Server 新增或修改工具后，用户必须手动刷新。且 MCP Server 宕机恢复后，工具状态不会自动恢复。

**技术设计**：
- 后端：新增 MCP Server 心跳检测（定时 ping），工具列表变更时自动更新缓存；Agent 对话时，ToolPrepMiddleware 从缓存读取工具列表（而非每次都连接 MCP Server）
- 前端：MCP 管理页显示"最后同步时间"和"手动同步"按钮

**难点**：MCP 协议本身不支持工具变更通知（需轮询）

**工作量**：3 天

**影响范围**：McpClientServiceImpl、McpServer entity、McpManage.vue

---

### 5.3 工具测试结果无历史记录

**位置**：`ToolController.java` testTool

**问题**：
工具测试执行后，结果仅显示在当前页面，刷新后丢失。用户无法回顾历史测试结果，也无法对比不同参数的执行效果。

**技术设计**：
- 后端：新增 `tool_test_result` 表（toolId, args, result, duration, status, userId, createTime）；新增查询接口
- 前端：工具测试面板增加"历史记录"标签页，展示最近 20 次测试结果

**难点**：无

**工作量**：1.5 天

**影响范围**：新增 entity/mapper、ToolController、ToolManage.vue

---

## 六、Skill 体系

### 6.1 Skill 无在线编辑器 — 必须上传 ZIP

**位置**：`SkillController.java` + `SkillManage.vue`

**问题**：
Skill 的内容（SKILL.md + 脚本文件）只能通过 ZIP 导入或远程安装。用户无法在线编辑 Skill 内容，修改一个小错误也需要重新打包上传。

**技术设计**：
- 后端：已有 `SkillStorageService` 的文件 CRUD 接口（`getSkillFiles`、`readSkillFile`、`createSkillFile`、`updateSkillFile`、`deleteSkillFile`），前端只需对接
- 前端：`SkillDetail.vue` 已有文件树编辑器，确保功能完整（Markdown 编辑 + 预览 + 保存）

**难点**：文件树的实时保存与冲突处理

**工作量**：1 天（后端已就绪，主要前端对接）

**影响范围**：SkillDetail.vue

---

### 6.2 Skill 依赖的工具/MCP 未自动绑定

**位置**：`SkillPrepMiddleware.java` + `AgentDetail.vue`

**问题**：
Skill 依赖特定工具或 MCP Server（通过 `toolIds`/`mcpServerIds` 声明），但 Agent 绑定 Skill 时，不会自动检查或提示依赖的工具是否已绑定。用户可能绑定了 Skill 但忘了绑定其依赖的工具，导致 Skill 执行失败。

**技术设计**：
- 后端：`AgentService.updateSkills()` 时，检查 Skill 的依赖工具是否已在 Agent 的工具绑定中，返回缺失的依赖列表（warning 级别，不阻断）
- 前端：绑定 Skill 时，如果依赖工具未绑定，显示黄色提示"该 Skill 依赖以下工具：xxx，请先绑定"

**难点**：依赖工具的匹配（工具名 vs 工具 ID）

**工作量**：1.5 天

**影响范围**：AgentService、AgentDetail.vue

---

### 6.3 Skill 无版本管理

**位置**：`Skill` entity

**问题**：
Skill 更新后旧版本丢失，无法回滚。与 Agent 的版本管理（draft/publish/restore）相比，Skill 缺乏版本控制。

**技术设计**：
- 后端：新增 `skill_version` 表，记录每次更新的完整快照；Skill 增加 `version` 字段（已有）；新增版本列表/恢复接口
- 前端：Skill 详情页增加版本历史面板

**难点**：Skill 文件的版本快照存储（MinIO 中的文件版本）

**工作量**：3 天

**影响范围**：Skill entity、新增 skill_version entity、SkillController、SkillDetail.vue

---

## 七、评估与实验系统

### 7.1 评估实验无法与 Agent 对话直接关联

**位置**：`EvalExperimentController.java`

**问题**：
评估实验使用独立的评估聊天服务（`EvalChatService`），与 Agent 的实际对话服务是两套独立流程。评估结果可能与 Agent 实际表现不一致（模型版本、工具配置差异）。

**设计思路**：
评估实验应复用 Agent 的实际对话链路（中间件链），确保评估结果与真实场景一致。具体方案需要评估 `EvalChatService` 与 `ChatService` 的差异后再定。

**难点**：评估需要批量执行，直接复用对话链路可能影响在线服务

**工作量**：待评估，预估 5 天

**影响范围**：EvalChatService、ChatService、评估实验整体架构

---

### 7.2 评估数据集无自动更新机制

**位置**：`EvalDatasetController.java`

**问题**：
评估数据集是静态的，不会随知识库内容更新而更新。当知识库新增文档后，评估基准（benchmark）可能过时。

**设计思路**：
知识库文档变更时，触发 benchmark 的增量更新（基于新增文档生成新的 QA 对）。需要与知识库的增量更新机制联动。

**难点**：自动生成的 QA 对质量控制

**工作量**：3 天

**影响范围**：EvalDatasetService、KnowledgeService、异步任务系统

---

## 八、可观测性与运维

### 8.1 对话 Token 用量无实时统计

**位置**：`ChatServiceCore.java` + `ChatSession` entity

**问题**：
`ChatSession` 有 `totalTokens` 字段，但更新存在竞态条件（v0.9 已指出），且用户在对话过程中无法实时看到本次对话的 Token 消耗。用户无法控制 Token 成本。

**技术设计**：
- 后端：每次 LLM 调用后，从 response metadata 中提取 token usage，累加到 session 的 `totalTokens`（使用 SQL 原子操作 `UPDATE chat_session SET total_tokens = total_tokens + #{delta}`）
- 前端：对话底部状态栏显示"本次对话：输入 xxx tokens / 输出 xxx tokens"

**难点**：流式响应的 token 统计（部分模型在流式结束时才返回 usage）

**工作量**：2 天

**影响范围**：ChatServiceCore、ChatSessionMapper、Chat.vue

---

### 8.2 LLM 调用无限流/预算控制

**位置**：`ChatServiceCore.java` + `ModelProvider` entity

**问题**：
没有 Token 预算控制机制。用户可以无限调用 LLM，无法设置每月/每日 Token 上限，存在成本失控风险。

**设计思路**：
- `ModelProvider` 或 `User` 增加 `tokenBudget` 配置（月度上限）
- 每次 LLM 调用前检查累计用量，超限时拒绝并提示
- 新增 `token_usage_daily` 表记录每日用量

**难点**：流式调用的 token 统计延迟（调用开始时不知道最终消耗多少）

**工作量**：3 天

**影响范围**：ModelProvider/User entity、ChatServiceCore、新增用量统计表

---

### 8.3 系统健康检查不完整

**位置**：`SystemConfigController.java` health

**问题**：
当前健康检查仅返回简单的 `{"status": "UP"}`，不检查下游依赖（PostgreSQL、Redis、MinIO、Milvus、Neo4j）的连通性。运维人员无法通过健康检查判断系统真实状态。

**技术设计**：
- 后端：`/api/system-config/health` 扩展为详细健康检查，逐个 ping 下游依赖，返回每个组件的状态（UP/DOWN/DEGRADED）和响应时间
- 敏感信息脱敏（不暴露连接字符串）

**难点**：无

**工作量**：1 天

**影响范围**：SystemConfigController

---

## 九、用户与权限体系

### 9.1 无多租户/团队协作支持

**位置**：全系统

**问题**：
当前系统是单用户模式（admin + 普通用户），所有资源（Agent、知识库、工具）按 userId 隔离。无法支持团队协作（多人共享 Agent、知识库协同编辑）。

**设计思路**（远期）：
- 引入 `team` / `workspace` 概念
- 资源归属从 `userId` 扩展为 `teamId + userId`
- 权限模型：Owner > Admin > Editor > Viewer
- 知识库已有 `knowledge_member` 表，可作为权限模型的基础

**难点**：权限模型的全面改造；现有数据的迁移

**工作量**：15-20 天（远期规划）

**影响范围**：全系统

---

### 9.2 API Key 认证缺失

**位置**：`SaTokenConfig.java`

**问题**：
系统只支持 Session 认证（Sa-Token），不支持 API Key 认证。外部系统（如第三方应用、自动化脚本）无法通过 API Key 调用 LightBot 的对话接口。

**设计思路**：
- 新增 `api_key` 表（userId, keyHash, name, permissions, expiresAt）
- 请求拦截器中优先检查 `Authorization: Bearer sk-xxx` 格式的 API Key
- API Key 权限范围：chat-only / full-access

**难点**：API Key 的安全管理（哈希存储、限流、过期）

**工作量**：3 天

**影响范围**：新增 entity、SaTokenConfig、认证拦截器

---

## 十、前端交互体验

### 10.1 对话页面无快捷键支持

**位置**：`Chat.vue`

**问题**：
对话页面没有键盘快捷键。用户必须用鼠标点击发送按钮、切换 Agent、新建对话等操作。

**技术设计**：
- `Enter` 发送消息（已有）
- `Ctrl+Shift+N` 新建对话
- `Ctrl+Shift+O` 打开/关闭侧边栏
- `Ctrl+/` 聚焦输入框
- `Escape` 停止生成 / 关闭弹窗

**难点**：快捷键冲突检测（与浏览器/输入法快捷键）

**工作量**：1 天

**影响范围**：Chat.vue、MainLayout.vue

---

### 10.2 对话输入框无历史消息回溯

**位置**：`Chat.vue` 输入框

**问题**：
输入框不支持上下键翻阅历史发送的消息。在调试场景中，用户经常需要重复发送类似内容。

**技术设计**：
- 前端：维护 `inputHistory` 数组（当前会话的已发送消息），上下键翻阅，`Escape` 清空
- 历史仅保留当前会话，不跨会话

**难点**：无

**工作量**：0.5 天

**影响范围**：Chat.vue

---

### 10.3 巨型组件可维护性差

**位置**：`AgentDetail.vue`（5400+ 行）、`Chat.vue`（2850+ 行）、`KnowledgeDetail.vue`（3500+ 行）、`WorkflowEdit.vue`（3400+ 行）

**问题**：
4 个核心页面都是单文件组件，代码量均超过 2800 行。维护困难，每次修改都需要在大量代码中定位，容易引入副作用。

**设计思路**：
按功能域拆分为 composable + 子组件：
- `AgentDetail.vue` → `useAgentConfig` + `useAgentBinding` + `useAgentVersion` + 子组件（BasicInfoPanel、BindingTabs、VersionDrawer）
- `Chat.vue` → `useChatMessages` + `useChatStream` + `useChatEdit` + 子组件（MessageList、ChatInput、AgentSelector）
- `KnowledgeDetail.vue` → `useDocuments` + `useChunks` + `useGraph` + 子组件
- `WorkflowEdit.vue` → `useWorkflowCanvas` + `useNodeConfig` + `useWorkflowTest` + 子组件

**难点**：拆分过程中的状态提升和事件传递；避免过度拆分导致文件碎片化

**工作量**：每个 3-5 天，共 12-20 天（持续重构）

**影响范围**：4 个核心页面及相关组件

---

### 10.4 深色模式缺失

**位置**：`App.vue` + 全局 CSS

**问题**：
系统只有浅色主题，不支持深色模式。长时间使用时眼睛疲劳。

**设计思路**：
- 使用 CSS 变量管理所有颜色（已有部分基础）
- `App.vue` 的 `ConfigProvider` 中动态切换 `algorithm: theme.darkAlgorithm`
- 用户偏好存储在 `localStorage`

**难点**：部分组件内联样式需要改造为 CSS 变量

**工作量**：5 天

**影响范围**：App.vue、全局 CSS、所有组件的内联样式

---

## 附录：优先级排序

### P0 — 业务核心缺陷（1-2 周）

| 编号 | 问题 | 工作量 | 理由 |
|------|------|--------|------|
| 1.6 | 多模态图片理解未暴露 | 2d | 浪费已有模型能力 |
| 5.1 | API 工具不支持自动执行 | 5d | 创建了但无法用，功能缺失 |
| 1.1 | 对话消息引用/回复 | 2d | 长对话基本需求 |
| 1.7 | 会话列表无分页 | 1d | 会话多了找不到 |
| 8.1 | Token 用量实时统计 | 2d | 成本控制基本需求 |

### P1 — 体验优化（2-4 周）

| 编号 | 问题 | 工作量 | 理由 |
|------|------|--------|------|
| 1.2 | 对话消息搜索 | 3d | 历史信息查找效率 |
| 1.5 | 对话导出 | 1.5d | 知识沉淀需求 |
| 2.1 | Agent 复制/克隆 | 1d | 配置效率 |
| 2.5 | SubAgent 执行可观测 | 3d | 调试需求 |
| 3.2 | 重复文档检测 | 1.5d | 存储浪费 |
| 3.3 | RAG 反馈闭环 | 1.5d | 检索质量改进 |
| 4.1 | 条件分支默认路径校验 | 0.5d | 工作流健壮性 |
| 5.2 | MCP 工具热更新 | 3d | 运维效率 |
| 6.2 | Skill 依赖自动检查 | 1.5d | 配置正确性 |
| 8.3 | 完整健康检查 | 1d | 运维基本需求 |
| 10.1 | 快捷键支持 | 1d | 交互效率 |
| 10.2 | 输入框历史回溯 | 0.5d | 交互效率 |

### P2 — 功能增强（1-2 月）

| 编号 | 问题 | 工作量 | 理由 |
|------|------|--------|------|
| 1.3 | 消息收藏/标记 | 1.5d | 知识管理 |
| 1.4 | 会话标签/分组 | 2d | 会话管理 |
| 2.2 | Agent 导入/导出 | 2d | 团队协作 |
| 2.3 | 系统提示词变量预览 | 1d | 配置体验 |
| 2.4 | Agent 版本对比 | 2d | 版本管理 |
| 3.1 | 知识库增量更新 | 3d | 自动化运维 |
| 3.4 | 查询参数友好配置 | 1d | 用户体验 |
| 4.3 | 工作流变量面板 | 2d | 调试效率 |
| 5.3 | 工具测试历史 | 1.5d | 调试效率 |
| 6.1 | Skill 在线编辑 | 1d | 编辑效率 |
| 6.3 | Skill 版本管理 | 3d | 版本控制 |
| 8.2 | Token 限流/预算 | 3d | 成本控制 |
| 9.2 | API Key 认证 | 3d | 外部集成 |
| 10.4 | 深色模式 | 5d | 用户体验 |

### P3 — 架构演进（持续）

| 编号 | 问题 | 工作量 | 理由 |
|------|------|--------|------|
| 3.5 | 知识图谱与 RAG 融合 | 5d | 检索质量提升 |
| 4.2 | 人工审批节点 | 5d | 业务流程扩展 |
| 4.4 | 子流程/模块化 | 5d | 工作流复用 |
| 7.1 | 评估与对话链路统一 | 5d | 评估准确性 |
| 7.2 | 评估数据集自动更新 | 3d | 评估自动化 |
| 9.1 | 多租户/团队协作 | 15-20d | 企业级需求 |
| 10.3 | 巨型组件拆分 | 12-20d | 可维护性 |

---

**工作量汇总**：

| 优先级 | 工作量 | 建议周期 |
|--------|--------|----------|
| P0 | ~11 天 | 2 周 |
| P1 | ~23 天 | 4 周 |
| P2 | ~28 天 | 6 周 |
| P3 | ~50-58 天 | 持续 |
| **合计** | **~112-120 天** | — |
