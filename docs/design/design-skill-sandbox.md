# LightBot Skill & Sandbox 模块设计文档

> 日期：2026-06-18
> 参考：Yuxi Skill 系统 + Yuxi Sandbox 模块
> 目标：为 LightBot 设计完整的 Skill 管理体系与沙箱文件系统
> 最后更新：2026-06-21 — 补充实现状态追踪

---

## 一、需求分析

### 1.1 Skill 模块核心需求

| 需求 | 说明 | 优先级 | 实现状态 |
|------|------|--------|---------|
| Skill 文件化存储 | Skill 内容从 DB 迁移到 MinIO，支持 SKILL.md + 附属文件 | P0 | ✅ 已实现 |
| ZIP 上传安装 | 用户上传 ZIP 包即可安装 Skill，包含 SKILL.md 和附属资源 | P0 | ✅ 已实现（两阶段） |
| SKILL.md frontmatter 解析 | 声明式管理 Skill 的元数据和依赖关系 | P0 | ✅ 已实现 |
| Agent 绑定 Skill | Agent 编排页可选择绑定 Skill，已有能力需保留 | P0（已有） | ✅ 已实现 |
| Skill 懒激活 | Agent 运行时按需读取 SKILL.md 并激活依赖，而非全量注入 | P1 | ✅ 已实现 |
| Skill 依赖声明 | Skill 可声明对 Tool、MCP Server、其他 Skill 的依赖 | P1 | ✅ 已实现 |
| Skill 依赖闭包展开 | A→B→C 传递依赖自动展开，含循环检测 | P1 | ✅ 已实现 |
| 导出 ZIP | 将 Skill 导出为 ZIP 包便于分享 | P2 | ✅ 已实现 |
| 远程安装 | 从 GitHub/ModelScope/skills.sh 安装 Skill | P1 | ✅ 已实现（超出设计） |
| Skill 版本管理 | 支持版本号 + 内容快照，内置 Skill 更新可检测变更 | P2 | ⚠️ 部分实现（版本号+content_hash） |
| 权限隔离 | 全局/用户两级共享范围（部门级后续扩展） | P2 | ⚠️ 部分实现（scope 字段已有） |

### 1.2 Sandbox 模块核心需求

| 需求 | 说明 | 优先级 | 实现状态 |
|------|------|--------|---------|
| 会话级工作区 | 每个对话有独立的文件读写空间（MinIO prefix 隔离） | P1 | ⚠️ 基础设施就绪，工具未实现 |
| Skill 只读文件系统 | Agent 只能读取已绑定 Skill 的文件，不可写入 | P1 | ✅ 已实现（SandboxPathValidator） |
| 路径安全校验 | 防止 `..` 路径遍历攻击，读写白名单控制 | P1 | ✅ 已实现 |
| 用户级共享工作区 | 同一用户的跨对话共享文件空间 | P2 | ❌ 未实现 |
| 文件操作 Agent 工具 | read_file / write_file / list_files 等沙箱工具 | P2 | ❌ 未实现 |
| 容器沙箱（命令执行） | Docker/K8s 容器内执行代码，完全隔离 | P3 | ❌ 未实现（远期规划） |

### 1.3 为什么 Skill 和 Sandbox 应一起做

1. **Skill 文件化是 Sandbox 的前置**：Skill 内容迁移到 MinIO 后，自然形成 `skills/{slug}/` 的文件结构，这就是 Sandbox 的只读区域
2. **懒激活依赖 Sandbox**：Agent 通过 `read_skill` 工具读取 SKILL.md 触发激活，这个工具本身就是 Sandbox 文件工具的一种
3. **统一路径体系**：Skill 目录（只读）和工作区目录（读写）共用同一套 MinIO prefix 体系和路径安全校验
4. **避免重复建设**：如果分开做，Skill 文件存储和 Sandbox 文件系统会各自实现一套 MinIO 操作封装

---

## 二、技术设计思路

### 2.1 核心设计思想：分层存储 + 渐进式披露

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent 运行时                               │
│                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐  │
│  │ Skill 摘要    │    │ 工具列表      │    │ 工作区文件    │  │
│  │ (System Prompt)│   │ (按需加载)    │    │ (读写操作)    │  │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘  │
│         │                   │                   │           │
│         ▼                   ▼                   ▼           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Sandbox 虚拟文件系统                      │   │
│  │  /skills/     → MinIO skills/{slug}/    (只读)       │   │
│  │  /workspace/  → MinIO threads/{sid}/    (读写)       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

**渐进式披露**（对齐 Yuxi）：
- Agent 启动时只看到 Skill 名称和描述（注入 System Prompt）
- Agent 决定使用某个 Skill 时，调用 `read_skill` 工具读取 SKILL.md 全文
- 读取动作触发懒激活，后续请求自动加载该 Skill 的依赖工具
- 好处：减少无关工具污染上下文，降低 token 消耗

### 2.2 双层存储模式

对齐 Yuxi 的 **DB 索引 + 文件内容** 双层存储：

| 层级 | 存储内容 | 介质 |
|------|---------|------|
| 索引层 | slug、name、description、依赖列表、权限、enabled | PostgreSQL（skill 表） |
| 内容层 | SKILL.md 正文、附属脚本、参考资料 | MinIO `skills/{slug}/` |

桥接方式：`skill.object_prefix` 字段指向 MinIO 路径前缀。

### 2.3 两阶段导入模式

对齐 Yuxi 的草稿→确认流程，防止导入失败留下脏数据：

```
用户上传 ZIP
    │
    ▼
┌──────────────────────┐
│ 阶段一：暂存草稿       │
│ MinIO skill_drafts/  │
│   {draftId}/         │
│     SKILL.md + 文件   │
│ DB: 无记录            │
│ TTL: 1 小时自动清理    │
└──────────┬───────────┘
           │ 用户确认
           ▼
┌──────────────────────┐
│ 阶段二：原子提交       │
│ 1. 移动 MinIO 对象    │
│    drafts/ → skills/ │
│ 2. 插入 DB 记录       │
│ 3. 失败则回滚 MinIO   │
└──────────────────────┘
```

**实现差异**：设计文档描述草稿目录含 `metadata.json`，实际实现中 `stageDraft()` 直接解析 SKILL.md frontmatter 返回预览，不生成独立的 metadata.json 文件。

---

## 三、架构方案

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层                                    │
│  SkillManage.vue    AgentDetail.vue    Chat.vue                  │
│  (Skill CRUD)       (Skill 绑定)       (skill_active 事件展示)    │
│  SkillImportModal   SkillRemoteInstallModal                      │
│  (ZIP 导入弹窗)     (远程安装弹窗)                                │
└────────────┬──────────────┬──────────────────┬──────────────────┘
             │              │                  │
             ▼              ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Controller 层                              │
│  SkillController         AgentController                         │
│  (CRUD + ZIP 导入导出     (绑定管理)                              │
│   + 远程安装 API)                                                │
└────────────┬──────────────┬──────────────────────────────────────┘
             │              │
             ▼              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Service 层                                │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐                      │
│  │ SkillServiceImpl │  │ AgentServiceImpl │                      │
│  │ (CRUD + 导入导出  │  │ (绑定管理)       │                      │
│  │  + 远程安装提交)  │  │                  │                      │
│  └────────┬────────┘  └────────┬────────┘                      │
│           │                    │                                │
│           ▼                    ▼                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  SkillStorageService  │  GitHubSkillService               │  │
│  │  (MinIO 读写/frontmatter │  (GitHub/ModelScope/skills.sh  │  │
│  │   解析/ZIP 导入导出)     │   远程 Skill 安装)              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  SkillActivationStore          SandboxPathValidator       │  │
│  │  (Redis 激活状态持久化)          (路径安全校验)              │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     中间件链（Chat 流程）                         │
│                                                                 │
│  InitMiddleware                                                   │
│    → SkillPrepMiddleware   ← 注入 Skill 摘要到 System Prompt     │
│    → MessageMiddleware     ← 拼接完整 System Prompt              │
│    → ToolPrepMiddleware    ← 合并 Skill 依赖的 Tool/MCP          │
│    → TraceMiddleware                                                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      存储层                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ PostgreSQL    │  │ MinIO        │  │ Redis                │  │
│  │ skill 表      │  │ skills/      │  │ skill:activated:{sid}│  │
│  │ agent.config  │  │ skill_drafts/│  │ 激活状态（24h TTL）   │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 MinIO 路径规范

```
{bucket}/
├── skills/                              ← Skill 根目录（全局，只读）
│   ├── {slug}/
│   │   ├── SKILL.md                     ← 必需：frontmatter + 指令正文
│   │   ├── references/                  ← 可选：模板、示例文件
│   │   └── scripts/                     ← 可选：附属脚本
│   └── ...
├── skill_drafts/                        ← 导入草稿（临时）
│   └── {draftId}/
│       └── {slug}/
│           └── SKILL.md + 附属文件
└── threads/                             ← 会话级目录（Level 3，基础设施就绪）
    ├── {sessionId}/
    │   ├── workspace/                   ← 会话工作区（读写）
    │   │   ├── input/                   ← 用户上传文件
    │   │   └── output/                  ← Agent 生成产物
    │   └── skills/                      ← 会话级 Skill 副本（只读，可选）
    └── shared/
        └── {userId}/
            └── workspace/               ← 用户级共享工作区（读写）
```

### 3.3 SKILL.md 格式

```markdown
---
name: deep-research
slug: deep-research
description: 多轮检索 + 结构化整理，生成深度研究报告
version: "1.0.0"
tool_dependencies:
  - web_search
mcp_dependencies: []
skill_dependencies: []
---

# 指令内容（注入 System Prompt）

你是一个深度研究助手。当用户要求深度分析某个主题时，执行以下步骤：

1. 使用 web_search 工具进行多轮检索
2. 整理检索结果，提取关键信息
3. 生成结构化研究报告
...
```

**解析规则**：
- frontmatter 用 `---` 分隔，使用 SnakeYAML（Spring Boot 自带）解析
- 正文部分作为 `promptTemplate` 注入 System Prompt
- `slug` 必须匹配 `^[a-z0-9]+(-[a-z0-9]+)*$`
- 解析失败时降级：整个文件作为 promptTemplate，依赖从 DB 读取

### 3.4 数据模型

#### 3.4.1 Skill 表结构

```sql
CREATE TABLE skill (
    id                  BIGINT          NOT NULL,
    user_id             BIGINT,
    name                VARCHAR(128)    NOT NULL,
    display_name        VARCHAR(128),
    description         TEXT,
    prompt_template     TEXT,
    scope               VARCHAR(20)     NOT NULL DEFAULT 'global',
    status              VARCHAR(20)     NOT NULL DEFAULT 'enabled',
    is_builtin          SMALLINT        NOT NULL DEFAULT 0,
    -- 沙箱相关字段（Level 1 新增）
    slug                VARCHAR(128),
    object_prefix       VARCHAR(256),
    version             VARCHAR(64)     DEFAULT '1.0.0',
    skill_dependencies  JSONB           DEFAULT '[]',
    source_type         VARCHAR(20)     DEFAULT 'builtin',
    content_hash        VARCHAR(128),
    tool_ids            JSONB           DEFAULT '[]',
    mcp_server_ids      JSONB           DEFAULT '[]',
    -- 通用字段
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_skill_slug ON skill(slug);
CREATE INDEX idx_skill_source_type ON skill(source_type);
CREATE INDEX idx_skill_is_builtin ON skill(is_builtin);
CREATE INDEX idx_skill_scope ON skill(scope);
```

#### 3.4.2 Redis 结构（SkillActivationStore）

- **Key**: `skill:activated:{sessionId}`
- **Value**: JSON 数组 `["deep-research", "knowledge-grounded-qa"]`
- **TTL**: 24 小时（与会话一致）

#### 3.4.3 字段对照（Yuxi → LightBot）

| Yuxi 字段 | LightBot 字段 | 说明 |
|-----------|--------------|------|
| `slug` | `slug` | 唯一标识 |
| `name` | `name` / `display_name` | 显示名 |
| `description` | 已有（promptTemplate 首行提取） | 描述 |
| `source_type` | `source_type` | 新增 |
| `tool_dependencies` | `tool_ids` | JSON 数组（Long ID vs slug 差异） |
| `mcp_dependencies` | `mcp_server_ids` | JSON 数组 |
| `skill_dependencies` | `skill_dependencies` | 新增（JSON slug 数组） |
| `dir_path` | `object_prefix` | MinIO prefix vs 本地路径 |
| `version` | `version` | 新增 |
| `content_hash` | `content_hash` | 已有 |
| `share_config` | `scope` + `user_id` | 简化为两级 |
| `enabled` | `status` | 复用 CommonStatus 枚举 |

### 3.5 懒激活机制

#### 3.5.1 流程设计

```
会话开始
    │
    ▼
SkillPrepMiddleware.prepare(ctx)
    │
    ├── 1. 从 Redis 加载已激活的 Skill slug 集合
    ├── 2. 读取 Agent 绑定的 Skill 列表
    ├── 3. 注入 Skill 摘要到 System Prompt
    │      "以下技能已绑定到当前 Agent，使用 read_skill 工具读取完整指令后生效：
    │       - deep-research: 多轮检索 + 结构化整理"
    ├── 4. 构建 read_skill / list_skill_files 工具并注册
    │
    ▼
Agent 第一轮对话（模型只看到摘要，无 Skill 依赖工具）
    │
    ▼
Agent 决定使用 deep-research → 调用 read_skill("deep-research")
    │
    ├── ReadSkillTool.execute()
    │   ├── 校验 slug 存在且启用
    │   ├── SkillStorageService.getSkillMarkdown("deep-research")
    │   │   → MinIO getObject
    │   └── 返回 SKILL.md 全文给 Agent
    ├── SkillActivationStore.activate(sessionId, "deep-research")
    │   → Redis SET skill:activated:{sessionId} = ["deep-research"]
    │
    ▼
Agent 第二轮对话
    │
    ▼
ToolPrepMiddleware.prepare(ctx)
    │
    ├── 读取 activatedSkills
    ├── expandSkillClosure(activatedSlugs, depMap) -- DFS + 循环检测
    ├── 合并 toolIds / mcpServerIds 到 mergedToolIds / mergedMcpIds
    ├── toolService.resolveToolCallbacksByIds() → ToolCallback 列表
    ├── 注入到 ToolCallingChatOptions
    │
    ▼
Agent 获得 web_search 等依赖工具，执行深度研究
```

#### 3.5.2 激活状态存储

```java
// Redis 结构
// Key: skill:activated:{sessionId}
// Value: Set<String> activatedSlugs
// TTL: 与会话一致（24h）
```

**选择 Redis 而非 ChatContext 的原因**：
- `ChatContext` 是请求级对象，不跨轮次持久化
- 懒激活需要跨轮次记住已激活的 Skill
- Redis 天然支持 TTL，会话过期自动清理

### 3.6 Sandbox 文件工具

#### 3.6.1 工具清单

| 工具名 | 类型 | 权限 | 实现状态 | 说明 |
|--------|------|------|---------|------|
| `read_skill` | Skill 专用 | 只读 | ✅ 已实现 | 读取 SKILL.md，触发懒激活 |
| `list_skill_files` | Skill 专用 | 只读 | ✅ 已实现 | 列出 Skill 目录下的文件 |
| `read_file` | 通用沙箱 | 读 | ❌ 未实现 | 读取工作区文件 |
| `write_file` | 通用沙箱 | 写 | ❌ 未实现 | 写入工作区文件 |
| `list_files` | 通用沙箱 | 读 | ❌ 未实现 | 列出工作区目录 |
| `upload_file` | 通用沙箱 | 写 | ❌ 未实现 | 上传文件到工作区 |

#### 3.6.2 路径安全设计

```java
public final class SandboxPathValidator {

    private static final List<String> READABLE_ROOTS = List.of("skills/", "threads/");
    private static final List<String> WRITABLE_ROOTS = List.of("threads/"); // skills 只读
    private static final List<String> DRAFT_ROOTS = List.of("skill_drafts/");

    /** 标准化路径 + 安全校验 */
    public static String normalize(String path) {
        String normalized = path.replace("\\", "/");
        // 逐段检查拒绝 ..
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                throw new BizException(ErrorCode.SANDBOX_PATH_VIOLATION);
            }
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /** 校验读权限 */
    public static void checkReadable(String path) { ... }

    /** 校验写权限 */
    public static void checkWritable(String path) { ... }

    /** 校验草稿权限 */
    public static void checkDraft(String path) { ... }
}
```

**实现差异**：设计文档使用原生 `SecurityException`，实际代码使用 `BizException(ErrorCode.SANDBOX_PATH_VIOLATION)`，与项目统一异常体系一致。

### 3.7 Skill 依赖闭包展开

```java
/**
 * ToolPrepMiddleware.expandSkillClosure()
 * DFS 展开 Skill 依赖闭包，含循环检测
 *
 * @param selectedSlugs Agent 绑定的 Skill slug 集合
 * @param dependencyMap  slug → skillDependencies 的映射
 * @return 展开后的完整 Skill slug 集合（拓扑序）
 */
public Set<String> expandSkillClosure(Set<String> selectedSlugs,
                                       Map<String, List<String>> dependencyMap) {
    Set<String> visited = new LinkedHashSet<>();
    Deque<String> stack = new ArrayDeque<>(selectedSlugs);

    while (!stack.isEmpty()) {
        String slug = stack.pop();
        if (!visited.add(slug)) continue; // 已访问或循环
        List<String> deps = dependencyMap.getOrDefault(slug, List.of());
        deps.stream()
            .filter(d -> !visited.contains(d))
            .forEach(stack::push);
    }
    return visited;
}
```

**实现说明**：设计文档描述为独立的 `SkillDependencyResolver` 类，实际实现内联在 `ToolPrepMiddleware` 中。

---

## 四、涉及文件与组件

### 4.1 后端文件（已实现）

| 文件 | 层级 | 说明 |
|------|------|------|
| `util/SandboxPathValidator.java` | 基础 | 路径安全校验工具（读写白名单 + 路径遍历防护） |
| `service/sandbox/SkillStorageService.java` | Service | MinIO Skill 文件读写、frontmatter 解析、ZIP 导入导出 |
| `service/sandbox/GitHubSkillService.java` | Service | 远程 Skill 安装（GitHub API + ModelScope + npx skills find） |
| `service/sandbox/SkillActivationStore.java` | Service | Redis 存储每个会话已激活的 Skill slug 集合 |
| `model/SkillMetadata.java` | Model | SKILL.md frontmatter 解析后的结构化数据 |
| `tool/builtin/ReadSkillTool.java` | Tool | `read_skill` 内置工具：读取 SKILL.md，触发懒激活 |
| `tool/builtin/ListSkillFilesTool.java` | Tool | `list_skill_files` 内置工具：列出 Skill 目录文件 |
| `service/impl/SkillServiceImpl.java` | Service | Skill CRUD + ZIP 导入导出 + 远程安装提交 |
| `controller/SkillController.java` | Controller | REST API：ZIP 导入/导出、远程安装 |
| `service/chat/SkillPrepMiddleware.java` | Middleware | 注入 Skill 摘要到 System Prompt，构建 read_skill 工具 |
| `service/chat/ToolPrepMiddleware.java` | Middleware | 读取已激活 Skill，展开依赖闭包，合并 Tool/MCP |
| `service/chat/ChatContext.java` | Model | 新增 activatedSkills、skillSystemAppendix、skillExtraToolIds |
| `skill/BuiltInSkillRegistrar.java` | Init | 内置 Skill 启动注册器，同步 SKILL.md 到 MinIO |
| `skill/BuiltInSkillDefinitions.java` | Init | 内置 Skill 定义清单 |
| `util/MinioUtil.java` | Util | 底层 MinIO 操作（uploadString, listObjects, copyObject 等） |
| `enums/ErrorCode.java` | Enum | 沙箱相关错误码：SANDBOX_PATH_VIOLATION, SKILL_FILE_NOT_FOUND 等 |

### 4.2 后端未实现文件

| 文件 | 说明 | 前置依赖 |
|------|------|---------|
| `tool/sandbox/ReadFileTool.java` | 读取工作区文件 | Level 3 |
| `tool/sandbox/WriteFileTool.java` | 写入工作区文件 | Level 3 |
| `tool/sandbox/ListFilesTool.java` | 列出工作区目录 | Level 3 |
| `controller/SandboxController.java` | 文件操作 API | Level 3 |
| `service/SandboxService.java` | 文件操作 Service 层 | Level 3 |

### 4.3 前端文件

| 文件 | 说明 |
|------|------|
| `views/SkillManage.vue` | Skill 管理页：CRUD、ZIP 导入、远程安装、导出 ZIP |
| `components/SkillImportModal.vue` | ZIP 导入弹窗：拖拽上传 + 草稿预览 + 确认安装 |
| `components/SkillRemoteInstallModal.vue` | 远程安装弹窗：仓库拉取 + 全局搜索 + 预览确认 |
| `views/AgentDetail.vue` | Agent 编排页 Skill 绑定 Tab |
| `views/Chat.vue` | 对话页展示 skill_active 事件 |
| `components/AgentCapabilityPanel.vue` | 对话中展示已启用 Skill 列表 |
| `api/skill.js` | Skill API 封装（12 个接口） |

---

## 五、SkillController API 端点

| 端点 | 方法 | 说明 | 实现状态 |
|------|------|------|---------|
| `/api/skills` | GET | 获取 Skill 列表 | ✅ |
| `/api/skills/{id}` | GET | 获取 Skill 详情 | ✅ |
| `/api/skills` | POST | 创建 Skill | ✅ |
| `/api/skills/{id}` | PUT | 更新 Skill | ✅ |
| `/api/skills/{id}` | DELETE | 删除 Skill | ✅ |
| `/api/skills/{id}/enabled` | PUT | 启用/禁用 Skill | ✅ |
| `/api/skills/import/preview` | POST | ZIP 导入阶段一：暂存草稿返回预览 | ✅ |
| `/api/skills/import/commit` | POST | ZIP 导入阶段二：确认提交 | ✅ |
| `/api/skills/{id}/export` | GET | 导出 Skill 为 ZIP | ✅ |
| `/api/skills/remote/list` | POST | 列出远程仓库中的 Skill | ✅ |
| `/api/skills/remote/search` | POST | 全局搜索远程 Skill | ✅ |
| `/api/skills/remote/prepare` | POST | 远程安装准备（下载并暂存草稿） | ✅ |
| `/api/skills/remote/commit` | POST | 远程安装确认（提交草稿） | ✅ |

---

## 六、核心难点

### 6.1 懒激活的状态管理

**难点**：LightBot 的 `ChatContext` 是请求级对象，不跨轮次持久化。懒激活需要记住"哪些 Skill 已被激活"。

**方案**：Redis 存储激活状态（SkillActivationStore）

### 6.2 MinIO 与 DB 一致性

**难点**：ZIP 导入时，MinIO 上传成功但 DB 事务回滚，导致孤儿对象。

**方案**：两阶段提交（草稿→确认），删除时先标记 DB 再异步清理 MinIO。

### 6.3 SKILL.md Frontmatter 解析

**难点**：YAML 中可能包含特殊字符，解析失败不能阻断 Skill 加载。

**方案**：正则提取 + SnakeYAML 解析 + 降级兜底（整个文件作为 promptTemplate）。

### 6.4 Spring AI 动态 Tool 注册

**难点**：Spring AI 的 `FunctionCallback` 通常在启动时注册。懒激活需要运行时动态添加 Tool。

**方案**：`ToolPrepMiddleware` 每次请求时重新构建 `ToolCallingChatOptions`，合并已激活 Skill 的依赖 Tool。

### 6.5 ZIP 导入的 Slug 冲突处理

**难点**：用户上传的 ZIP 中 SKILL.md 定义的 slug 可能已存在。

**方案**：冲突时自动追加后缀（`deep-research` → `deep-research-v2`），导入预览阶段展示 slug 映射，允许用户修改。

---

## 七、特点

### 7.1 与 Yuxi 的对齐点

| 能力 | Yuxi 实现 | LightBot 实现 | 状态 |
|------|----------|--------------|------|
| 双层存储 | 文件系统 + DB | MinIO + DB | ✅ 一致 |
| SKILL.md 格式 | pyyaml 解析 frontmatter | SnakeYAML 解析 frontmatter | ✅ 一致 |
| 懒激活 | `read_file` 路径匹配 → State | `read_skill` 工具 → Redis | ✅ 一致 |
| 依赖闭包展开 | DFS + 循环检测 | 同（Java 实现） | ✅ 一致 |
| 两阶段导入 | 草稿目录 + TTL | `skill_drafts/` prefix + TTL | ✅ 一致 |
| 内置 Skill 启动同步 | `init_builtin_skills()` + content_hash | `BuiltInSkillRegistrar` + content_hash | ✅ 一致 |

### 7.2 与 Yuxi 的差异点

| 维度 | Yuxi | LightBot | 理由 |
|------|------|----------|------|
| 存储层 | 宿主机文件系统 | MinIO 对象存储 | LightBot 已有 MinIO 基础设施 |
| 依赖引用 | slug 字符串 | Long ID + slug 混合 | 渐进迁移，不破坏现有 Agent 绑定 |
| 激活状态 | LangGraph State | Redis | Java 生态更自然，支持跨实例 |
| 沙箱执行 | 容器沙箱（agent-sandbox） | Level 1-2 先行，Level 3 按需 | 降低初期复杂度 |
| 权限模型 | global/department/user 三级 | global/user 两级 | 暂无多部门需求 |
| 远程安装 | npx skills CLI | GitHub API + ModelScope + npx skills find | 超出设计，三种来源 |

### 7.3 代码实现超出设计文档的部分

| 实现点 | 说明 |
|--------|------|
| **GitHubSkillService** | 设计文档提到"远程安装：GitHub API 下载 ZIP"，实际支持 GitHub 仓库递归扫描、ModelScope git clone、npx skills find 全局搜索三种来源 |
| **ModelScope 支持** | 设计文档未提及，代码中实现了 `listModelScopeSkills()` 和 `prepareModelScopeInstall()` |
| **AgentCapabilityPanel** | 前端对话中展示已启用 Skill 的组件，设计文档未详细描述 |
| **SkillRemoteInstallModal** | 远程安装弹窗（仓库拉取 + 全局搜索），设计文档未详细描述 |

### 7.4 设计文档描述但未实现的部分

| 设计点 | 当前状态 | 说明 |
|--------|---------|------|
| 会话工作区工具（read_file/write_file/list_files） | ❌ 未实现 | SandboxPathValidator 已支持 `threads/` 路径校验，但 Tool 类未创建 |
| 用户级共享工作区 | ❌ 未实现 | 路径规范已设计，代码未实现 |
| Caffeine 本地缓存 | ❌ 未实现 | SkillStorageService 直接读 MinIO，无本地缓存层 |
| SkillDependencyResolver 独立类 | 内联在 ToolPrepMiddleware | expandSkillClosure() 直接在中间件内实现 |
| SandboxController / SandboxService | ❌ 未实现 | 文件操作 API 端点未实现 |
| metadata.json 草稿文件 | ❌ 未实现 | stageDraft() 直接解析 SKILL.md，不生成独立文件 |
| tool_dependencies 自动绑定 | ❌ 未实现 | 导入时 toolIds 设为 "[]"，未自动关联 |

### 7.5 分级实施路线

| 阶段 | 周期 | 产出 | 实现状态 |
|------|------|------|---------|
| **Level 1** | 2 周 | MinioUtil 扩展 + SkillStorageService + SKILL.md 解析 + Skill 内容迁移 + ZIP 导入导出 | ✅ 已完成 |
| **Level 2** | 1 周 | read_skill 懒激活 + 激活状态 Redis 存储 + 依赖闭包展开 + SandboxPathValidator | ✅ 已完成 |
| **Level 3** | 1 周 | 会话工作区（read_file / write_file / list_files）+ 用户共享工作区 | ❌ 未开始 |
| **Level 4** | 4-6 周 | 容器沙箱（Docker/K8s）+ 命令执行 + Sandbox Provisioner 服务 | ❌ 未开始（远期） |

---

## 八、前端交互设计

### 8.1 Skill 管理页

- 展示全局 Skill 列表，显示 slug、version、sourceType（builtin/upload/remote）标签
- 顶部操作栏：搜索、刷新、新增 Skill、ZIP 导入、远程安装
- 每个 Skill 卡片支持：查看详情、启用/禁用、导出 ZIP、删除
- 内置 Skill 不可编辑/删除

### 8.2 ZIP 导入流程（SkillImportModal）

两步骤流程：
1. **上传**：拖拽 ZIP 文件（限制 10MB），调用 `/api/skills/import/preview`
2. **预览确认**：展示 slug（可修改）、名称、描述、版本、依赖工具、依赖 Skill、文件列表。确认后调用 `/api/skills/import/commit`

### 8.3 远程安装流程（SkillRemoteInstallModal）

两个 Tab：
1. **按仓库拉取**：输入 `owner/repo` 或 GitHub URL，调用 `/api/skills/remote/list` 获取 SKILL.md 列表，多选后 prepare + commit
2. **全局搜索发现**：输入关键字，调用 `/api/skills/remote/search`（npx skills find），选择后走相同 prepare + commit 流程
3. 支持 ModelScope 单个 Skill 地址（自动识别，单选）

### 8.4 Agent 编排页 Skill 绑定

- Skill Tab 展示已绑定 Skill 列表（最多 10 个）
- 从全局 Skill 库中选择绑定
- 发布版本时绑定关系写入版本快照

### 8.5 对话中的 Skill 展示

- Chat.vue 监听 SSE 事件流，识别 `skill_active` 类型事件
- 通过 `AgentCapabilityPanel` 组件展示"已启用 N 个 Skill"，可展开查看每个 Skill 的 displayName、slug、是否内置

---

## 九、关键问题 Q&A

### Q1：不做容器沙箱，能正常使用 Skill 相关功能吗？

**能。** 容器沙箱（Level 4）和 Skill 核心功能是完全独立的两件事。

| 功能层级 | 是否需要容器沙箱 | 说明 |
|---------|:---:|------|
| Skill 文件存储（MinIO） | 否 | 纯 MinIO 对象读写 |
| SKILL.md frontmatter 解析 | 否 | 纯 Java 字符串解析 |
| ZIP 导入/导出 | 否 | MinIO + ZIP 流处理 |
| Agent 绑定 Skill | 否 | DB 字段读写 |
| Skill 懒激活（read_skill） | 否 | MinIO getObject + Redis 状态 |
| Skill 依赖闭包展开 | 否 | 纯内存计算 |
| 远程安装（GitHub/ModelScope） | 否 | HTTP API + MinIO |
| **会话工作区文件读写** | 否 | MinIO prefix 隔离即可 |
| **代码执行（Python/Shell）** | **是** | 需要容器隔离 |

### Q2：除了 Skill，还有什么会用到文件沙箱？

| 场景 | 当前实现 | 是否需要沙箱 | 说明 |
|------|---------|:---:|------|
| **Skill 文件** | MinIO `skills/{slug}/` | 否 | 已迁移到 MinIO |
| **聊天附件** | MinIO `chat/{agentId}/{sessionId}/{attachmentId}` | 否 | 已有，无需改动 |
| **知识库文档** | MinIO `knowledge/{knowledgeId}/documents/` | 否 | 已有，无需改动 |
| **工作流脚本节点** | JVM 内 Nashorn 引擎直接执行 | **建议隔离** | 风险：脚本可访问 JVM 内所有类 |
| **Agent 代码执行** | 无（未实现） | **是** | 需要容器沙箱 |

---

*文档生成时间: 2026-06-18*
*基于 Yuxi Skill 系统 + Sandbox 模块分析，结合 LightBot 现有架构设计*
*最后更新: 2026-06-21 — 补充实现状态追踪、API 端点清单、已实现/未实现差异分析*
