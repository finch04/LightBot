# RFC-002: Workflow Engine

| 字段 | 值 |
|------|------|
| RFC 编号 | 002 |
| 标题 | Workflow Engine |
| 状态 | Draft |
| 作者 | LightBot Team |
| 创建日期 | 2025-05-19 |
| 最后更新 | 2025-05-19 |
| 关联模块 | lightbot-core/workflow |

---

## 1. 背景

单一 Agent 能力有限，企业场景中常见的多步骤业务流程（如：客服工单处理、文档审核、数据分析）需要将多个 Agent、Tool、条件判断、人工审批串联为一个完整的 Workflow。

Dify 的 Workflow 和 n8n 的节点编排已经验证了可视化工作流在 AI 应用中的价值。LightBot 需要一套**轻量、可嵌入、支持 DAG 调度**的 Workflow 引擎，既可独立运行，也可作为 Agent 的上层编排器。

---

## 2. 问题定义

### 2.1 核心问题

**如何设计一个面向 AI 场景的 Workflow 引擎，使其满足：**

1. **可视化编排** — 前端通过拖拽画布定义 Workflow，无需编码
2. **DAG 执行** — 支持有向无环图拓扑排序，支持串行、并行、条件分支
3. **节点类型丰富** — LLM 节点、Tool 节点、条件节点、变量赋值节点、人工审批节点
4. **变量透传** — 节点间通过变量上下文传递数据
5. **可调试** — 支持单节点调试、运行时变量查看、断点暂停
6. **可恢复** — 长时间运行的 Workflow 支持中断与断点恢复

### 2.2 约束条件

| 约束 | 说明 |
|------|------|
| 执行模型 | 同步 + 异步混合，LLM 调用为异步阻塞 |
| 并发度 | 单 Workflow 内并行节点数 ≤ 20 |
| 运行时长 | 单次 Workflow 执行 ≤ 30 分钟 |
| 状态存储 | 执行状态持久化到 PostgreSQL，支持故障恢复 |

---

## 3. 设计目标

| 优先级 | 目标 | 衡量标准 |
|--------|------|----------|
| P0 | **DAG 执行引擎** | 拓扑排序 + 并发执行，支持串行/并行/分支 |
| P0 | **节点类型** | 至少支持 LLM、Tool、Condition、Variable 四种节点 |
| P0 | **变量系统** | 节点间变量引用，支持 `{{nodeId.output}}` 语法 |
| P1 | **可视化画布** | Vue Flow 画布，节点拖拽、连线、配置面板 |
| P1 | **调试模式** | 单节点执行、变量快照、运行日志 |
| P2 | **断点恢复** | 执行中断后可从断点节点恢复 |
| P2 | **超时与熔断** | 节点级超时、Workflow 级超时、异常熔断 |

---

## 4. 非目标

| 非目标 | 原因 |
|--------|------|
| 人工审批节点（v0.3） | 需要异步等待机制，复杂度高，v1.0 考虑 |
| 子 Workflow 嵌套 | 增加调试复杂度，v1.0 考虑 |
| 定时触发 | 需要调度器，属于平台层能力 |
| 版本管理 | v1.0 再考虑 Workflow 版本回滚 |
| 分布式执行 | 单体架构足够，不过早引入分布式复杂度 |

---

## 5. 核心架构

### 5.1 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                   Workflow Engine Architecture               │
├─────────────────────────────────────────────────────────────┤
│  Design Layer (Vue Flow)                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                    │
│  │ Canvas   │ │ Node     │ │ Property │                    │
│  │ Renderer │ │ Palette  │ │ Panel    │                    │
│  └──────────┘ └──────────┘ └──────────┘                    │
├─────────────────────────────────────────────────────────────┤
│  API Layer                                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Workflow API│  │ Execution   │  │ Debug API   │        │
│  │ CRUD        │  │ API         │  │             │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
├─────────────────────────────────────────────────────────────┤
│  Engine Layer                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              WorkflowEngine                          │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │   │
│  │  │ DAG      │ │ Node     │ │ Variable │            │   │
│  │  │ Scheduler│ │ Executor │ │ Context  │            │   │
│  │  └──────────┘ └──────────┘ └──────────┘            │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │   │
│  │  │ State    │ │ Debug    │ │ Event    │            │   │
│  │  │ Manager  │ │ Tracer   │ │ Bus      │            │   │
│  │  └──────────┘ └──────────┘ └──────────┘            │   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  Node Registry                                              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐     │
│  │ LLM Node │ │Tool Node │ │Condition │ │Variable  │     │
│  │          │ │          │ │  Node    │ │  Node    │     │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘     │
├─────────────────────────────────────────────────────────────┤
│  Storage                                                    │
│  ┌──────────────┐  ┌──────────────┐                       │
│  │  PostgreSQL  │  │    Redis     │                       │
│  │  (定义+历史) │  │  (执行状态)  │                       │
│  └──────────────┘  └──────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 核心组件

| 组件 | 职责 | 接口 |
|------|------|------|
| `WorkflowEngine` | Workflow 执行入口 | `WorkflowResult execute(WorkflowRequest)` |
| `DAGScheduler` | 拓扑排序、并发调度 | `List<NodeGroup> schedule(WorkflowGraph)` |
| `NodeExecutor` | 单节点执行 | `NodeResult execute(Node, NodeContext)` |
| `VariableContext` | 变量存储与解析 | `void set(nodeId, key, value)` / `Object resolve(expression)` |
| `StateManager` | 执行状态持久化与恢复 | `void saveState(ExecutionState)` / `ExecutionState loadState(executionId)` |
| `DebugTracer` | 调试信息收集 | `void trace(Node, input, output, duration)` |
| `NodeRegistry` | 节点类型注册 | `NodeExecutor getExecutor(NodeType)` |

---

## 6. DAG 调度

### 6.1 拓扑排序

Workflow 以 DAG（有向无环图）表示，执行前进行拓扑排序，确保依赖关系正确。

```java
public class DAGScheduler {

    /**
     * 将 Workflow Graph 转换为可执行的节点分组
     * 同一组内的节点可并行执行
     */
    public List<NodeGroup> schedule(WorkflowGraph graph) {
        // 1. 校验无环
        validateNoCycle(graph);

        // 2. 拓扑排序（Kahn 算法）
        Map<String, Integer> inDegree = calculateInDegree(graph);
        Queue<String> queue = inDegree.entrySet().stream()
            .filter(e -> e.getValue() == 0)
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(LinkedList::new));

        List<NodeGroup> groups = new ArrayList<>();
        while (!queue.isEmpty()) {
            int size = queue.size();
            NodeGroup group = new NodeGroup();
            for (int i = 0; i < size; i++) {
                String nodeId = queue.poll();
                group.addNode(graph.getNode(nodeId));
                for (String successor : graph.getSuccessors(nodeId)) {
                    inDegree.merge(successor, -1, Integer::sum);
                    if (inDegree.get(successor) == 0) {
                        queue.add(successor);
                    }
                }
            }
            groups.add(group);
        }
        return groups;
    }
}
```

### 6.2 执行模型

```
Group 0        Group 1           Group 2
┌──────┐      ┌──────┐         ┌──────┐
│ Start│─────▶│ LLM  │────────▶│ Tool │
└──────┘      │ Node │         │ Node │
              ├──────┤         ├──────┤
              │ Var  │────────▶│ End  │
              │ Node │         │ Node │
              └──────┘         └──────┘
             (并行执行)         (串行/并行)
```

```java
public class WorkflowEngine {

    public WorkflowResult execute(WorkflowRequest request) {
        WorkflowGraph graph = buildGraph(request.getDefinition());
        List<NodeGroup> groups = dagScheduler.schedule(graph);
        VariableContext varCtx = new VariableContext();

        // 注入输入变量
        varCtx.set("workflow", "input", request.getInput());

        for (NodeGroup group : groups) {
            // 同组内并行执行
            List<CompletableFuture<NodeResult>> futures = group.getNodes().stream()
                .map(node -> CompletableFuture.supplyAsync(
                    () -> nodeExecutor.execute(node, varCtx), executor))
                .collect(Collectors.toList());

            // 等待本组全部完成
            for (int i = 0; i < futures.size(); i++) {
                NodeResult result = futures.get(i).join();
                Node node = group.getNodes().get(i);
                varCtx.set(node.getId(), "output", result.getOutput());

                // 条件分支：决定下一组
                if (node.getType() == NodeType.CONDITION) {
                    groups = handleConditionBranch(graph, node, result, groups);
                }
            }
        }

        return WorkflowResult.success(varCtx.get("end", "output"));
    }
}
```

---

## 7. 节点类型

### 7.1 节点定义

```java
public interface NodeExecutor {

    /** 支持的节点类型 */
    NodeType getNodeType();

    /** 执行节点 */
    NodeResult execute(Node node, VariableContext context);

    /** 校验节点配置 */
    ValidationResult validate(Node node);
}
```

### 7.2 内置节点

| 节点类型 | 类型标识 | 输入 | 输出 | 说明 |
|----------|----------|------|------|------|
| **Start** | `start` | - | 用户输入 | Workflow 入口 |
| **End** | `end` | 最终结果 | - | Workflow 出口 |
| **LLM** | `llm` | prompt, variables | response | 调用 LLM 生成内容 |
| **Tool** | `tool` | tool_name, arguments | result | 调用注册的 Tool |
| **Condition** | `condition` | expression | true/false 分支 | 条件判断 |
| **Variable** | `variable` | expression | value | 变量赋值/转换 |
| **Knowledge** | `knowledge` | query | documents | 知识库检索 |
| **Code** | `code` | variables | result | 自定义代码执行（JS/Python） |

### 7.3 LLM 节点示例

```json
{
    "id": "llm_1",
    "type": "llm",
    "name": "内容生成",
    "config": {
        "model": "gpt-4o",
        "systemPrompt": "你是一个内容创作助手",
        "userPrompt": "根据以下大纲生成文章：{{start.output}}",
        "temperature": 0.7,
        "maxTokens": 2000
    },
    "nextNodes": ["condition_1"]
}
```

### 7.4 条件节点

```json
{
    "id": "condition_1",
    "type": "condition",
    "name": "质量检查",
    "config": {
        "expression": "{{llm_1.output.length}} > 500",
        "trueBranch": "tool_1",
        "falseBranch": "llm_1"
    }
}
```

---

## 8. 变量系统

### 8.1 变量引用语法

```
{{nodeId.output}}              — 引用节点输出
{{nodeId.output.field}}        — 引用节点输出的嵌套字段
{{workflow.input}}             — 引用 Workflow 输入变量
{{workflow.variables.key}}     — 引用 Workflow 全局变量
```

### 8.2 变量上下文

```java
public class VariableContext {

    /** 节点输出存储：nodeId -> output */
    private final Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();

    /** 全局变量 */
    private final Map<String, Object> globalVariables = new ConcurrentHashMap<>();

    /** 设置节点输出 */
    public void set(String nodeId, String key, Object value) {
        nodeOutputs.put(nodeId + "." + key, value);
    }

    /** 解析变量表达式 {{nodeId.output}} */
    public Object resolve(String expression) {
        // 解析 {{xxx.yyy}} 格式
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        if (!matcher.matches()) {
            return expression; // 字面量，直接返回
        }
        String path = matcher.group(1);
        return resolvePath(path);
    }

    /** 解析嵌套路径 */
    private Object resolvePath(String path) {
        String[] parts = path.split("\\.");
        Object current = nodeOutputs.get(parts[0] + "." + parts[1]);
        for (int i = 2; i < parts.length && current != null; i++) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(parts[i]);
            }
        }
        return current;
    }
}
```

---

## 9. 状态管理与断点恢复

### 9.1 执行状态

```java
public class WorkflowExecutionState {

    private String executionId;
    private String workflowId;
    private ExecutionStatus status;    // PENDING / RUNNING / PAUSED / COMPLETED / FAILED
    private List<NodeExecutionState> nodeStates;
    private VariableContext variableContext;
    private String currentNodeId;      // 当前执行到的节点
    private int currentGroupIndex;     // 当前执行到的分组
    private long startedAt;
    private long updatedAt;
}

public class NodeExecutionState {
    private String nodeId;
    private NodeStatus status;         // PENDING / RUNNING / COMPLETED / FAILED / SKIPPED
    private Object input;
    private Object output;
    private long startedAt;
    private long completedAt;
    private String errorMessage;
}
```

### 9.2 状态持久化策略

| 场景 | 存储位置 | 说明 |
|------|----------|------|
| 执行中 | Redis | 高频更新，低延迟 |
| 执行完成 | PostgreSQL | 持久归档 |
| 执行中断 | Redis → PostgreSQL | 故障恢复时从 PostgreSQL 加载 |

### 9.3 断点恢复

```java
public class WorkflowRecoveryManager {

    public void recover(String executionId) {
        WorkflowExecutionState state = stateManager.loadState(executionId);
        if (state.getStatus() != ExecutionStatus.PAUSED
            && state.getStatus() != ExecutionStatus.FAILED) {
            return;
        }

        // 从上次中断的分组继续执行
        int resumeGroupIndex = state.getCurrentGroupIndex();
        VariableContext varCtx = state.getVariableContext();

        // 恢复执行
        workflowEngine.resume(state, resumeGroupIndex, varCtx);
    }
}
```

---

## 10. 调试模式

### 10.1 调试能力

| 能力 | 说明 |
|------|------|
| 单节点执行 | 只执行指定节点，依赖节点使用 mock 数据 |
| 变量快照 | 执行到任意节点时查看当前所有变量值 |
| 运行日志 | 记录每个节点的 input / output / duration |
| 断点暂停 | 在指定节点前暂停，等待用户确认继续 |
| 步进执行 | 逐节点执行，每步暂停 |

### 10.2 调试 API

```
POST   /api/workflows/{id}/debug/start     — 启动调试会话
POST   /api/workflows/{id}/debug/step      — 步进执行
GET    /api/workflows/{id}/debug/snapshot   — 获取变量快照
POST   /api/workflows/{id}/debug/resume     — 恢复执行
DELETE /api/workflows/{id}/debug/stop       — 停止调试
```

---

## 11. 风险分析

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| DAG 环检测遗漏 | 高 | Kahn 算法 + DFS 双重校验 |
| 循环分支导致无限执行 | 高 | 最大执行步数限制（默认 100 步） |
| 节点并行竞争变量 | 中 | 变量写入在节点完成后，节点内只读 |
| 长时间 Workflow 内存溢出 | 中 | 状态持久化到 Redis，内存只保留当前分组 |
| 前端画布与后端定义不一致 | 低 | 前后端使用统一的 Workflow Definition JSON Schema |

---

## 12. 后续演进

| 阶段 | 能力 |
|------|------|
| v0.3 | 基础 DAG 引擎 + 4 种节点类型 + 可视化画布 |
| v0.3+ | 人工审批节点、子 Workflow、定时触发 |
| v1.0 | Workflow 版本管理、执行回放、性能分析 |
| v1.0+ | 分布式执行、Workflow 市场 |

---

## 附录：配置项

```yaml
lightbot:
  workflow:
    # 最大并行节点数
    max-parallel-nodes: 20
    # 最大执行步数
    max-execution-steps: 100
    # Workflow 总超时（秒）
    execution-timeout-seconds: 1800
    # 节点超时（秒）
    node-timeout-seconds: 300
    # 状态持久化策略 (redis / postgres / redis-then-postgres)
    state-persistence: redis-then-postgres
    # 调试会话超时（秒）
    debug-session-timeout-seconds: 3600
```
