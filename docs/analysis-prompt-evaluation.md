# 提示词评测功能 — 参考分析与迁移方案

> 参考项目：spring-ai-alibaba-admin
> 目标项目：LightBot
> 日期：2026-05-27

---

## 一、参考项目概览

spring-ai-alibaba-admin 的提示词评测系统采用 **"LLM-as-Judge"** 模式：用大模型来评判大模型的输出质量。

核心概念三角：

```
评测集 (Dataset)  ──→  实验 (Experiment)  ←──  评估器 (Evaluator)
   测试数据                执行单元                评分规则
```

- **评测集**：一组测试用例（input + reference_output），支持版本管理
- **评估器**：一个 Prompt 模板，指导 LLM 如何打分，返回 `{score, reason}`
- **实验**：将评测集 + 被测 Prompt + 评估器组合，批量执行并汇总结果

---

## 二、参考项目数据库设计（7 张表）

### 2.1 表关系总览

```
dataset ──1:N──→ dataset_version ──1:N──→ dataset_item
evaluator ──1:N──→ evaluator_version
evaluator_template（预置模板）
experiment ──1:N──→ experiment_result
experiment ──N:1──→ dataset_version
experiment ──N:N──→ evaluator_version
```

### 2.2 各表字段明细

| 表名 | 核心字段 | 说明 |
|------|----------|------|
| `dataset` | id, name, description, columns_config(JSON) | 评测集定义，columns_config 定义列结构 |
| `dataset_version` | id, dataset_id, version, data_count, status, dataset_items(JSON) | 版本化管理，dataset_items 存数据项ID列表 |
| `dataset_item` | id, dataset_id, data_content(JSON) | 单条测试数据，JSON 格式存 input/reference_output |
| `evaluator` | id, name, description | 评估器定义 |
| `evaluator_version` | id, evaluator_id, version, model_config(JSON), prompt, variables(JSON), status | 评估器的 Prompt 模板和模型配置 |
| `evaluator_template` | id, evaluator_template_key, template, variables, model_config | 预置评估模板（文本相似度/代码质量/情感分析） |
| `experiment` | id, name, dataset_id, dataset_version_id, evaluation_object_config(JSON), evaluator_config(JSON), status, progress | 实验配置与状态 |
| `experiment_result` | id, experiment_id, input, actual_output, reference_output, score(DECIMAL 3,2), reason, evaluator_version_id | 单条评测结果 |

### 2.3 关键设计点

- **JSON 字段大量使用**：columns_config、data_content、evaluation_object_config、evaluator_config、variables 均为 JSON/JSONB
- **版本化管理**：评测集和评估器都独立版本化，实验绑定到具体版本，确保可复现
- **score 精度**：DECIMAL(3,2)，范围 0.00-1.00

---

## 三、核心业务流程

### 3.1 创建实验（4 步向导）

```
步骤1: 基本信息  →  名称 + 描述
步骤2: 选择评测集  →  选评测集 + 版本
步骤3: 配置评测对象  →  选 Prompt + 版本 + 变量映射（Prompt变量 → 评测集字段）
步骤4: 配置评估器  →  选评估器 + 版本 + 参数映射（评估器变量 → 数据来源）
```

### 3.2 实验执行流程

```
创建实验 → 异步启动线程池（5线程）
         ↓
    解析 evaluation_object_config（评测对象类型 + Prompt配置）
         ↓
    加载数据集版本 → 解析 dataset_items → 批量查询 DatasetItem
         ↓
    遍历每条数据项:
      ├── (a) 调用被测 Prompt → 替换变量 → 调用 LLM → 获取 actual_output
      ├── (b) 调用评估器 → 替换变量（actual_output/reference_output） → 调用 LLM → 获取 {score, reason}
      ├── (c) 保存 experiment_result
      └── (d) 更新进度（每条 +1）
         ↓
    全部完成 → 更新状态为 COMPLETED
```

### 3.3 评分机制

- **完全依赖 LLM 返回**：评估器 Prompt 指导模型如何评分，模型返回 JSON `{score, reason}`
- **强制格式**：在评估器 Prompt 后追加系统指令，要求只返回 JSON
- **分数解析**：从可能包含 Markdown 代码块的响应中提取纯文本，再解析 JSON
- **汇总**：按评估器版本分组计算算术平均分

### 3.4 双层变量映射

```
第一层：评测集字段 → 被测 Prompt 变量
  例：dataset.input → Prompt.question

第二层：数据来源 → 评估器变量
  例：actual_output → 评估器.actual_output
      dataset.reference_output → 评估器.reference_output
```

---

## 四、LightBot 迁移分析

### 4.1 现有基础设施对比

| 能力 | spring-ai-alibaba-admin | LightBot | 差距 |
|------|--------------------------|----------|------|
| AI 调用 | SpringAI ChatClient | SpringAI（已集成） | **无差距** |
| Prompt 管理 | 独立 Prompt 表 + 版本 | AgentVersion.config JSONB 内嵌 | **需新建** |
| 模型配置 | 模型管理（已对接） | Model + ModelProvider 表 | **无差距** |
| 异步任务 | 线程池 + 状态管理 | Task 表 + 状态枚举 | **可复用** |
| 前端框架 | React + Ant Design | Vue3 + Arco Design | **需重写前端** |
| 数据库 | MySQL | PostgreSQL | **需适配** |

### 4.2 需要新建的内容

#### 后端（6 个 Entity + 6 个 Service + 3 个 Controller）

| 组件 | 说明 | 与现有模块的关系 |
|------|------|-----------------|
| `Dataset` / `DatasetVersion` / `DatasetItem` | 评测集管理 | 独立新模块 |
| `Evaluator` / `EvaluatorVersion` / `EvaluatorTemplate` | 评估器管理 | 独立新模块 |
| `Experiment` / `ExperimentResult` | 实验执行与结果 | 可复用 Task 异步机制 |
| DatasetController | 评测集 CRUD | 新增 |
| EvaluatorController | 评估器 CRUD + 调试 | 新增 |
| ExperimentController | 实验 CRUD + 执行 | 新增 |

#### 前端（3 个页面模块）

| 页面 | 功能 |
|------|------|
| 评测集管理 | 创建评测集、管理版本、编辑数据项（表格编辑） |
| 评估器管理 | 创建评估器、管理版本、调试评估器（实时调用） |
| 实验管理 | 4步创建向导、实验列表、结果详情（概览+明细） |

### 4.3 可复用的现有组件

| 组件 | 复用方式 |
|------|----------|
| `Model` + `ModelProvider` | 评估器选择模型时直接复用 |
| `Task` + TaskService | 实验执行的异步任务框架可复用 |
| `PromptTemplateUtil` | 评估器变量替换可复用 |
| `ChatService` / SpringAI ChatClient | LLM 调用层直接复用 |
| `LlmTrace` | 评测过程的 LLM 调用可自动纳入 Trace |
| `Result<T>` / `PageVO<T>` | 统一返回封装直接复用 |

---

## 五、迁移难点分析

### 5.1 高难度

| 难点 | 说明 | 建议方案 |
|------|------|----------|
| **Prompt 管理体系缺失** | LightBot 目前 Prompt 内嵌在 AgentVersion.config 中，无独立的 Prompt 版本管理。评测系统需要独立选择"被测 Prompt" | **方案A**：新建 prompt + prompt_version 表，从 Agent 配置中抽取 Prompt 独立管理；**方案B**：直接用 AgentVersion 作为评测对象（评测整个 Agent 而非单个 Prompt） |
| **双层变量映射** | 数据集字段 → Prompt 变量 → 评估器变量，两层映射的前端交互复杂 | 参考原项目的 VariableMapItem 设计，前端用下拉选择 + 动态表单实现 |
| **LLM 输出解析稳定性** | 评估器依赖 LLM 返回标准 JSON，但模型可能返回格式错误的内容 | 需要健壮的解析逻辑：Markdown 代码块提取 + JSON 解析 + 重试机制 + 默认值兜底 |

### 5.2 中难度

| 难点 | 说明 | 建议方案 |
|------|------|----------|
| **评测集数据编辑** | 前端需要一个可编辑表格，支持动态列（根据 columns_config 生成） | 使用 Arco Design 的 Table 组件 + 动态列配置 |
| **实验进度实时更新** | 前端需要轮询或 WebSocket 获取进度 | 初版用轮询（每 2 秒 GET 进度），后续可升级 SSE |
| **PostgreSQL 适配** | 原项目用 MySQL，JSON 函数语法不同 | JSONB 字段使用 PG 原生 JSON 操作符（`->>`、`#>`等） |

### 5.3 低难度

| 难点 | 说明 |
|------|------|
| 版本管理 | LightBot 已有 AgentVersion 的版本化经验，直接复用模式 |
| 异步执行 | Task 表已存在，实验状态流转可复用 |
| 评估器模板 | 纯数据初始化，INSERT 3 条记录即可 |

---

## 六、LightBot 适配方案（与原项目的差异点）

### 6.1 评测对象的取舍

原项目评测的是"Prompt"（独立的 Prompt 版本），但 LightBot 的 Prompt 是内嵌在 Agent 配置中的。

**建议方案：评测 Agent 而非独立 Prompt**

理由：
1. LightBot 的 Agent 是一等公民，Prompt 是 Agent 的一部分
2. 避免新建 prompt + prompt_version 两张表
3. 更贴合实际使用场景：用户关心的是"我的 Agent 表现如何"

调整后的 evaluation_object_config：

```json
{
  "type": "agent",
  "config": {
    "agentId": "123",
    "agentVersionId": "456",
    "variableMap": [
      {"agentVariable": "user_input", "datasetColumn": "input"}
    ]
  }
}
```

### 6.2 数据库适配（MySQL → PostgreSQL）

| 原项目（MySQL） | LightBot（PostgreSQL） |
|-----------------|----------------------|
| BIGINT(20) 自增 | BIGINT + 雪花算法（IdType.ASSIGN_ID） |
| LONGTEXT | TEXT（PG 无 LONGTEXT） |
| TINYINT(1) | SMALLINT |
| DATETIME | TIMESTAMP |
| JSON 字段用 TEXT 存储 | JSONB 原生类型 |

### 6.3 表名适配

遵循 LightBot 规范（不加 `t_` 前缀，PG 保留字需换名）：

| 原表名 | LightBot 表名 | 说明 |
|--------|--------------|------|
| dataset | eval_dataset | 加 eval_ 前缀区分业务域 |
| dataset_version | eval_dataset_version | |
| dataset_item | eval_dataset_item | |
| evaluator | eval_evaluator | |
| evaluator_version | eval_evaluator_version | |
| evaluator_template | eval_evaluator_template | |
| experiment | eval_experiment | |
| experiment_result | eval_experiment_result | |

### 6.4 Entity 示例（适配 LightBot 规范）

```java
@Data
@TableName("eval_experiment")
@Schema(description = "评测实验表")
public class EvalExperiment {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("user_id")
    @Schema(description = "创建者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @TableField("name")
    @Schema(description = "实验名称")
    private String name;

    @TableField("description")
    @Schema(description = "实验描述")
    private String description;

    @TableField("dataset_id")
    @Schema(description = "评测集ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;

    @TableField("dataset_version_id")
    @Schema(description = "评测集版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetVersionId;

    @TableField(value = "evaluation_object_config", typeHandler = JsonbTypeHandler.class)
    @Schema(description = "评测对象配置")
    private String evaluationObjectConfig;

    @TableField(value = "evaluator_config", typeHandler = JsonbTypeHandler.class)
    @Schema(description = "评估器配置")
    private String evaluatorConfig;

    @TableField("status")
    @Schema(description = "实验状态")
    private ExperimentStatus status;

    @TableField("progress")
    @Schema(description = "进度百分比")
    private Integer progress;

    @TableField("complete_time")
    @Schema(description = "完成时间")
    private LocalDateTime completeTime;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除")
    private Integer deleted;
}
```

---

## 七、实施建议

### 7.1 分阶段交付

| 阶段 | 内容 | 工作量估算 |
|------|------|-----------|
| **P1：数据层** | 8 张表 + Entity + Mapper + Service 骨架 | 2-3 天 |
| **P2：评测集 + 评估器 CRUD** | 后端接口 + 前端页面 | 3-4 天 |
| **P3：实验执行引擎** | 异步执行 + LLM 调用 + 结果保存 | 3-4 天 |
| **P4：实验前端** | 创建向导 + 结果展示 | 3-4 天 |
| **P5：评估器模板 + 调试** | 预置模板 + 单次调试功能 | 1-2 天 |
| **合计** | | **12-17 天** |

### 7.2 优先级建议

1. **先做评测集 + 评估器的 CRUD**（纯数据管理，无 AI 依赖，可独立验证）
2. **再做实验执行引擎**（核心难点，需要 LLM 调用 + 异步 + 进度管理）
3. **最后做前端交互**（创建向导最复杂，结果展示相对简单）

### 7.3 技术风险点

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| LLM 返回格式不稳定 | 评分失败率高 | 多次重试 + 宽松解析 + 默认分数兜底 |
| 大数据量评测耗时长 | 用户体验差 | 控制并发数 + 进度实时反馈 + 支持中途停止 |
| 评估器 Prompt 质量 | 评分不准确 | 提供预置模板 + 支持用户自定义调试 |
| Agent 调用链路长 | 实验执行超时 | 单条数据设置超时 + 失败跳过继续 |

---

## 八、结论

**迁移难度：中等偏低**

核心原因：
1. LightBot 已有完整的 SpringAI 集成，LLM 调用层无需重建
2. LightBot 的 Entity/Service/Mapper 规范清晰，新表创建有章可循
3. 异步任务机制已有 Task 表基础

主要工作量在：
- **前端**（Vue3 重写 React 页面，约 60% 工作量）
- **评测对象适配**（从"评测 Prompt"改为"评测 Agent"，需调整配置结构和调用逻辑）
- **数据库适配**（MySQL → PostgreSQL 的类型和 JSON 操作差异）

建议先实现最小可用版本（评测集 + 评估器 + 单个评估器的实验），验证 LLM 评分效果后再扩展多评估器、评估器模板等高级功能。
