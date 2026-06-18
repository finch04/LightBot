# LightBot Skill & Sandbox 模块设计文档

> 日期：2026-06-18
> 参考：Yuxi Skill 系统 + Yuxi Sandbox 模块
> 目标：为 LightBot 设计完整的 Skill 管理体系与沙箱文件系统

---

## 一、需求分析

### 1.1 Skill 模块核心需求

| 需求 | 说明 | 优先级 |
|------|------|--------|
| Skill 文件化存储 | Skill 内容从 DB 迁移到 MinIO，支持 SKILL.md + 附属文件 | P0 |
| ZIP 上传安装 | 用户上传 ZIP 包即可安装 Skill，包含 SKILL.md 和附属资源 | P0 |
| SKILL.md frontmatter 解析 | 声明式管理 Skill 的元数据和依赖关系 | P0 |
| Agent 绑定 Skill | Agent 编排页可选择绑定 Skill，已有能力需保留 | P0（已有） |
| Skill 懒激活 | Agent 运行时按需读取 SKILL.md 并激活依赖，而非全量注入 | P1 |
| Skill 依赖声明 | Skill 可声明对 Tool、MCP Server、其他 Skill 的依赖 | P1 |
| Skill 依赖闭包展开 | A→B→C 传递依赖自动展开，含循环检测 | P1 |
| 导出 ZIP | 将 Skill 导出为 ZIP 包便于分享 | P2 |
| Skill 版本管理 | 支持版本号 + 内容快照，内置 Skill 更新可检测变更 | P2 |
| 权限隔离 | 全局/用户两级共享范围（部门级后续扩展） | P2 |

### 1.2 Sandbox 模块核心需求

| 需求 | 说明 | 优先级 |
|------|------|--------|
| 会话级工作区 | 每个对话有独立的文件读写空间（MinIO prefix 隔离） | P1 |
| Skill 只读文件系统 | Agent 只能读取已绑定 Skill 的文件，不可写入 | P1 |
| 路径安全校验 | 防止 `..` 路径遍历攻击，读写白名单控制 | P1 |
| 用户级共享工作区 | 同一用户的跨对话共享文件空间 | P2 |
| 文件操作 Agent 工具 | read_file / write_file / list_files 等沙箱工具 | P2 |
| 容器沙箱（命令执行） | Docker/K8s 容器内执行代码，完全隔离 | P3 |

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
│     metadata.json    │
│     skill-content/   │
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

---

## 三、架构方案

### 3.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端层                                    │
│  SkillManage.vue    AgentDetail.vue    Chat.vue                  │
│  (Skill CRUD)       (Skill 绑定)       (沙箱文件操作)             │
└────────────┬──────────────┬──────────────────┬──────────────────┘
             │              │                  │
             ▼              ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Controller 层                              │
│  SkillController         AgentController     SandboxController   │
│  (CRUD + ZIP 导入导出)    (绑定管理)           (文件操作 API)      │
└────────────┬──────────────┬──────────────────┬──────────────────┘
             │              │                  │
             ▼              ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Service 层                                │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────────┐  │
│  │ SkillService     │  │ AgentService     │  │ SandboxService │  │
│  │ (CRUD + 导入导出) │  │ (绑定管理)       │  │ (文件操作)      │  │
│  └────────┬────────┘  └────────┬────────┘  └───────┬────────┘  │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                 SkillStorageService                        │  │
│  │  (MinIO skills/ 读写、frontmatter 解析、ZIP 解压)          │  │
│  └────────────────────────┬─────────────────────────────────┘  │
│                           │                                     │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                 SandboxPathValidator                       │  │
│  │  (路径标准化、遍历攻击防护、读写白名单)                      │  │
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
│  │ skill 表      │  │ skills/      │  │ 激活状态缓存          │  │
│  │ agent.config  │  │ skill_drafts/│  │ Caffeine 本地缓存     │  │
│  │               │  │ threads/     │  │                      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 MinIO 路径规范

```
{bucket}/
├── skills/                              ← Skill 根目录（全局）
│   ├── {slug}/
│   │   ├── SKILL.md                     ← 必需：frontmatter + 指令正文
│   │   ├── references/                  ← 可选：模板、示例文件
│   │   └── scripts/                     ← 可选：附属脚本
│   └── ...
├── skill_drafts/                        ← 导入草稿（TTL 1 小时）
│   └── {draftId}/
│       ├── metadata.json
│       └── skill-content/
├── threads/                             ← 会话级目录
│   ├── {sessionId}/
│   │   ├── workspace/                   ← 会话工作区（读写）
│   │   │   ├── input/                   ← 用户上传文件
│   │   │   └── output/                  ← Agent 生成产物
│   │   └── skills/                      ← 会话级 Skill 副本（只读，可选）
│   └── shared/
│       └── {userId}/
│           └── workspace/               ← 用户级共享工作区（读写）
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

### 3.4 数据模型变更

#### 3.4.1 Skill 表新增字段

```sql
ALTER TABLE skill ADD COLUMN object_prefix VARCHAR(256);
-- MinIO 路径前缀: "skills/{slug}/"

ALTER TABLE skill ADD COLUMN version VARCHAR(64) DEFAULT '1.0.0';
-- 语义版本号

ALTER TABLE skill ADD COLUMN skill_dependencies JSONB DEFAULT '[]';
-- 依赖其他 Skill 的 slug 列表: ["other-skill", "another-skill"]

ALTER TABLE skill ADD COLUMN source_type VARCHAR(20) DEFAULT 'builtin';
-- 来源类型: builtin / upload / remote
```

#### 3.4.2 字段对照（Yuxi → LightBot）

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
    ├── 1. 读取 Agent 绑定的 Skill 列表
    ├── 2. 注入 Skill 摘要到 System Prompt
    │      "可用技能：\n- deep-research: 多轮检索 (调用 read_skill 查看全文)"
    ├── 3. 构建 read_skill 工具并注册
    │
    ▼
Agent 第一轮对话（模型只看到摘要，无 Skill 依赖工具）
    │
    ▼
Agent 决定使用 deep-research → 调用 read_skill("deep-research")
    │
    ├── SkillStorageService.getSkillMarkdown("deep-research")
    │   → MinIO getObject → Caffeine 缓存
    ├── 返回 SKILL.md 全文给 Agent
    ├── SkillActivationListener 标记 activated
    │   → Redis SET skill:activated:{sessionId} = ["deep-research"]
    │
    ▼
Agent 第二轮对话
    │
    ▼
ToolPrepMiddleware.prepare(ctx)
    │
    ├── 读取 activatedSkills
    ├── 展开 skill_dependencies（DFS + 循环检测）
    ├── 合并 tool_dependencies / mcp_dependencies
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

| 工具名 | 类型 | 权限 | 说明 |
|--------|------|------|------|
| `read_skill` | Skill 专用 | 只读 | 读取 SKILL.md，触发懒激活 |
| `list_skill_files` | Skill 专用 | 只读 | 列出 Skill 目录下的文件 |
| `read_file` | 通用沙箱 | 读 | 读取工作区文件 |
| `write_file` | 通用沙箱 | 写 | 写入工作区文件 |
| `list_files` | 通用沙箱 | 读 | 列出工作区目录 |
| `upload_file` | 通用沙箱 | 写 | 上传文件到工作区 |

#### 3.6.2 路径安全设计

```java
public final class SandboxPathValidator {

    private static final List<String> READABLE_ROOTS = List.of("skills/", "threads/");
    private static final List<String> WRITABLE_ROOTS = List.of("threads/"); // skills 只读

    /** 标准化路径 + 安全校验 */
    public static String normalize(String path) {
        String normalized = path.replace("\\", "/");
        if (normalized.contains("..")) {
            throw new SecurityException("path traversal not allowed: " + path);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    /** 校验读权限 */
    public static void checkReadable(String path) {
        String p = normalize(path);
        if (READABLE_ROOTS.stream().noneMatch(p::startsWith)) {
            throw new SecurityException("read not allowed: " + path);
        }
    }

    /** 校验写权限 */
    public static void checkWritable(String path) {
        String p = normalize(path);
        if (WRITABLE_ROOTS.stream().noneMatch(p::startsWith)) {
            throw new SecurityException("write not allowed: " + path);
        }
    }
}
```

### 3.7 Skill 依赖闭包展开

```java
/**
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

**校验规则**：
- 不允许自依赖（`slug ∈ skill_dependencies`）
- 不允许循环依赖（A→B→A，DFS 天然处理）
- 依赖的 Skill 必须存在且 enabled
- 依赖的 Skill 必须在用户可见范围内

---

## 四、涉及文件与组件

### 4.1 后端新增文件

| 文件 | 层级 | 说明 |
|------|------|------|
| `util/SandboxPathValidator.java` | 基础 | 路径安全校验工具 |
| `service/sandbox/SkillStorageService.java` | Service | MinIO Skill 文件读写、frontmatter 解析、ZIP 导入导出 |
| `model/SkillMetadata.java` | Model | SKILL.md frontmatter 解析后的结构化数据 |
| `tool/builtin/ReadSkillTool.java` | Tool | `@SystemTool` 读取 SKILL.md，触发懒激活 |
| `tool/builtin/ListSkillFilesTool.java` | Tool | `@SystemTool` 列出 Skill 目录文件 |
| `tool/sandbox/ReadFileTool.java` | Tool | `@SystemTool` 读取工作区文件 |
| `tool/sandbox/WriteFileTool.java` | Tool | `@SystemTool` 写入工作区文件 |
| `tool/sandbox/ListFilesTool.java` | Tool | `@SystemTool` 列出工作区目录 |

### 4.2 后端修改文件

| 文件 | 修改内容 |
|------|---------|
| `entity/Skill.java` | 新增 `objectPrefix`、`version`、`skillDependencies`、`sourceType` 字段 |
| `service/impl/SkillServiceImpl.java` | 新增 ZIP 导入、导出、依赖校验方法 |
| `controller/SkillController.java` | 新增 ZIP 导入/导出 API |
| `service/chat/SkillPrepMiddleware.java` | 改造：注入 Skill 摘要 + read_skill 工具（替代全量 prompt 注入） |
| `service/chat/ToolPrepMiddleware.java` | 改造：读取 activatedSkills，展开依赖，按需合并 Tool/MCP |
| `service/chat/ChatContext.java` | 新增 `activatedSkills` 字段 |
| `util/MinioUtil.java` | 新增 `listObjects`、`copyObject`、`exists`、`statObject` 方法 |
| `skill/BuiltInSkillRegistrar.java` | 改造：同步 Skill 内容到 MinIO（替代 DB promptTemplate） |
| `skill/BuiltInSkillDefinitions.java` | 改造：定义从 SKILL.md 文件读取（替代硬编码字符串） |

### 4.3 前端修改文件

| 文件 | 修改内容 |
|------|---------|
| `views/SkillManage.vue` | 新增 ZIP 上传按钮、sourceType 筛选、版本号显示 |
| `views/AgentDetail.vue` | Skill 绑定 Tab 增加依赖关系展示 |
| `views/Chat.vue` | 沙箱文件操作结果展示（read_file / write_file 的 SSE 事件） |
| `components/SkillImportModal.vue` | 新建：ZIP 导入弹窗（拖拽上传 + 草稿预览 + 确认安装） |

### 4.4 SQL 变更

```sql
-- 2026-06-18-001.sql
-- Skill 表扩展：文件化存储 + 依赖声明 + 来源类型

ALTER TABLE skill ADD COLUMN object_prefix VARCHAR(256);
ALTER TABLE skill ADD COLUMN version VARCHAR(64) DEFAULT '1.0.0';
ALTER TABLE skill ADD COLUMN skill_dependencies JSONB DEFAULT '[]';
ALTER TABLE skill ADD COLUMN source_type VARCHAR(20) DEFAULT 'builtin';

COMMENT ON COLUMN skill.object_prefix IS 'MinIO 路径前缀，如 skills/{slug}/';
COMMENT ON COLUMN skill.version IS '语义版本号';
COMMENT ON COLUMN skill.skill_dependencies IS '依赖其他 Skill 的 slug 列表';
COMMENT ON COLUMN skill.source_type IS '来源类型: builtin/upload/remote';

CREATE INDEX idx_skill_source_type ON skill(source_type);
```

---

## 五、核心难点

### 5.1 懒激活的状态管理

**难点**：LightBot 的 `ChatContext` 是请求级对象，不跨轮次持久化。懒激活需要记住"哪些 Skill 已被激活"。

**方案**：Redis 存储激活状态

```java
// Key:   skill:activated:{sessionId}
// Value: JSON Set<String> activatedSlugs
// TTL:   24h（与会话一致）

@Component
@RequiredArgsConstructor
public class SkillActivationStore {
    private final RedisUtil redisUtil;
    private static final String PREFIX = "skill:activated:";
    private static final Duration TTL = Duration.ofHours(24);

    public Set<String> getActivated(Long sessionId) {
        String json = redisUtil.get(PREFIX + sessionId);
        return json != null ? JsonUtils.parseSet(json, String.class) : new LinkedHashSet<>();
    }

    public void activate(Long sessionId, String slug) {
        Set<String> slugs = getActivated(sessionId);
        slugs.add(slug);
        redisUtil.set(PREFIX + sessionId, JsonUtils.toJson(slugs), TTL);
    }
}
```

### 5.2 MinIO 与 DB 一致性

**难点**：ZIP 导入时，MinIO 上传成功但 DB 事务回滚，导致孤儿对象。

**方案**：两阶段提交（对齐 Yuxi）

1. **阶段一**：解压到 `skill_drafts/{draftId}/`，解析 frontmatter，返回预览
2. **阶段二**：用户确认后，原子执行：
   - MinIO：`skill_drafts/{draftId}/` → `skills/{slug}/`
   - DB：INSERT skill 记录
   - DB 失败则异步清理 MinIO 对象

删除时：先标记 DB `status=DELETED` → 异步删除 MinIO prefix → 物理删除 DB 记录。

### 5.3 SKILL.md Frontmatter 解析

**难点**：YAML 中可能包含特殊字符（多行字符串、引号、冒号），解析失败不能阻断 Skill 加载。

**方案**：正则提取 + SnakeYAML 解析 + 降级兜底

```java
public SkillMetadata parseSkillMarkdown(String content) {
    // 1. 正则提取 frontmatter
    Matcher m = Pattern.compile("^---\\n(.*?)\\n---\\n(.*)$", Pattern.DOTALL)
                        .matcher(content);
    if (!m.find()) {
        // 无 frontmatter，整个内容作为 promptTemplate
        return SkillMetadata.builder().promptTemplate(content).build();
    }

    String yamlStr = m.group(1);
    String body = m.group(2);

    // 2. SnakeYAML 安全解析
    Yaml yaml = new Yaml(new SafeConstructor());
    Map<String, Object> data = yaml.load(yamlStr);

    // 3. 构建元数据
    return SkillMetadata.builder()
        .slug((String) data.get("slug"))
        .name((String) data.get("name"))
        .description((String) data.get("description"))
        .toolDependencies(getStringList(data, "tool_dependencies"))
        .mcpDependencies(getStringList(data, "mcp_dependencies"))
        .skillDependencies(getStringList(data, "skill_dependencies"))
        .promptTemplate(body)
        .build();
}
```

### 5.4 Spring AI 动态 Tool 注册

**难点**：Spring AI 的 `FunctionCallback` 通常在启动时注册。懒激活需要运行时动态添加 Tool。

**方案**（LightBot 已有基础）：

`ToolPrepMiddleware` 每次请求时重新构建 `ToolCallingChatOptions`。改造点：

```java
// ToolPrepMiddleware.prepare() 中增加：
Set<String> activated = skillActivationStore.getActivated(sessionId);
if (activated != null && !activated.isEmpty()) {
    // 展开依赖闭包
    Map<String, List<String>> depMap = skillService.buildDependencyMap(activated);
    Set<String> allSlugs = SkillDependencyResolver.expandSkillClosure(activated, depMap);

    // 合并依赖的 Tool
    for (String slug : allSlugs) {
        Skill skill = skillService.getBySlug(slug);
        if (skill != null && skill.getToolIds() != null) {
            List<Long> depToolIds = JsonUtils.parseLongList(skill.getToolIds());
            mergedToolIds.addAll(depToolIds);
        }
        // 合并依赖的 MCP Server
        if (skill != null && skill.getMcpServerIds() != null) {
            List<Long> depMcpIds = JsonUtils.parseLongList(skill.getMcpServerIds());
            mergedMcpServerIds.addAll(depMcpIds);
        }
    }
}
```

### 5.5 ZIP 导入的 Slug 冲突处理

**难点**：用户上传的 ZIP 中 SKILL.md 定义的 slug 可能已存在。

**方案**（对齐 Yuxi）：
- 冲突时自动追加后缀：`deep-research` → `deep-research-v2` → `deep-research-v3`
- 导入预览阶段展示 slug 映射，允许用户修改
- 内置 Skill 的 slug 不允许被上传覆盖

### 5.6 多实例 Skill 内容缓存

**难点**：每请求读 MinIO 有网络延迟，高频访问场景下需要缓存。

**方案**：Caffeine 本地缓存 + content_hash 失效

```java
@Cacheable(value = "skill-content", key = "#slug + ':' + #contentHash")
public String getSkillMarkdown(String slug, String contentHash) {
    String path = "skills/" + slug + "/SKILL.md";
    byte[] bytes = minioUtil.downloadBytes(path);
    return new String(bytes, StandardCharsets.UTF_8);
}
```

- 缓存 key = `slug:contentHash`
- Skill 更新时 contentHash 变化，自动失效
- 缓存容量：100 个 Skill，单个最大 50KB

---

## 六、特点

### 6.1 与 Yuxi 的对齐点

| 能力 | Yuxi 实现 | LightBot 对齐方案 |
|------|----------|------------------|
| 双层存储 | 文件系统 + DB | MinIO + DB |
| SKILL.md 格式 | pyyaml 解析 frontmatter | SnakeYAML 解析 frontmatter |
| 懒激活 | `read_file` 路径匹配 → State | `read_skill` 工具 → Redis |
| 依赖闭包展开 | DFS + 循环检测 | 同（Java 实现） |
| 两阶段导入 | 草稿目录 + TTL | `skill_drafts/` prefix + TTL |
| Skill 只读后端 | `SelectedSkillsReadonlyBackend` | MinIO prefix + `checkReadable` |
| 内置 Skill 启动同步 | `init_builtin_skills()` + content_hash | `BuiltInSkillRegistrar` + content_hash |

### 6.2 与 Yuxi 的差异点

| 维度 | Yuxi | LightBot | 理由 |
|------|------|----------|------|
| 存储层 | 宿主机文件系统 | MinIO 对象存储 | LightBot 已有 MinIO 基础设施 |
| 依赖引用 | slug 字符串 | Long ID + slug 混合 | 渐进迁移，不破坏现有 Agent 绑定 |
| 激活状态 | LangGraph State | Redis | Java 生态更自然，支持跨实例 |
| 沙箱执行 | 容器沙箱（agent-sandbox） | Level 1-2 先行，Level 3 按需 | 降低初期复杂度 |
| 权限模型 | global/department/user 三级 | global/user 两级 | 暂无多部门需求 |
| 远程安装 | npx skills CLI | GitHub API 下载 ZIP | 避免引入 Node.js 运行时 |

### 6.3 分级实施路线

| 阶段 | 周期 | 产出 | 前置依赖 |
|------|------|------|---------|
| **Level 1** | 2 周 | MinioUtil 扩展 + SkillStorageService + SKILL.md 解析 + Skill 内容迁移 + ZIP 导入导出 | 无 |
| **Level 2** | 1 周 | read_skill 懒激活 + 激活状态 Redis 存储 + 依赖闭包展开 + SandboxPathValidator | Level 1 |
| **Level 3** | 1 周 | 会话工作区（read_file / write_file / list_files）+ 用户共享工作区 | Level 2 |
| **Level 4** | 4-6 周 | 容器沙箱（Docker/K8s）+ 命令执行 + Sandbox Provisioner 服务 | Level 3 + 基础设施 |

**建议**：Level 1 + Level 2 + Level 3 共 4 周，覆盖 Skill 文件化、懒激活、会话工作区三大场景。Level 4 按业务需求（是否需要代码执行能力）决定是否实施。

---

## 七、前端交互设计

### 7.1 Skill 管理页改造

```
┌─────────────────────────────────────────────────────────────┐
│  Skill 管理                                    [ZIP导入] [新建] │
│                                                             │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐          │
│  │ 全部     │ │ 内置    │ │ 上传    │ │ 远程    │          │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘          │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 🔵 deep-research              内置  v1.0.0            │ │
│  │    多轮检索 + 结构化整理                                │ │
│  │    依赖: web_search                     [编辑] [导出]   │ │
│  ├───────────────────────────────────────────────────────┤ │
│  │ 🟢 my-custom-skill            上传  v1.0.0            │ │
│  │    自定义分析技能                                       │ │
│  │    依赖: (无)                       [编辑] [导出] [删除] │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 ZIP 导入弹窗

```
┌─────────────────────────────────────────────┐
│  导入 Skill                         [×]     │
│                                             │
│  ┌───────────────────────────────────────┐  │
│  │                                       │  │
│  │     拖拽 ZIP 文件到此处，或 点击上传     │  │
│  │                                       │  │
│  └───────────────────────────────────────┘  │
│                                             │
│  解析结果预览：                              │
│  ┌───────────────────────────────────────┐  │
│  │ Slug:    my-analysis-skill            │  │
│  │ 名称:    数据分析技能                   │  │
│  │ 描述:    多维度数据分析与可视化          │  │
│  │ 依赖:    web_search, calculator       │  │
│  │ 文件:    SKILL.md + 3 个附属文件        │  │
│  └───────────────────────────────────────┘  │
│                                             │
│              [取消]    [确认导入]             │
└─────────────────────────────────────────────┘
```

### 7.3 Agent 编排页 Skill 绑定改造

```
┌─────────────────────────────────────────────────────────────┐
│  Agent 编排 > Skill 配置                                     │
│                                                             │
│  已绑定 2/10 个 Skill                                        │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 🔵 deep-research    v1.0.0    依赖: web_search        │ │
│  │    [×]                                                  │ │
│  ├───────────────────────────────────────────────────────┤ │
│  │ 🟢 my-skill         v1.0.0    依赖: (无)              │ │
│  │    [×]                                                  │ │
│  └───────────────────────────────────────────────────────┘ │
│                                                             │
│  + 添加 Skill                                                │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐ │
│  │ 可选 Skill（按依赖自动展开）：                           │ │
│  │   web_search → 因 deep-research 依赖自动注入            │ │
│  └───────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## 八、关键问题 Q&A

### Q1：不做容器沙箱，能正常使用 Skill 相关功能吗？

**能。** 容器沙箱（Level 3）和 Skill 核心功能是完全独立的两件事。

| 功能层级 | 是否需要容器沙箱 | 说明 |
|---------|:---:|------|
| Skill 文件存储（MinIO） | 否 | 纯 MinIO 对象读写，不需要容器 |
| SKILL.md frontmatter 解析 | 否 | 纯 Java 字符串解析 |
| ZIP 导入/导出 | 否 | MinIO + ZIP 流处理 |
| Agent 绑定 Skill | 否 | DB 字段读写 |
| Skill 懒激活（read_skill） | 否 | MinIO getObject + Redis 状态 |
| Skill 依赖闭包展开 | 否 | 纯内存计算 |
| **会话工作区文件读写** | 否 | MinIO prefix 隔离即可 |
| **代码执行（Python/Shell）** | **是** | 需要容器隔离，防止恶意代码 |

**结论**：把 MinIO 当"文件沙箱"完全够用。Level 1（Skill 文件化）+ Level 2（懒激活）+ Level 3（会话工作区）全部基于 MinIO，不依赖容器。只有需要让 Agent 执行代码（Python 脚本、Shell 命令）时才需要容器沙箱，那是 Level 4 的事。

---

### Q2：除了 Skill，还有什么会用到文件沙箱？

| 场景 | 当前实现 | 是否需要沙箱 | 说明 |
|------|---------|:---:|------|
| **Skill 文件** | DB `prompt_template` 字段 | 否（迁移到 MinIO） | 本次改造重点 |
| **聊天附件** | MinIO `chat/{agentId}/{sessionId}/{attachmentId}` | 否 | 已有，无需改动 |
| **知识库文档** | MinIO `knowledge/{knowledgeId}/documents/` | 否 | 已有，无需改动 |
| **工作流脚本节点** | JVM 内 Nashorn 引擎直接执行 | **建议隔离** | 见下文分析 |
| **Agent 代码执行** | 无（未实现） | **是** | 需要容器沙箱 |

**关于工作流脚本节点（ScriptNodeProcessor）**：

当前实现在 `ScriptNodeProcessor.java` 中，使用 `javax.script.ScriptEngine`（Nashorn）在 **JVM 进程内直接执行** JavaScript，没有任何隔离：

```java
// 当前实现：直接在 JVM 内执行，无沙箱
ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
engine.eval(script, bindings);
```

**风险**：
- 脚本可以访问 JVM 内所有类和资源
- 恶意脚本可以读写宿主机文件系统
- 无限循环会阻塞 JVM 线程

**改进方向**（与 Sandbox 模块独立，可单独优化）：
- **短期**：Nashorn 沙箱化 — 限制可访问的 Java 类（`ClassFilter`）、设置脚本执行超时、限制内存
- **中期**：将脚本发送到容器沙箱执行（Level 4 实施后）
- **长期**：支持 Python 等更多语言，统一走容器沙箱

**结论**：脚本节点的隔离是独立议题，不影响 Skill 模块实施。但建议在 Level 4 容器沙箱就绪后，将脚本节点也迁移到沙箱执行。

---

### Q3：不做工作区，能正常使用 Skill 功能吗？

**能。** 工作区和 Skill 是两个完全独立的功能域。

| 功能 | 依赖工作区？ | 说明 |
|------|:---:|------|
| Skill 文件存储（MinIO skills/） | 否 | Skill 有自己的 MinIO prefix |
| SKILL.md 解析 | 否 | 纯字符串处理 |
| Agent 绑定 Skill | 否 | DB 字段 |
| 懒激活（read_skill） | 否 | 读取 `skills/{slug}/SKILL.md` |
| 依赖工具注入 | 否 | `ToolPrepMiddleware` 合并 |
| **Agent 对话中读写文件** | **是** | 需要 `threads/{sessionId}/workspace/` |

**工作区解决的问题**是：Agent 在对话过程中需要读写文件（如"帮我分析这个 CSV 并生成报告"），这与 Skill 本身无关。

**建议实施顺序**：
1. **Level 1**：Skill 文件化（MinIO skills/）— 必做
2. **Level 2**：懒激活 — 必做
3. **Level 3**：会话工作区 — 可选，按需实施

即使永远不做工作区，Skill 的文件存储、懒激活、依赖管理、ZIP 导入导出等功能都能完整运行。

---

### Q4：Sandbox 文件工具跟本系统自己的工具是两回事吗？

**是同一套注册机制，但职责不同。**

| 维度 | 系统工具（现有） | Sandbox 文件工具（新增） |
|------|--------------|-------------------|
| 注册方式 | `@SystemTool` 注解 → `ToolRegistrar` | 同 |
| 注册时机 | 应用启动时 | 同（但 read_skill 的内容是动态的） |
| 工具类型 | `type=builtin` 或 `type=knowledge` | `type=builtin`（read_skill / read_file 等） |
| 执行内容 | 调用外部 API / 数据库查询 | 调用 MinIO 读写 |
| 数据来源 | 外部服务 | MinIO 对象存储 |
| 与 Skill 的关系 | Skill 通过 `tool_ids` 引用系统工具 | Sandbox 工具是 Skill 激活的触发器 |

**关键区别**：

```
系统工具（如 web_search）：  Skill 声明依赖 → 懒激活后注入 → Agent 调用
Sandbox 工具（如 read_skill）：Agent 调用 → 触发 Skill 激活 → 注入依赖的系统工具
```

**交互流程**：

```
Agent 看到 Skill 摘要: "deep-research: 多轮检索 (调用 read_skill 查看全文)"
    │
    ▼
Agent 调用 read_skill("deep-research")     ← Sandbox 工具（读 MinIO）
    │
    ▼
返回 SKILL.md 全文 + 标记激活
    │
    ▼
下一轮请求，ToolPrepMiddleware 注入 web_search  ← 系统工具（外部 API）
    │
    ▼
Agent 使用 web_search 执行深度研究
```

**结论**：Sandbox 文件工具和系统工具共享 `@SystemTool` + `ToolRegistrar` 注册体系，但它们在 Skill 生命周期中扮演不同角色 — Sandbox 工具是"钥匙"（触发激活），系统工具是"能力"（被激活后可用）。

---

*文档生成时间: 2026-06-18*
*基于 Yuxi Skill 系统 + Sandbox 模块分析，结合 LightBot 现有架构设计*
