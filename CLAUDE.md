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
❌ 禁止使用拼接SQL语句操作数据库（必须使用 MyBatis-Plus）
❌ 禁止在 Service 中直接操作中间件客户端（必须通过 Util 类）
```

### 必须遵守

```text
✅ 所有模型调用必须通过统一 AI Framework（SpringAI）
✅ 所有跨模块调用必须通过 Facade 接口
✅ 所有 Prompt 必须模板化，存储于配置或数据库
✅ 所有外部依赖通过依赖注入，禁止静态方法调用
✅ 所有数据库操作必须使用 MyBatis-Plus（LambdaQueryWrapper / ServiceImpl）
✅ 所有中间件操作（MinIO/Redis/MQ等）必须封装为 Util 类
✅ 业务核心逻辑必须有注释说明意图
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
├── service/           # 业务接口定义
│   └── impl/          # 业务实现类
├── entity/            # 数据库实体
├── dto/               # 数据传输对象
├── mapper/            # MyBatis-Plus Mapper 接口
├── util/              # 工具类（中间件封装：MinIO/Redis/MQ等）
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
 * 命名：数据库表名驼峰化，不加 t_ 前缀
 * 表名：agent
 * 注意：PG保留字（如 user）需换名（如 users）
 */
@Data
@TableName("agent")
@Schema(description = "Agent表")
public class Agent {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Schema(description = "Agent名称")
    private String name;

    @TableField("system_prompt")
    @Schema(description = "系统提示词")
    private String systemPrompt;

    @TableField(value = "config", typeHandler = JsonNodeTypeHandler.class)
    @Schema(description = "扩展配置")
    private String config;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
```

- 主键统一使用雪花算法 `IdType.ASSIGN_ID`
- **表名不加 `t_` 前缀**，直接使用业务名（如 `user`、`agent`、`knowledge`）
- **每个字段必须加 `@TableField`**：明确指定数据库列名
- **每个字段必须加 `@Schema`**：使用 OpenAPI 3 注解，替代 Javadoc 注释
- **JSONB 字段**：使用 `String` 类型 + `@TableField(value = "xxx", typeHandler = JsonNodeTypeHandler.class)`
- **type/status 字段必须使用 Java 枚举**（配合 `@EnumValue` + `@JsonValue`），不使用数据库枚举
- 必须包含 `createTime`、`updateTime`、`deleted` 字段
- 使用 `@TableLogic` 实现逻辑删除
- 如果遇到SQL更新，要放到整个文件夹根目录下的sql文件夹下，并且以日期-001.sql文件命名，如果存在则编号递增,再次注意！不是docs文件夹下！
- **Long ID 字段必须加 `@JsonSerialize(using = ToStringSerializer.class)`**：包括主键和所有外键字段（如 `userId`、`agentId`、`knowledgeId` 等），防止前端 JavaScript 精度丢失

### Service 规范

**必须遵循 Interface + ServiceImpl 模式：**

```java
/**
 * 接口定义（放在 service/ 包）
 * 继承 IService<Entity> 可获得 MyBatis-Plus 内置 CRUD 方法
 */
public interface AgentService extends IService<Agent> {
    Agent create(AgentCreateDTO dto);
    Page<Agent> listMyAgents(int pageNum, int pageSize);
}

/**
 * 实现类（放在 service/impl/ 包）
 * 命名：{业务名}ServiceImpl
 */
@Service
@RequiredArgsConstructor
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent>
        implements AgentService {

    @Override
    public Agent create(AgentCreateDTO dto) {
        // 1. 参数校验
        // 2. 业务处理（核心逻辑必须有注释）
        // 3. 返回结果
    }
}
```

- **接口与实现必须分离**：接口在 `service/`，实现在 `service/impl/`
- 需要 MyBatis-Plus 内置方法的 Service，接口继承 `IService<Entity>`，实现继承 `ServiceImpl<Mapper, Entity>`
- 一个 Service 只处理一个业务域
- 禁止在 Service 中直接调用其他模块的 Mapper

### Controller 规范（纯透传）

```text
❌ 禁止在 Controller 中写业务逻辑（包括权限校验、数据查询、状态变更）
❌ 禁止在 Controller 中注入 Mapper（包括 BaseMapper、自定义Mapper）
❌ 禁止在 Controller 中构建 LambdaQueryWrapper / QueryWrapper
✅ Controller 只做参数接收 + 调用 Service + 返回 Result
✅ 权限校验、业务判断、数据操作一律放在 ServiceImpl 中
```

**正确示例：**
```java
@GetMapping("/{id}/documents")
public Result<List<Document>> listDocuments(@PathVariable Long id) {
    return Result.ok(documentService.listByKnowledgeId(id));
}
```

**错误示例：**
```java
// ❌ Controller 中直接构建查询
@GetMapping("/{id}/documents")
public Result<List<Document>> listDocuments(@PathVariable Long id) {
    checkMember(id);
    List<Document> docs = documentService.list(
        new LambdaQueryWrapper<Document>().eq(Document::getKnowledgeId, id));
    return Result.ok(docs);
}
```

### Long ID 序列化规范

```text
所有 Entity 的主键 ID 字段必须添加 @JsonSerialize(using = ToStringSerializer.class)
原因：雪花算法生成的 Long 类型 ID 超过 JavaScript Number.MAX_SAFE_INTEGER (2^53)
```

```java
@TableId(type = IdType.ASSIGN_ID)
@Schema(description = "主键ID")
@JsonSerialize(using = ToStringSerializer.class)
private Long id;
```

### Util 规范（中间件封装）

```java
/**
 * 中间件工具类（放在 util/ 包）
 * 命名：{中间件名}Util
 * 用途：封装 MinIO/Redis/MQ 等中间件的客户端操作
 */
@Slf4j
@Component
public class MinioUtil {
    private final MinioClient minioClient;

    public String upload(MultipartFile file, String filePath) { ... }
    public InputStream download(String filePath) { ... }
    public void delete(String filePath) { ... }
}
```

- **所有中间件操作必须封装为 Util 类**，禁止在 Service 中直接操作中间件客户端
- Util 类只做技术封装，不包含业务逻辑
- 业务逻辑（如路径生成、权限校验）放在 Service 层
- 常见 Util：`MinioUtil`、`RedisUtil`、`OssUtil` 等

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

## 开发准则

### 1. 编码前思考

**不要假设，不要隐藏困惑，暴露权衡。**

- 实现前明确说明你的假设。不确定时，主动提问。
- 如果存在多种解读方式，列出它们 — 不要默默选择一种。
- 如果有更简单的方案，提出来。必要时可以反驳。
- 如果某个地方不清晰，停下来。说清楚哪里困惑你，然后提问。

### 2. 简单优先

**解决问题的最少代码，不做投机性设计。**

- 不做超出需求的功能。
- 不为一次性代码做抽象。
- 不添加未要求的"灵活性"或"可配置性"。
- 不为不可能的场景做错误处理。
- 如果 200 行能缩到 50 行，重写它。

问自己："高级工程师会觉得这过度设计了吗？" 如果是，简化。

### 3. 精准修改

**只动必须动的。只清理自己制造的混乱。**

编辑现有代码时：
- 不要"顺手改进"相邻的代码、注释或格式。
- 不要重构没坏的东西。
- 匹配现有风格，即使你会用不同的方式写。
- 如果发现无关的死代码，提一下 — 但不要删。

你的改动产生孤立代码时：
- 移除你的改动使其变得无用的 import/变量/函数。
- 不要移除改动之前就存在的死代码，除非被要求。

检验标准：每一行改动都应直接追溯到用户的需求。

### 4. 前端开发

- 使用 **pnpm** 管理前端依赖
- 图标优先使用 **lucide-vue-next**（注意尺寸）
- 样式使用 CSS 变量统一管理颜色
- 界面 logo 统一使用 `public/lightbot-logo.png`

### 5. 后端开发

- 不允许把代码写得稀碎：不要为简单线性逻辑拆出一堆细碎 helper
- 优先写成职责清晰、结构完整、可一眼读懂的实现
- 拆函数必须服务于明确的复用、隔离副作用或降低认知负担
- 如果拆分后调用链更绕、上下文更分散，就应合并回更直接的实现

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

### 数据库规范

```text
1. 表名不加 t_ 前缀，直接使用业务名（agent、knowledge 等）
2. PostgreSQL 保留字不能用作表名/列名（如 user → users，order → orders，group → groups）
3. 索引命名：idx_{表名}_{字段名}，唯一索引：uk_{表名}_{字段名}
4. 所有表必须包含 id、create_time、update_time、deleted 字段（关联表可省略 deleted）
5. type/status 字段使用 VARCHAR 存储 Java 枚举的 code 值
6. 禁止使用数据库枚举类型
7. 数据库变更 SQL 文件统一放在项目 `docs/sql/` 目录，命名格式：`YYYY-MM-DD-NNN.sql`（如 `2026-05-19-001.sql`）
   - 同一天多个变更：001、002、003 依次递增
   - 写入前先检查目录中是否已有当天文件，序号顺延
8. SQL 文件中必须用注释说明变更内容（CREATE TABLE / ALTER TABLE / 新增索引等）
```

建表模板（PostgreSQL）：

```sql
CREATE TABLE agent (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_status ON agent (status);
COMMENT ON TABLE agent IS 'Agent表';
```

### 数据库操作规范（MyBatis-Plus）

```text
1. 优先使用 MyBatis-Plus 提供的方法（LambdaQueryWrapper / ServiceImpl）
2. 禁止在 Service 中手动拼接 SQL 字符串
3. 复杂查询使用 MyBatis-Plus 的 LambdaQueryWrapper 链式调用
4. 分页查询使用 MyBatis-Plus 的 Page 对象
5. 当 SQL 过于复杂或 MyBatis-Plus 无法表达时（如 pgvector 向量操作、多表联查），
   必须在 Mapper 接口中使用 @Select/@Update 注解或 XML 映射文件编写 SQL
6. 禁止在 Service 中使用 JdbcTemplate 拼接 SQL
```

```java
// ✅ 正确：使用 LambdaQueryWrapper
List<Agent> agents = list(new LambdaQueryWrapper<Agent>()
        .eq(Agent::getUserId, userId)
        .eq(Agent::getStatus, AgentStatus.PUBLISHED)
        .orderByDesc(Agent::getCreateTime));

// ✅ 正确：复杂SQL放在 Mapper 中
@Mapper
public interface EmbeddingMapper extends BaseMapper<Embedding> {
    @Select("SELECT c.id, c.content FROM embedding e JOIN chunk c ON e.chunk_id = c.id WHERE ...")
    List<Map<String, Object>> searchSimilar(@Param("vector") String vector, @Param("knowledgeId") Long knowledgeId);
}

// ❌ 错误：在 Service 中拼接 SQL
String sql = "SELECT * FROM agent WHERE user_id = " + userId;
jdbcTemplate.queryForList(sql);
```

### 业务注释规范

```text
1. Service 实现类中的核心业务逻辑必须有注释，说明"做什么"和"为什么"
2. 注释使用编号步骤：// 1. 参数校验、// 2. 核心处理、// 3. 返回结果
3. 主步骤间空一行，步骤内紧凑无空行
4. 不注释显而易见的代码（如 getter/setter），只注释业务意图
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
