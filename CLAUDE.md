# LightBot - AI Coding Guide

> 轻量级 Java AI Agent 平台
>
> Tech Stack: SpringBoot + SpringAI + Vue3

---

## 项目原则

| 原则 | 说明 |
|------|------|
| **模块化** | 每个功能域独立模块，边界清晰，可独立演进 |
| **AI Native** | 架构围绕 AI 能力设计，而非传统 CRUD 套壳 |
| **Java First** | 后端统一 Java，不混入 Python / Node |
| **渐进式演进** | 先单体后拆分，先简单后复杂，不提前设计 |
| **低耦合** | 模块间通过接口通信，禁止直接依赖实现类 |

---

## 架构原则

### 禁止事项

```text
❌ 禁止跨模块直接调用 Service
❌ 禁止 Controller 写业务逻辑
❌ 禁止循环依赖
❌ 禁止硬编码 Prompt
❌ 禁止直接依赖 OpenAI SDK / DashScope SDK
❌ 禁止在业务代码中直接 new RestTemplate 调用模型
```

### 必须遵守

```text
✅ 所有模型调用必须通过统一 AI Framework（SpringAI）
✅ 所有跨模块调用必须通过 Facade 接口
✅ 所有 Prompt 必须模板化，存储于配置或数据库
✅ 所有外部依赖通过依赖注入，禁止静态方法调用
```

### 模块结构

```
lightbot/
├── lightbot-common/           # 公共工具、常量、异常
├── lightbot-ai/               # AI Framework 封装
├── lightbot-agent/            # Agent 定义与运行时
├── lightbot-tool/             # Tool 体系
├── lightbot-workflow/         # Workflow 引擎
├── lightbot-rag/              # RAG 知识库
├── lightbot-server/           # 主服务入口
└── lightbot-web/              # Vue3 前端
```

### 依赖规则

```text
lightbot-server → lightbot-agent, lightbot-workflow, lightbot-rag
lightbot-agent  → lightbot-ai, lightbot-tool
lightbot-workflow → lightbot-ai, lightbot-tool
lightbot-rag    → lightbot-ai
lightbot-ai     → lightbot-common
lightbot-tool   → lightbot-common
```

**下层禁止依赖上层，同层禁止循环依赖。**

---

## 代码规范

### 包结构

```
com.lightbot.{module}/
├── controller/        # 接口层，只做参数校验和返回
├── service/           # 业务逻辑
│   ├── impl/          # 实现类
│   └── facade/        # 对外暴露的接口
├── model/
│   ├── entity/        # 数据库实体
│   ├── dto/           # 服务间传输对象
│   ├── vo/            # 前端展示对象
│   └── bo/            # 业务对象
├── repository/        # 数据访问
├── config/            # 配置类
├── constant/          # 常量
├── enums/             # 枚举
└── exception/         # 异常定义
```

### DTO 规范

```java
/**
 * 命名：{业务名}DTO
 * 用途：服务间数据传输
 */
@Data
public class AgentCreateDTO {
    @NotBlank(message = "Agent名称不能为空")
    private String name;
    
    private String systemPrompt;
    
    private String modelId;
}
```

- DTO 只用于服务间传输，不暴露给前端
- 使用 Jakarta Validation 注解做校验
- 字段类型使用包装类（Long / Integer / Boolean），不用基本类型

### VO 规范

```java
/**
 * 命名：{业务名}VO / {业务名}Response
 * 用途：返回给前端的数据
 */
@Data
public class AgentVO {
    private Long id;
    private String name;
    private String modelId;
    private LocalDateTime createTime;
}
```

- VO 不包含业务逻辑
- 时间字段统一使用 `LocalDateTime`
- 列表返回使用 `PageVO<T>` 包装

### Entity 规范

```java
/**
 * 命名：数据库表名驼峰化
 * 表名：t_agent
 */
@Data
@TableName("t_agent")
public class AgentEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    
    private String name;
    
    private String systemPrompt;
    
    private String modelId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    @TableLogic
    private Integer deleted;
}
```

- 主键统一使用雪花算法 `IdType.ASSIGN_ID`
- 必须包含 `createTime`、`updateTime`、`deleted` 字段
- 使用 `@TableLogic` 实现逻辑删除

### Service 规范

```java
/**
 * 接口定义
 */
public interface AgentService {
    AgentVO create(AgentCreateDTO dto);
    AgentVO getById(Long id);
    PageVO<AgentVO> list(AgentQueryDTO query);
}

/**
 * 实现类
 * 命名：{业务名}ServiceImpl
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {
    
    private final AgentRepository agentRepository;
    
    @Override
    public AgentVO create(AgentCreateDTO dto) {
        // 1. 参数校验
        // 2. 业务处理
        // 3. 返回结果
    }
}
```

- 接口与实现分离
- 一个 Service 只处理一个业务域
- 禁止在 Service 中直接调用其他模块的 Repository

### Repository 规范

```java
/**
 * 命名：{业务名}Repository
 * 用途：数据访问层封装
 */
@Repository
@RequiredArgsConstructor
public class AgentRepository {
    
    private final AgentMapper agentMapper;
    
    public AgentEntity getById(Long id) {
        return agentMapper.selectById(id);
    }
    
    public void save(AgentEntity entity) {
        if (entity.getId() == null) {
            agentMapper.insert(entity);
        } else {
            agentMapper.updateById(entity);
        }
    }
}
```

- Repository 封装所有数据库操作
- Service 不直接使用 Mapper
- 复杂查询在 Repository 中封装，不在 Service 中拼装

### Workflow Node 规范

```java
/**
 * 所有节点必须实现 Node 接口
 * 命名：{功能}Node
 */
public interface Node {
    
    /** 节点类型标识 */
    String getType();
    
    /** 执行节点逻辑 */
    NodeResult execute(NodeContext context);
}

@Component
@RequiredArgsConstructor
public class LLMNode implements Node {
    
    @Override
    public String getType() {
        return "LLM";
    }
    
    @Override
    public NodeResult execute(NodeContext context) {
        // 从 context 获取输入
        // 调用 AI 模型
        // 返回结果
    }
}
```

- 每个节点是独立组件，无状态
- 节点间通过 `NodeContext` 传递数据
- 节点必须声明输入输出类型

### Tool 规范

```java
/**
 * 所有 Tool 必须实现 Tool 接口
 * 命名：{功能}Tool
 */
public interface Tool {
    
    /** Tool 唯一标识 */
    String getName();
    
    /** Tool 描述，用于 Agent 理解 */
    String getDescription();
    
    /** 输入参数定义（JSON Schema） */
    String getInputSchema();
    
    /** 执行 Tool */
    ToolResult execute(ToolInput input);
}

@Component
public class HttpRequestTool implements Tool {
    
    @Override
    public String getName() {
        return "http_request";
    }
    
    @Override
    public String getDescription() {
        return "发送 HTTP 请求，支持 GET/POST/PUT/DELETE";
    }
    
    @Override
    public String getInputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "url": {"type": "string", "description": "请求地址"},
                "method": {"type": "string", "enum": ["GET","POST","PUT","DELETE"]},
                "body": {"type": "object", "description": "请求体"}
            },
            "required": ["url", "method"]
        }
        """;
    }
    
    @Override
    public ToolResult execute(ToolInput input) {
        // 实现逻辑
    }
}
```

- Tool 必须无状态，可并发调用
- `getInputSchema()` 返回标准 JSON Schema
- Tool 不直接访问数据库，通过 Service 层

### MCP 规范

```java
/**
 * MCP Server 定义
 * 命名：{功能}McpServer
 */
@Component
public class LightBotMcpServer {
    
    private final List<Tool> tools;
    
    /**
     * 注册 Tool 到 MCP
     */
    public void registerTool(Tool tool) {
        // 注册逻辑
    }
    
    /**
     * 处理 MCP 请求
     */
    public McpResponse handle(McpRequest request) {
        // 路由到对应 Tool
    }
}
```

- MCP 是 Tool 的对外暴露协议
- 一个 McpServer 可包含多个 Tool
- 遵循 MCP 协议规范（Model Context Protocol）

---

## AI 编码规则

### 如何生成代码

```text
1. 先读现有代码，理解模块结构和命名风格
2. 新增代码必须符合包结构规范
3. 新增类必须添加 Javadoc 注释
4. 新增方法必须添加 @param / @return 注释
5. 禁止生成重复逻辑，发现重复先抽象
```

### 如何生成接口

```text
1. Controller 只做参数校验和返回封装
2. 业务逻辑必须放在 Service 层
3. 请求体使用 DTO 接收
4. 响应体使用 VO / Response 封装
5. 统一返回 Result<T> 包装
6. 必须添加 Swagger 注解
```

接口模板：

```java
@PostMapping("/agents")
@Operation(summary = "创建Agent")
public Result<AgentVO> create(@RequestBody @Valid AgentCreateDTO dto) {
    return Result.success(agentService.create(dto));
}
```

### 如何生成测试

```text
1. 单元测试：测试 Service 层逻辑
2. 集成测试：测试完整请求链路
3. 测试类命名：{类名}Test
4. 测试方法命名：test_{场景}_{预期结果}
5. 使用 @SpringBootTest + @Transactional 自动回滚
```

测试模板：

```java
@SpringBootTest
@Transactional
class AgentServiceTest {
    
    @Autowired
    private AgentService agentService;
    
    @Test
    void test_create_withValidParam_shouldSuccess() {
        // given
        AgentCreateDTO dto = new AgentCreateDTO();
        dto.setName("test-agent");
        
        // when
        AgentVO result = agentService.create(dto);
        
        // then
        assertNotNull(result.getId());
        assertEquals("test-agent", result.getName());
    }
}
```

### 如何生成数据库

```text
1. 使用 Flyway 管理数据库版本
2. 迁移文件命名：V{版本号}__{描述}.sql
3. 新增表必须包含 id, create_time, update_time, deleted 字段
4. 索引命名：idx_{表名}_{字段名}
5. 禁止在代码中直接执行 DDL
```

迁移模板：

```sql
-- V0.1__create_agent_table.sql
CREATE TABLE t_agent (
    id          BIGINT       NOT NULL COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    system_prompt TEXT       COMMENT '系统提示词',
    model_id    VARCHAR(64)  COMMENT '模型ID',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    INDEX idx_agent_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent表';
```

### 如何新增模块

```text
1. 在 lightbot/ 下创建新模块目录
2. 创建 pom.xml，声明 parent 和依赖
3. 在父 pom.xml 中添加 module 声明
4. 按包结构规范创建目录
5. 编写模块的 Facade 接口
6. 在 lightbot-server 中引入新模块
```

---

## Workflow 规范

### 节点设计原则

```text
✅ 单一职责：一个节点只做一件事
✅ 无状态：节点不持有状态，所有数据从 Context 获取
✅ 可测试：节点可独立单元测试
✅ 幂等：相同输入相同输出
❌ 禁止节点间直接调用
❌ 禁止节点持有数据库连接
```

### DAG 原则

```text
1. Workflow 是有向无环图（DAG）
2. 必须有明确的开始节点和结束节点
3. 禁止出现环路
4. 并行分支必须有汇聚节点
5. 条件分支必须有默认路径
```

### 状态流转

```text
Workflow 状态：
  CREATED → RUNNING → COMPLETED
                   → FAILED
                   → CANCELLED

Node 状态：
  PENDING → RUNNING → SUCCESS
                   → FAILED
                   → SKIPPED
```

### Context 传递

```java
/**
 * 节点上下文，用于节点间数据传递
 */
public class NodeContext {
    
    /** 全局变量 */
    private Map<String, Object> variables;
    
    /** 当前节点输入 */
    private NodeInput input;
    
    /** 上一个节点输出 */
    private NodeOutput previousOutput;
    
    /** Workflow 级别的配置 */
    private WorkflowConfig config;
}
```

- Context 是只读的，节点不能修改全局变量
- 节点输出通过 `NodeResult` 返回，由引擎合并到 Context
- 敏感信息（API Key 等）不放入 Context

---

## Agent Runtime 规范

### 生命周期

```text
Agent 实例生命周期：
  1. 创建（Create）    - 从配置加载 Agent 定义
  2. 初始化（Init）    - 加载 Tool、Memory、Prompt
  3. 运行（Run）       - 接收用户输入，执行推理
  4. 销毁（Destroy）   - 释放资源

单次对话生命周期：
  1. 接收消息
  2. 加载记忆
  3. 构建 Prompt
  4. 调用模型
  5. 解析响应
  6. 执行 Tool（如有）
  7. 返回结果
```

### Memory 规范

```text
1. Memory 是对话历史的存储抽象
2. 支持短期记忆（当前会话）和长期记忆（跨会话）
3. Memory 必须有大小限制，防止无限增长
4. 敏感信息不存入 Memory
```

```java
public interface Memory {
    
    /** 加载历史消息 */
    List<Message> load(String conversationId, int limit);
    
    /** 保存新消息 */
    void save(String conversationId, Message message);
    
    /** 清空会话记忆 */
    void clear(String conversationId);
}
```

### Tool 调用规范

```text
1. Agent 调用 Tool 必须经过 ToolRouter 路由
2. Tool 调用必须设置超时时间（默认 30s）
3. Tool 执行失败必须返回明确的错误信息
4. 禁止 Agent 直接调用外部 API
5. Tool 调用链最大深度：10 层
```

### Streaming 规范

```text
1. 所有 LLM 调用默认使用流式输出
2. 流式响应使用 SSE（Server-Sent Events）
3. 前端必须处理流式中断和重连
4. Tool 调用结果不流式返回，等完整执行后返回
```

---

## Prompt 规范

### Prompt 模板化

```text
1. 所有 Prompt 必须使用模板，禁止硬编码
2. 模板存储位置：数据库 / 配置文件
3. 模板使用 Mustache 语法：{{variable}}
4. 模板必须有版本管理
```

模板结构：

```java
public class PromptTemplate {
    
    /** 模板 ID */
    private String id;
    
    /** 模板名称 */
    private String name;
    
    /** 模板内容（支持 Mustache 变量） */
    private String template;
    
    /** 变量定义 */
    private List<Variable> variables;
    
    /** 版本号 */
    private Integer version;
}
```

### 禁止 Prompt 硬编码

```java
// ❌ 错误
String prompt = "你是一个助手，请回答用户的问题";

// ✅ 正确
String prompt = promptTemplateService.render("agent.system.default", Map.of(
    "role", "助手",
    "task", "回答用户问题"
));
```

---

## Git 规范

### Commit 规范

```text
格式：<type>(<scope>): <subject>

type：
  feat     - 新功能
  fix      - Bug 修复
  docs     - 文档
  style    - 代码格式（不影响逻辑）
  refactor - 重构
  perf     - 性能优化
  test     - 测试
  chore    - 构建/工具变更

scope：模块名（agent / workflow / tool / rag / common）

示例：
  feat(agent): 新增 Agent 创建接口
  fix(workflow): 修复并行节点执行顺序问题
  refactor(tool): 抽象 Tool 基类
```

### PR 规范

```text
1. PR 标题使用 Commit 格式
2. PR 描述必须包含：
   - 变更内容
   - 影响范围
   - 测试说明
3. 一个 PR 只做一件事
4. PR 代码量控制在 500 行以内
5. 必须通过 CI 检查才能合并
6. 必至少 1 人 Review
```

PR 模板：

```markdown
## 变更内容
- 新增 xxx 功能

## 影响范围
- lightbot-agent 模块

## 测试说明
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 手动测试通过

## 关联 Issue
- #123
```

---

## 附录

### 代码生成 Checklist

```text
□ 是否符合包结构规范？
□ 是否添加了必要的注释？
□ 是否使用了统一的返回封装？
□ 是否处理了异常情况？
□ 是否避免了重复代码？
□ 是否遵循依赖方向？
□ 是否有硬编码的 Prompt？
□ 是否直接调用了外部 SDK？
```

### Review Checklist

```text
□ 架构原则是否违反？
□ 是否有循环依赖？
□ 是否有跨模块直接调用？
□ 异常处理是否完善？
□ 是否有安全风险（SQL注入、XSS）？
□ 是否有性能风险（N+1查询、大事务）？
□ 测试覆盖是否足够？
```
