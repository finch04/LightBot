# LightBot 智能问数方案

> 数据池语义分析 · 对话式查数 / 统计 / 下钻  
> 状态：**主路径 = Agent 勾选数据分类即可问（该类下全部模型）** · 2026-07-31  
> 用途：产品方案 + 技术方案的单一依据；文末两套清单分别验收「产品效果是否达成」与「技术是否落地」  
> 迁移：已有库执行 `sql/2026-07-30-002.sql` 后重启后端  

### 配置简化原则（2026-07-31）

```text
主路径：数据模型写好字段中文名/说明 → Agent「可问数据」勾选数据分类 → 该类下模型均可问
后台自动：ensureFromModel 生成维度/默认指标（cnt、sum_*、avg_*）并刷画像
可选轻量增强（数据模型卡片「问数增强」抽屉）：
  业务说明 / 默认时间 / 敏感字段 / 默认过滤 / 自定义业务指标（含固化过滤）
不提供：独立问数 Tab、维度表、同义词、Relation 进日常 UI
跨模型：主推宽表；Relation 不进主路径
```

---

## 0. 文档关系

| 文档 | 职责 |
| --- | --- |
| [PRODUCT.md](PRODUCT.md) | 企业定位、角色、API Key、资产归属（本文服从其边界） |
| [权限配置.md](权限配置.md) | API Key 能力边界、`callerContext`、问数 `tenantDimensions` 行级隔离配置 |
| **本文 ASK_DATA.md** | 智能问数的**产品方案** + **技术方案** + 验收清单 |
| [ROADMAP.md](ROADMAP.md) | 版本节奏（问数落地后可在此挂版本条目） |
| [AGENTS.md](AGENTS.md) / [CLAUDE.md](CLAUDE.md) | 工程规范（实现时必须遵守） |

**阅读建议**：先读「一、产品方案」对齐效果与边界；实现时对照「二、技术方案」；验收时勾选「三、检查清单」。

---

# 一、产品方案

> 本节定义**产品效果与边界**，作为智能问数验收的产品依据（对齐 PRODUCT.md 写法）。

## P1. 一句话定位

智能问数是 LightBot 数据中心之上的**企业对话式分析能力**：建设者把数据模型字段语义写清楚，并在 Agent 上勾选可问模型；业务侧通过已发布 Agent（控制台调试或企业 API Key）用自然语言完成查数、统计、对比、趋势与下钻，得到**可信任、可解释、可追问**的分析结果。

```text
┌─────────────────────────────────────────────────────────────┐
│  建设侧（LightBot 控制台）                                    │
│  数据中心：表单设计 · 数据池 · 数据模型（字段中文名/说明）     │
│  Agent：勾选「可问数据」· 问数 Skill/模板 · 平台内调试对话    │
└────────────────────────────┬────────────────────────────────┘
                             │ 企业 API Key / 控制台 Chat
                             ▼
┌─────────────────────────────────────────────────────────────┐
│  消费侧                                                      │
│  自然语言提问 → 结论 + 图表 + 表格 + 口径说明 + 推荐追问     │
│  终端用户只在外部产品中使用（不登录 LightBot）                │
└─────────────────────────────────────────────────────────────┘
```

## P2. 要解决的问题

| 现状痛点 | 产品目标 |
| --- | --- |
| 数据池只有 CRUD / 等值筛选，不会「问」 | 自然语言即可查数与统计 |
| 直接挂数据库工具不安全、无业务语义 | 按企业口径出数，不裸奔 SQL |
| 模型容易编数字、口径不一致 | 命名指标 + 可解释假设 + 评测闭环 |
| 答案只是一段文字/JSON | Insight 一等公民：结论 → 图 → 表 → 细节 |
| 多轮追问每次重猜 | 记住上次分析，支持「换成按月」「只看浙A」 |

## P3. 边界假设（服从 PRODUCT）

| 项 | 结论 |
| --- | --- |
| 部署 | 单企业；问数资产企业共享 |
| 谁配置口径 | 建设者 / 管理员（数据模型字段语义 + Agent 勾选） |
| 谁正式消费 | 企业 API Key 调用已授权 Agent；终端用户不进 LightBot |
| 平台内 Chat | 建设调试，不是对外正式形态 |
| 数据池 Open CRUD | 业务系统写数 / 集成；**不是**问数主入口 |
| 多租户 / 部门硬隔离 | 不做（与 PRODUCT 一致） |

## P4. 角色与职责

| 角色 | 问数职责 |
| --- | --- |
| **管理员** | 同建设者；企业 API Key、危险治理；可要求默认问数 Agent / 模型策略 |
| **建设者** | 维护数据模型与数据池（字段中文名/说明）；在 Agent「可问数据」勾选数据分类；平台内调试问数 |
| **企业 API Key** | 调用已发布且策略允许的问数 Agent；获得 Insight 结构化结果 |
| **终端用户** | 仅在外部产品中提问；不接触物理表名、不登录 LightBot |

### 权限一句话

```text
建设者  = 写清模型字段 + 勾选 Agent 可问数据分类 + 调试问数
API Key = 正式问数入口（经 Agent）
终端用户 = 只在外部产品里问，不见库表
```

## P5. 两类入口（产品形态）

### P5.1 控制台（建设侧）

| 入口 | 产品效果 |
| --- | --- |
| 数据中心 → 数据模型 | 写好字段中文名与说明（问数语义主来源） |
| 数据中心 → 数据模型卡片 **问数增强** | 抽屉配置：业务说明、默认时间、敏感字段、默认过滤、业务指标；打开时自动同步维度/自动指标 |
| 数据中心 → 数据池（推荐） | 当前模型内嵌 **「问一问」**（待做），默认锁定本模型 |
| Agent 详情 → **可问数据** | **勾选数据分类**即开启问数（该类下全部模型；自动 ensure 语义 + 画像）；**不绑定** PgSqlTool |
| 平台 Chat | 建设者调试完整 Insight 体验；会话个人隔离（对齐 PRODUCT） |

### P5.2 对外 API（消费侧）

| 入口 | 产品效果 |
| --- | --- |
| 企业 API · Chat | 与控制台同一套问数能力；返回/流式携带 **AskDataInsight**（或等价结构），外部门户可自绘 |
| 可选 Open · IR 直查 | 外部已有结构化意图时直查；**不替代**自然语言对话问数 |
| 数据池 Open CRUD | 仅业务数据读写；文档中明确「非问数」 |

## P6. 用户能问什么（能力清单）

| 能力 | 用户说法示例 | 产品呈现要求 |
| --- | --- | --- |
| 单指标 | 上个月活跃客户有多少？ | KPI 结论 + 时间/口径说明 |
| 分组统计 | 按区域统计订单金额 Top10 | 柱状图 + 表 |
| 对比 | 浙A 和 浙B 哪个多？ | 对比可视化 + 差值/占比 |
| 趋势 | 最近三个月活跃客户趋势 | 折线；自动选合理时间粒度 |
| 明细 | 找出车牌含浙A的活跃客户 | 表格；可导出 |
| 下钻 | 点某区域 / 「看明细」 | 在当前分析上收窄条件再出结果 |
| 追问 | 换成按月；只看浙A；加上区域 | 基于上一问连续变，而非重头选表 |
| 歧义澄清 | 「客户」对应两套数据 | 卡片选项澄清，**禁止瞎猜出数** |

## P7. 体验原则（验收时逐条对照）

1. **先结论，再图/表，最后技术细节**（计划 / SQL 默认折叠，建设排障可展开）  
2. **数字必须来自查询结果**，禁止模型凭空编造；口径不确定时先澄清或明示 assumptions  
3. **歧义必澄清**（数据集、指标、时间），用选项卡而非开放长问答  
4. **会话连续**：记住上一查询意图，支持自然语言增量修改  
5. **推荐追问**：每次结果附 2～4 条可点 Chip  
6. **过程可见**：理解问题 → 选定口径 → 查询中 → 生成结论  
7. **可带走**：表格/图可导出；API 侧可拿到结构化 Insight  
8. **可信任**：可查看「用了哪些指标/过滤/时间」；答错可标记（建设侧）  

## P8. 建设者工作流（产品路径）

```text
1. 建数据模型（字段写好中文名与描述）+ 录入数据
2. Agent「可问数据」勾选该模型所属分类 → 后台自动维度/默认指标/画像 → 即可问数
3. （可选）数据模型卡片「问数增强」抽屉补说明 / 时间 / 敏感字段 / 默认过滤 / 业务指标
4. 平台 Chat 验收；管理员发企业 API Key 对外
```

**产品要求**：零增强可问；增强抽屉不编辑维度表；业务指标用简表配置，系统自动指标只读展示。

## P9. 信息架构与文案

| 位置 | 文案 / IA |
| --- | --- |
| 数据中心 Tab | 「数据池」「数据模型」；问数增强挂在模型卡片操作上 |
| Agent 绑定 | 「可问数据」按**数据分类**勾选（非单模型），与「知识库」并列 |
| Chat 结果区 | 「分析结论」为主标题；「查询说明」「技术细节」为次级折叠 |
| 设置/文档 | 企业 API 文档区分「对话问数」与「数据池 Open CRUD」 |
| 禁止文案 | 面向终端的说明中不出现物理表名 `sjc_data_*` 作为主概念（建设者调试可显示） |

## P10. 资源与权限矩阵（问数域）

| 资源 | 归属 | 建设者 | 管理员 | 企业 API Key |
| --- | --- | --- | --- | --- |
| 数据模型 / 数据池 | 企业 | 读写 | 同左 | 不直接；经 Agent/可选 IR API |
| 问数语义（ask_dataset，自动） | 企业 | 经绑模型自动生成 | 同左 | 无控制台；间接经 Agent |
| 问数 Agent / Skill | 企业 | 读写维护 | 同左 + 治理 | 按策略**调用** |
| Agent↔Dataset 绑定 | 企业 | 可配 | 同左 | 仅能访问已绑定范围 |
| 平台问数会话 | 个人（调试） | 仅本人 | 可选审计 | — |
| API 问数会话 | 企业集成 | 只读排障 | 全权 | 创建 / 续聊 |
| 查询审计 / Trace | 企业 | 只读排障 | 全权 | 无控制台 |
| 答错标记 / 评测集 | 企业 | 可标记、可维护评测 | 同左 | 无 |

## P11. 产品明确不做

- 把 `PgSqlTool` / 自由 NL2SQL 当作智能问数主产品形态  
- 让终端用户或外部 Key「随便查全库任意表」  
- 用知识库 RAG 段落代替真实聚合数字  
- 多租户 SaaS 级数据隔离、复杂权限树（与 PRODUCT 一致）  
- 以「导出一整库给用户自己 Excel 分析」替代对话洞察（导出是补充，不是主路径）  

## P12. 产品验收标准

完成智能问数产品交付，须同时满足：

1. 建设者能为至少一个业务模型写好字段语义，并在 Agent「可问数据」勾选其所属分类。  
2. 控制台 Chat 用自然语言完成：单指标、分组 TopN、对比、趋势、明细、追问、歧义澄清——呈现符合 P7。  
3. 企业 API Key 调用同一 Agent，外部可拿到等价 Insight 结构。  
4. 未勾选的数据模型，Agent 问不到。  
5. 用户可见结论中的数字与底层查询一致；Explain 中 SQL 只读。  
6. 两建设者看到同一套数据模型与问数能力（企业共享）。  
7. 文档/UI 不引导用户用数据池 CRUD 接口充当问数。  

细项勾选见 **§C1 产品验收清单**。

---

# 二、技术方案

> 本节定义**怎么实现**；服从产品方案与 AGENTS 模块边界。

## T0. 主路径原则

```text
自然语言 → Intent IR → 可校验查询计划 → 参数化只读 SQL → AskDataInsight
```

**LLM 不生成最终执行 SQL**；只生成/修订 Intent IR。安全与正确性在引擎。

## T1. 总体架构

```text
┌─────────────────────────────────────────────────────────────┐
│ 体验层 Chat / 企业 API                                        │
│  InsightCard · Chart · Table · Drilldown · Explain · Export │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│ 问数 Runtime（Skill + Tools）                                 │
│  clarify → catalog → plan → execute → verify → present      │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│ 语义层 Semantic Layer                                         │
│  Dataset · Dimension · Metric · Synonym · Relation · Policy │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│ 查询引擎 Query Engine                                         │
│  Intent IR → Resolver → Validator → SQL Compiler → Runner   │
└────────────────────────────┬────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│ 数据池物理层（sjc_data_* + schema_json，现有 CRUD 不变）        │
└─────────────────────────────────────────────────────────────┘
```

## T2. 模块归属

| 能力 | 模块 | 说明 |
| --- | --- | --- |
| 语义层实体 / Service / DDL | `lightbot-platform` | 数据域；禁止 server 写业务 Service |
| Query Engine（IR→SQL→Result） | `lightbot-platform` | 独立 `AskData*` 或扩展 `DataPoolJdbcUtil`；标识符白名单 + 参数绑定 |
| Builtin Tools / Skill | `lightbot-tool`（或 agent tool 包 + Registrar 扫描） | 只注入 Service 接口，禁止 `service.impl.*` |
| Agent 绑定 datasets、编排、last_plan | `lightbot-agent` | 对齐 `config.knowledges`：`config.datasets` |
| HTTP | `lightbot-server` | Controller 纯透传 |
| Insight UI、分析配置、Tool 渲染 | `lightbot-ui` | `toolRegistry` 专用组件 |
| Prompt | 配置/库表模板 | **禁止**业务硬编码 Prompt |

## T3. 语义层模型（逻辑）

**Dataset**：绑定 `data_model`；说明；默认时间维度；默认过滤；敏感字段策略。  

**Dimension**：字段 key；`categorical` | `time` | `geo`；同义词；time grain；高基数标记。  

**Metric（一等公民）**：

```text
code: active_customer_count
name: 活跃客户数
expr: count_distinct(customer_id) | count(*) + 固化 filters
format: integer | currency | percent
synonyms: [活跃用户, 在网客户]
```

**Relation**：`fromDataset.field → toDataset.field`，仅白名单 Join。  

**Profile**：null 率、TopK、时间范围、样例行；供 catalog/plan，默认不对终端展示。

## T4. Intent IR

```json
{
  "dataset": "customer",
  "intent": "aggregate",
  "metrics": ["active_customer_count"],
  "dimensions": ["region"],
  "filters": [
    {"dim": "plate_no", "op": "starts_with", "value": "浙A"},
    {"dim": "create_time", "op": "in_last", "value": {"n": 1, "unit": "month"}}
  ],
  "orderBy": [{"metric": "active_customer_count", "dir": "desc"}],
  "limit": 10,
  "timeGrain": null,
  "compare": null
}
```

| intent | 用途 |
| --- | --- |
| `lookup` | 明细 / 找实体 |
| `aggregate` | 统计 / 分组 |
| `trend` | 时间序列 |
| `compare` | 对比 |
| `rank` | TopN |
| `distribute` | 分布 / 占比 |

**Filter 算子**：`eq` / `ne` / `in` / `like` / `starts_with` / `gt` / `gte` / `lt` / `lte` / `between` / `is_null` / `is_not_null` / `in_last` / `in_range`。  

自由 SQL 不是一等公民；仅 Explain 展示编译结果。

## T5. 查询引擎

```text
IR → Semantic Resolver → Plan Validator → SQL Compiler → Runner → Result Envelope
```

硬约束：只读 DataSource；`deleted=0`；LIMIT（明细默认 ≤50，聚合组 ≤500）；超时 ≤10s；敏感脱敏；审计 `plan` / `sql_hash` / `row_count` / `latency`；校验失败返回结构化错误码。

## T6. Agent Tools

| Tool | 职责 |
| --- | --- |
| `ask_data_clarify` | 澄清（可底层 `ask_user`） |
| `ask_data_search_catalog` | 检索 Dataset / Metric |
| `ask_data_plan` | 产出 IR |
| `ask_data_execute` | 执行 IR |
| `ask_data_present` | 产出 `AskDataInsight` |

编排：`catalog → plan → execute → verify → present`；后续轮次 **patch `last_query_plan`**。  
绑定：`agent.config.datasets`；问数 Agent **不得**依赖 `PgSqlTool`。

## T7. AskDataInsight 协议

```json
{
  "summary": "6 月活跃客户 1,284 人，环比 +12%。浙A 占比最高（38%）。",
  "assumptions": ["「活跃」= status=active", "时间=自然月"],
  "chart": {"type": "bar", "x": "region", "y": "active_customer_count"},
  "table": {"columns": [], "rows": [], "total": 10},
  "followups": ["按月看趋势", "只看浙A明细", "对比上月"],
  "explain": {"dataset": "客户", "metrics": [], "plan": {}, "sql": "SELECT ..."},
  "drill": {"type": "filter_patch", "fromRowField": "region"}
}
```

前端：结论条、主图、表、口径、解释、追问 Chip、下钻；大结果导出可走 `present_artifacts`。

## T8. API 面

| 接口族 | 用途 |
| --- | --- |
| 控制台语义层 CRUD、画像刷新 | 建设配置 |
| Chat + Insight | 主体验 |
| 可选 `POST /api/open/v1/ask-data/query` | IR 直查（`full` Key） |
| `/api/open/v1/data-pools/**` | CRUD，非问数主路径 |

## T9. 与现有能力

| 能力 | 角色 |
| --- | --- |
| 数据池 CRUD / Open API | 写数与集成 |
| `PgSqlTool` | 建设排障；禁止绑问数 Agent |
| 知识库 RAG | 解释制度/口径文档；不负责出数 |
| `ExecuteCodeTool` | 非问数主路径 |
| Eval | NL → 期望 IR / 数值回归 |

## T10. 实现分期

| 阶段 | 内容 | 产品阶段目标 |
| --- | --- | --- |
| **S1** | 语义存储 + ensureFromModel 自动语义 + 画像 | 绑模型即可问 |
| **S2** | IR + 引擎 + 只读 + 审计 | 查得安全可复现 |
| **S3** | Tools + Skill + datasets 绑定 + last_plan | 对话能问数 |
| **S4** | Insight UX + 追问下钻导出 | 看得懂可追问 |
| **S5** | 评测回流 + 可选 Open IR | 可运营可集成 |

**S2 完成前不得上线自由 SQL 捷径。**

---

# 三、检查清单

> 用法：完成一项将 `[ ]` 改为 `[x]`。  
> **产品清单（C1）** 证明「好不好用、达不达效果」；**技术清单（C2）** 证明「有没有按方案落地」。  
> 主路径完成 = C1 必选项全部勾选 + C2 非可选全部勾选 + C1 验收样例通过。

## C1. 产品验收清单

### C1.1 定位与入口

- [x] 控制台在数据模型卡片提供「问数增强」抽屉（无独立 Tab、无维度表）
- [x] Agent 可绑定「可问数据 / 数据分类」，与知识库绑定可区分
- [ ] 提供问数 Agent 或 Skill 模板（开箱可调试）
- [ ] （推荐）数据池/模型页「问一问」入口，默认锁定当前模型
- [ ] 企业 API 文档区分「对话问数」与「数据池 Open CRUD」

### C1.2 建设者体验

- [x] 数据模型字段中文名/说明即为问数语义来源
- [x] 勾选模型后后台自动维度 + 默认指标（cnt / sum_* / avg_*）+ 画像
- [x] 可选配置业务说明、默认时间、敏感字段、默认过滤、自定义业务指标
- [x] Relation 不进主路径（API 保留；控制台不强调）
- [x] 两建设者共享同一套模型与问数能力（企业共享）
- [x] 未勾选模型时，问数 Agent 无法访问未授权数据（产品可感知的错误/提示）

### C1.3 提问与呈现（P7）

- [x] 结果区：**结论优先**，图/表其次，技术细节默认折叠（基础 Insight 组件）
- [x] 数字来自真实查询；口径以 assumptions / 查询说明展示
- [ ] 歧义时出现选项澄清，不静默猜错表/指标
- [x] 支持推荐追问 Chip（展示；点击续聊待增强）
- [ ] 支持基于上一问的追问（换成按月 / 加过滤等）
- [ ] 支持下钻（点图或行收窄条件）
- [ ] 支持导出（表/图至少一种主路径）
- [ ] 过程状态对用户可见（理解 → 口径 → 查询 → 结论）

### C1.4 消费侧

- [ ] 企业 API Key 调用问数 Agent 可用（走现有 Chat，绑定 datasets 后应可用，待联调勾选）
- [x] API 侧可获得与控制台等价的 Insight 结构（或明确映射文档）
- [ ] 终端用户无需登录 LightBot 即可在外部产品中间接使用

### C1.5 产品验收样例

- [ ] 「上个月活跃客户有多少？」→ 单指标 + 时间口径正确
- [ ] 「按区域统计 Top10」→ 分组图 + 表
- [ ] 「浙A vs 浙B」→ 对比结果
- [ ] 「最近趋势」→ 时间序列 + 合理粒度
- [ ] 明细类问题 → 表格且可导出或可复制
- [ ] 两数据集同名歧义 → 澄清，不瞎查
- [ ] 「换成按月 / 只看浙A」→ 连续追问结果符合预期
- [ ] 用户可展开查看口径/计划；不引导使用裸库工具问数

### C1.6 产品明确不做（回归时确认未违反）

- [x] 问数主路径未变成 PgSqlTool / 自由 NL2SQL
- [x] 未把数据池 Open CRUD 宣称为问数能力
- [x] 未出现「RAG 段落当统计结果」的产品路径

---

## C2. 技术实现清单

### C2.1 语义层

- [x] Dataset 模型与持久化（绑定 `data_model`、默认时间、默认过滤、敏感字段策略）
- [x] Dimension 配置（类型、同义词、时间 grain、高基数标记）
- [x] Metric 配置（表达式、固化过滤、格式、同义词）
- [x] Relation 白名单跨模型关联
- [x] 字段画像生成与刷新（TopK / 时间范围 / 样例）
- [x] 问数语义 API + 轻量增强 API（`PUT .../enhancement`：说明/时间/敏感/默认过滤/业务指标）

### C2.2 查询引擎

- [x] Intent IR DTO + 校验（intent 枚举与 filter 算子）
- [x] Semantic Resolver（默认时间 / 指标固化过滤 / 默认过滤）
- [x] Plan Validator（字段/指标/高基数/limit）
- [x] SQL Compiler（白名单、参数绑定、`deleted=0`）
- [ ] Runner：只读 DataSource + 超时 + 行数上限（已有行数上限；只读库未单独接线）
- [x] Result Envelope（AskDataResultVO / Insight）
- [ ] 审计写入 ToolCall / Trace（依赖通用 ToolCall 日志）
- [x] 结构化校验错误码

### C2.3 Agent / Tool

- [x] `ask_data_search_catalog`
- [ ] `ask_data_clarify`（暂用模型+ask_user；未单独封装）
- [ ] `ask_data_plan`（模型直接产出 IR，由 execute 校验）
- [x] `ask_data_execute`
- [x] `ask_data_present` → `AskDataInsight`（合并进 execute 返回）
- [ ] 问数 Skill/模板 + Prompt 模板（非硬编码）
- [x] `agent.config.datasets` 绑定 API/UI
- [x] ToolPrep 按绑定注入问数工具
- [ ] 会话 `last_query_plan` 与 patch
- [x] 问数 Agent 不依赖 `PgSqlTool`

### C2.4 前端与协议

- [x] `AskDataInsight` 前后端对齐
- [x] InsightCard 全套 UI（基础版：结论/表/KPI/追问/Explain）
- [ ] 图表类型建议与切换（已有 chart 建议字段，可视化切换待增强）
- [ ] 下钻 / 追问 Chip / 导出
- [x] `toolRegistry` 注册
- [ ] （推荐）数据中心「问一问」

### C2.5 API / 安全

- [x] 语义配置控制台 API
- [ ] Chat + API Key 路径贯通（待联调）
- [ ] （可选）`POST /api/open/v1/ask-data/query`
- [x] Dataset 白名单强制生效
- [x] 敏感字段脱敏
- [ ] 只读库账号接线（`datasource-readonly`）

### C2.6 评测

- [ ] 评测集接入 Eval（NL → IR / 期望数值）
- [ ] 回归说明（改引擎必跑）
- [ ] 答错标注回流

---

# 四、冻结决策

| # | 决策 |
| --- | --- |
| D1 | 主路径 = 语义层 + Intent IR + 引擎；非 PgSql / 非自由 NL2SQL |
| D2 | Metric 一等公民，优先于裸字段聚合 |
| D3 | 结果协议 = `AskDataInsight` |
| D4 | 会话保存 `last_query_plan`，追问以 patch 为主 |
| D5 | 低置信必须 clarify |
| D6 | 评测闭环与模型字段语义同步建设 |
| D7 | 提供问数 Agent/Skill 模板；普通 Agent 按需勾选数据模型 |
| D8 | 数据池 Open CRUD 与问数引擎分离 |
| D9 | 产品方案以本文「一、产品方案」为准；与 PRODUCT.md 冲突时以 PRODUCT 企业边界为准，问数细节以本文为准 |

变更决策须改本文并记入变更记录。

---

# 五、变更记录

| 日期 | 说明 |
| --- | --- |
| 2026-07-30 | 初版：产品效果要点 + 技术方案 + 技术清单 |
| 2026-07-30 | 补全「一、产品方案」（定位/入口/体验/权限/不做/产品验收）；清单拆为 C1 产品 + C2 技术 |
| 2026-07-30 | 落地 S1–S4 骨架：`ask_dataset`/`ask_relation`、查询引擎、ask_data_* 工具、Agent 可问数据绑定、分析配置 UI、Insight 基础渲染 |
| 2026-07-31 | 简化为「模型即可问」：Agent 绑 dataModels、ensure/syncFromModel 自动语义；分析配置改为问数增强 |
| 2026-07-31 | 进一步简化：移除数据中心「问数增强」Tab；主路径仅 Agent 勾选模型；ensure 时自动刷画像 |
| 2026-07-31 | 恢复轻量问数增强：仅业务说明 / 默认时间 / 敏感字段；维度指标仍系统自动 |
| 2026-07-31 | 增强页补档：默认过滤 + 自定义业务指标（固化过滤）；维度仍不开放编辑 |
| 2026-07-31 | 问数增强改为数据模型卡片入口 + 抽屉；移除独立 Tab |
| 2026-07-31 | 默认过滤/指标固化过滤支持多算子（eq/ne/gt/like/in/between/空值等），兼容旧等值 Map |
| 2026-07-31 | 问数增强抽屉支持「测试过滤 / 测试指标」预览（`POST .../preview`，不落库） |
| 2026-07-31 | 测试结果改为弹窗展示：定高滚动、默认 6 列收敛、单元格省略 |
| 2026-07-31 | Agent「可问数据」改为以数据分类为绑定维度（`dataModelCategories`，运行时展开模型） |
