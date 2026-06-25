# LightBot 多用户改造分析文档

> 企业级 AI Agent 平台多用户支持方案
>
> 文档版本：v1.0 | 更新日期：2026-06-24

---

## 一、需求分析

### 1.1 业务背景

LightBot 定位为**企业级 AI Agent 平台**，当前系统已具备基础的用户认证（Sa-Token），但数据隔离和权限控制尚不完善。多用户改造的核心目标：

| 维度 | 当前状态 | 目标状态 |
|------|----------|----------|
| **用户认证** | 已实现（Sa-Token + BCrypt） | 保持不变 |
| **角色体系** | ADMIN / USER 两角色 | 保持 ADMIN / USER，细化权限矩阵 |
| **数据隔离** | 部分隔离（Agent/会话按用户） | 全面隔离，管理员可查看所有数据 |
| **知识库权限** | 4 级 RBAC（CREATOR/MANAGER/DEVELOPER/VIEWER） | 简化为 3 级（OWNER/EDITOR/VIEWER） |
| **资源共享** | 工具/MCP/Skill 全局可见 | 按需共享，支持私有 → 公开 |

### 1.2 核心原则

1. **平滑迁移**：不破坏现有功能，渐进式改造
2. **最小改动**：复用现有结构，避免大规模重构
3. **安全优先**：数据隔离 > 功能完整 > 性能优化
4. **企业特性**：支持管理员全局视图、审计追踪

---

## 二、现状分析

### 2.1 认证体系（已实现）

```
┌─────────────────────────────────────────────────┐
│                   Sa-Token                       │
├─────────────────────────────────────────────────┤
│ Token 存储: Redis (UUID)                        │
│ 密码加密: BCrypt                                │
│ 角色: ADMIN / USER                              │
│ 登录态: StpUtil.getLoginIdAsLong()              │
│ 使用点: 19 个文件                               │
└─────────────────────────────────────────────────┘
```

**结论**：认证体系完善，无需改造。

### 2.2 数据隔离现状

#### 按用户隔离（已有 userId）

| 实体 | 隔离方式 | 说明 |
|------|----------|------|
| Agent | userId 过滤 | 用户只能看到自己的 Agent |
| ChatSession | userId 过滤 | 会话与用户绑定 |
| Task | userId 过滤 | 任务与用户绑定 |
| Prompt | userId 过滤 | 提示词模板按用户 |

#### 全局共享（有 userId 但不隔离）

| 实体 | 当前行为 | 问题 |
|------|----------|------|
| Tool | 全局可见 | 用户 A 创建的工具，用户 B 可见可编辑 |
| McpServer | 全局可见 | 同上 |
| SubAgent | 全局可见 | 同上 |
| Skill | 全局可见 | 同上 |
| McpTool | 全局可见 | 同上 |

#### 无 userId 字段

| 实体 | 说明 |
|------|------|
| ModelProvider | 系统级配置，全局共享 |
| SystemConfig | 系统级配置，全局共享 |
| Knowledge | 通过 KnowledgeMember 关联表实现权限控制 |

### 2.3 知识库权限模型（重点分析）

#### 当前 4 级 RBAC

```
CREATOR (创建者)
  └── 可删除知识库、管理所有成员

MANAGER (管理者)
  └── 可添加/移除成员、修改权限

DEVELOPER (开发者)
  └── 可上传文档、编辑内容、测试问答

VIEWER (查看者)
  └── 仅查看，不可修改
```

#### 问题分析

| 问题 | 影响 |
|------|------|
| **角色过多** | 4 级角色增加了理解和使用成本 |
| **权限检查分散** | 21 处 `checkMember()`、27 处 `checkPermission(DEVELOPER)` |
| **MANAGER 角色冗余** | 实际使用中，CREATOR 和 MANAGER 权限高度重叠 |
| **DEVELOPER 命名歧义** | 容易与"开发者"角色混淆，实际是"编辑者" |

#### 涉及文件统计

```
权限检查调用点分布：
├── KnowledgeService.java      : 12 处
├── DocumentService.java       : 8 处
├── ChunkService.java          : 5 处
├── QaPairService.java         : 4 处
├── EmbeddingService.java      : 3 处
├── GraphService.java          : 3 处
├── PromptVersionService.java  : 2 处
└── 其他                       : 5 处
合计: 42 处权限检查调用
```

---

## 三、影响模块分析

### 3.1 后端模块影响

| 模块 | 影响程度 | 改动内容 |
|------|----------|----------|
| **lightbot-server** | 高 | 权限检查逻辑重构、数据隔离改造 |
| **lightbot-common** | 低 | 新增权限注解、工具类 |
| **lightbot-agent** | 中 | Agent 运行时权限校验 |
| **lightbot-rag** | 高 | 知识库权限模型重构 |
| **lightbot-tool** | 中 | 工具共享机制改造 |
| **lightbot-workflow** | 低 | Workflow 执行权限校验 |

### 3.2 前端模块影响

| 模块 | 影响程度 | 改动内容 |
|------|----------|----------|
| **views/AgentDetail.vue** | 中 | 工具/MCP/Skill 绑定列表过滤 |
| **views/Knowledge.vue** | 高 | 成员管理 UI 重构 |
| **views/Tool.vue** | 中 | 工具列表增加"我的"/"共享"切换 |
| **views/Chat.vue** | 低 | 会话列表已按用户隔离 |
| **components/** | 低 | 权限相关组件微调 |

### 3.3 数据库影响

| 表 | 改动类型 | 说明 |
|----|----------|------|
| knowledge_member | 字段修改 | role 枚举从 4 级改为 3 级 |
| tool | 新增字段 | `visibility` (PRIVATE/PUBLIC) |
| mcp_server | 新增字段 | `visibility` (PRIVATE/PUBLIC) |
| skill | 新增字段 | `visibility` (PRIVATE/PUBLIC) |
| sub_agent | 新增字段 | `visibility` (PRIVATE/PUBLIC) |

---

## 四、技术设计方案

### 4.1 权限模型重构

#### 4.1.1 全局角色（保持不变）

```java
public enum UserRole {
    ADMIN,  // 管理员：全局管理权限
    USER    // 普通用户：基础使用权限
}
```

#### 4.1.2 知识库角色（简化为 3 级）

```java
public enum KnowledgeRole {
    OWNER,   // 所有者：完全控制权（原 CREATOR）
    EDITOR,  // 编辑者：内容编辑权（原 DEVELOPER）
    VIEWER   // 查看者：只读权限（原 VIEWER）
}
```

**迁移映射**：
```
CREATOR  → OWNER
MANAGER  → OWNER (合并到所有者)
DEVELOPER → EDITOR
VIEWER   → VIEWER
```

#### 4.1.3 资源可见性

```java
public enum Visibility {
    PRIVATE, // 私有：仅创建者可见
    SHARED,  // 共享：指定用户可见（通过关联表）
    PUBLIC   // 公开：所有用户可见
}
```

### 4.2 数据隔离方案

#### 4.2.1 工具/MCP/Skill 隔离

**方案**：新增 `visibility` 字段，默认 `PUBLIC`（兼容现有数据）

```sql
-- 新增可见性字段
ALTER TABLE tool ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE mcp_server ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE skill ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE sub_agent ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC';
```

**查询逻辑**：

```java
// 普通用户：只能看到自己的 + 公开的
LambdaQueryWrapper<Tool> wrapper = new LambdaQueryWrapper<Tool>()
    .and(w -> w
        .eq(Tool::getUserId, currentUserId)
        .or()
        .eq(Tool::getVisibility, Visibility.PUBLIC)
    );

// 管理员：可以看到所有
if (isAdmin) {
    wrapper = new LambdaQueryWrapper<>(); // 无过滤
}
```

#### 4.2.2 知识库隔离（保持现有机制）

知识库通过 `knowledge_member` 表实现权限控制，机制完善，保持不变。

### 4.3 权限检查重构

#### 4.3.1 提取公共方法

当前权限检查代码分散在各 Service 中，提取为统一工具类：

```java
/**
 * 权限检查工具类
 */
@Component
@RequiredArgsConstructor
public class PermissionHelper {

    private final KnowledgeMemberMapper knowledgeMemberMapper;

    /**
     * 检查知识库权限
     * @param knowledgeId 知识库ID
     * @param requiredRole 最低要求角色
     * @throws BizException 权限不足时抛出
     */
    public void checkKnowledgePermission(Long knowledgeId, KnowledgeRole requiredRole) {
        Long userId = StpUtil.getLoginIdAsLong();
        KnowledgeRole userRole = getUserKnowledgeRole(knowledgeId, userId);

        if (userRole == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_ACCESS_DENIED);
        }

        // 角色层级：OWNER > EDITOR > VIEWER
        if (userRole.getLevel() < requiredRole.getLevel()) {
            throw new BizException(ErrorCode.KNOWLEDGE_PERMISSION_DENIED);
        }
    }

    /**
     * 检查资源所有权
     */
    public void checkResourceOwner(Long resourceUserId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        boolean isAdmin = StpUtil.getRoleList().contains("ADMIN");

        if (!resourceUserId.equals(currentUserId) && !isAdmin) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }
}
```

#### 4.3.2 权限注解（可选增强）

```java
/**
 * 权限检查注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    ResourceType resource();
    String action();
}

// 使用示例
@RequirePermission(resource = ResourceType.KNOWLEDGE, action = "edit")
public void uploadDocument(Long knowledgeId, MultipartFile file) {
    // 方法实现
}
```

### 4.4 前端改造方案

#### 4.4.1 工具列表过滤

```vue
<!-- Tool.vue -->
<template>
  <a-tabs v-model:activeKey="toolScope">
    <a-tab-pane key="mine" tab="我的工具" />
    <a-tab-pane key="public" tab="公共工具" />
  </a-tabs>
</template>

<script setup>
const toolScope = ref('mine')

const loadTools = async () => {
  const params = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    keyword: keyword.value
  }
  if (toolScope.value === 'mine') {
    params.userId = currentUserId
  }
  // ...
}
</script>
```

#### 4.4.2 知识库成员管理简化

```vue
<!-- KnowledgeMemberModal.vue -->
<template>
  <a-select v-model:value="memberRole">
    <a-select-option value="OWNER">所有者</a-select-option>
    <a-select-option value="EDITOR">编辑者</a-select-option>
    <a-select-option value="VIEWER">查看者</a-select-option>
  </a-select>
</template>
```

---

## 五、难点与风险

### 5.1 技术难点

| 难点 | 风险等级 | 应对策略 |
|------|----------|----------|
| **知识库角色迁移** | 高 | 编写迁移脚本，MANAGER → OWNER，DEVELOPER → EDITOR |
| **权限检查一致性** | 高 | 提取公共方法，统一调用点 |
| **数据兼容性** | 中 | visibility 字段默认 PUBLIC，兼容现有数据 |
| **管理员视图** | 中 | 前端增加管理员视角切换 |

### 5.2 业务难点

| 难点 | 说明 | 建议 |
|------|------|------|
| **资源共享机制** | 用户 A 的工具如何分享给用户 B？ | Phase 1 先支持公开/私有，Phase 2 再加分享功能 |
| **知识库转让** | 所有者离职后如何处理？ | 支持所有权转让功能 |
| **审计追踪** | 谁在什么时候修改了什么？ | 记录操作日志，Phase 2 实现 |

### 5.3 数据迁移风险

```
风险点：
1. knowledge_member 表 role 字段枚举值变更
   - 需要编写迁移脚本
   - 需要在低峰期执行
   - 需要备份数据

2. 新增 visibility 字段的默认值
   - 现有数据默认 PUBLIC（保持兼容）
   - 新建资源默认 PRIVATE（更安全）

3. 权限检查逻辑变更
   - 需要全面测试
   - 建议灰度发布
```

---

## 六、工作量评估

### 6.1 后端改造

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| KnowledgeRole 枚举重构 | 0.5 天 | P0 |
| knowledge_member 数据迁移 | 1 天 | P0 |
| PermissionHelper 工具类 | 1 天 | P0 |
| 42 处权限检查调用点重构 | 2 天 | P0 |
| Tool/McpServer/Skill 新增 visibility 字段 | 0.5 天 | P1 |
| 资源查询逻辑改造 | 1 天 | P1 |
| 管理员全局视图 API | 1 天 | P2 |
| 单元测试补充 | 1 天 | P1 |
| **小计** | **8 天** | |

### 6.2 前端改造

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| 知识库成员管理 UI 简化 | 1 天 | P0 |
| 工具/MCP/Skill 列表过滤 | 1 天 | P1 |
| 管理员视角切换 | 0.5 天 | P2 |
| 权限相关组件调整 | 0.5 天 | P1 |
| **小计** | **3 天** | |

### 6.3 测试与部署

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| 权限测试用例 | 1 天 | P0 |
| 数据迁移验证 | 0.5 天 | P0 |
| 集成测试 | 1 天 | P1 |
| 灰度发布准备 | 0.5 天 | P1 |
| **小计** | **3 天** | |

### 6.4 总计

| 阶段 | 工作量 | 内容 |
|------|--------|------|
| Phase 1 (P0) | 5 天 | 知识库权限简化 + 数据迁移 |
| Phase 2 (P1) | 5 天 | 资源隔离 + 查询改造 |
| Phase 3 (P2) | 4 天 | 管理员视图 + 高级功能 |
| **总计** | **14 天** | 约 3 周 |

---

## 七、实施建议

### 7.1 推荐实施路径

```
Week 1: 知识库权限重构（P0）
├── Day 1-2: KnowledgeRole 枚举重构 + 数据迁移脚本
├── Day 3: PermissionHelper 工具类
├── Day 4-5: 42 处权限检查调用点重构
└── 测试: 权限测试用例

Week 2: 资源隔离改造（P1）
├── Day 1: visibility 字段新增
├── Day 2-3: 资源查询逻辑改造
├── Day 4: 前端列表过滤
└── 测试: 集成测试

Week 3: 管理员功能 + 收尾（P2）
├── Day 1-2: 管理员全局视图
├── Day 3: 前端管理员视角
├── Day 4-5: 灰度发布 + 监控
```

### 7.2 平滑迁移策略

1. **数据库迁移**
   - 在低峰期执行迁移脚本
   - 迁移前备份 knowledge_member 表
   - 迁移后验证数据完整性

2. **代码部署**
   - 后端先部署（兼容新旧权限）
   - 前端后部署（适配新接口）
   - 保留回滚方案

3. **灰度策略**
   - 先在测试环境验证
   - 再在内部用户灰度
   - 最后全量发布

### 7.3 验证清单

```text
□ 知识库成员角色迁移正确（MANAGER → OWNER, DEVELOPER → EDITOR）
□ 普通用户只能看到自己的 + 公开的工具/MCP/Skill
□ 管理员可以看到所有资源
□ 知识库权限检查正确（42 处调用点）
□ 前端列表过滤功能正常
□ 管理员视角切换正常
□ 无权限用户操作返回正确错误码
□ 数据迁移脚本可回滚
```

---

## 八、结论

### 8.1 能否平滑迁移？

**可以**，但需要满足以下条件：

1. **知识库权限简化**：4 级 → 3 级，需要数据迁移，存在一定风险
2. **资源隔离改造**：新增 visibility 字段，默认 PUBLIC，兼容现有数据
3. **权限检查重构**：提取公共方法，统一调用点，工作量较大但风险可控

### 8.2 核心改动点

| 改动 | 风险 | 工作量 | 必要性 |
|------|------|--------|--------|
| 知识库角色简化 | 中 | 2 天 | 必须 |
| 权限检查重构 | 低 | 3 天 | 必须 |
| 资源可见性 | 低 | 2 天 | 建议 |
| 管理员视图 | 低 | 2 天 | 可选 |

### 8.3 最终建议

1. **Phase 1 优先做知识库权限简化**：这是用户感知最强、收益最大的改动
2. **Phase 2 做资源隔离**：提升数据安全性，符合企业级定位
3. **Phase 3 做管理员功能**：提升管理效率，可选实施

整体改造工作量约 **14 个工作日**，风险可控，建议分 3 个阶段渐进实施。

---

## 附录

### A. 关键文件清单

**后端（权限相关）**：
```
lightbot-server/src/main/java/com/lightbot/
├── service/impl/KnowledgeServiceImpl.java      # 知识库权限检查（12 处）
├── service/impl/DocumentService.java            # 文档权限检查（8 处）
├── service/impl/ChunkService.java               # 分块权限检查（5 处）
├── service/impl/QaPairService.java              # QA对权限检查（4 处）
├── service/impl/EmbeddingService.java           # 嵌入权限检查（3 处）
├── service/impl/GraphService.java               # 图谱权限检查（3 处）
├── service/impl/PromptVersionService.java       # 提示词权限检查（2 处）
├── entity/KnowledgeMember.java                  # 成员关联表
├── enums/KnowledgeRole.java                     # 知识库角色枚举
└── enums/UserRole.java                          # 用户角色枚举
```

**前端（权限相关）**：
```
lightbot-ui/src/
├── views/Knowledge.vue                          # 知识库管理
├── views/Tool.vue                               # 工具管理
├── views/AgentDetail.vue                        # Agent 详情（绑定列表）
├── components/KnowledgeMemberModal.vue          # 成员管理弹窗
└── api/knowledge.js                             # 知识库 API
```

### B. 数据库迁移脚本（示例）

```sql
-- 1. 备份 knowledge_member 表
CREATE TABLE knowledge_member_backup AS SELECT * FROM knowledge_member;

-- 2. 更新角色枚举值
UPDATE knowledge_member SET role = 'OWNER' WHERE role = 'CREATOR';
UPDATE knowledge_member SET role = 'OWNER' WHERE role = 'MANAGER';
UPDATE knowledge_member SET role = 'EDITOR' WHERE role = 'DEVELOPER';
-- VIEWER 保持不变

-- 3. 验证迁移结果
SELECT role, COUNT(*) FROM knowledge_member GROUP BY role;
-- 预期结果：只有 OWNER, EDITOR, VIEWER 三种角色
```

### C. 参考资料

- Sa-Token 官方文档：https://sa-token.cc/
- RBAC 最佳实践：https://en.wikipedia.org/wiki/Role-based_access_control
- Spring Security 权限设计：https://spring.io/projects/spring-security
