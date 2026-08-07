package com.lightbot.skill;

import java.util.List;

/**
 * 内置 Skill 定义清单
 * <p>对标 Yuxi 项目中的 SKILL.md：每个 Skill 是一条「编排指令」，
 * 由 prompt_template 指导主 Agent 何时、如何使用所依赖的 Tool。</p>
 *
 * <p>新增内置 Skill 时仅需在 {@link #list()} 中追加一条 Definition，
 * {@link BuiltInSkillRegistrar} 启动时会按 content_hash 同步到数据库。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
public final class BuiltInSkillDefinitions {

    private BuiltInSkillDefinitions() {}

    public record Definition(
            String slug,
            String name,
            String displayName,
            String icon,
            String description,
            List<String> toolNames,
            List<String> skillDependencies,
            String promptTemplate,
            int sortOrder
    ) {}

    private static final List<Definition> DEFS = List.of(
            new Definition(
                    "deep-research",
                    "deep-research",
                    "深度研究",
                    "ExperimentOutlined",
                    "对话题进行多轮联网检索、任务拆解与结构化整理，适合调研报告、行业分析等需要事实证据的问题。",
                    List.of("web_search", "ask_user", "ocr_parse_file", "sandbox_read_file", "sandbox_write_file", "sandbox_append_file"),
                    List.of(),
                    """
                    ### 技能：深度研究（deep-research）
                    当任务目标是产出**多来源、可追溯、经过核验**的深度研究结论（科研综述、行业/竞品调研、技术选型、专题分析等）时，使用此技能组织整个研究过程。本技能的核心是**编排**：你负责整体把控与子智能体调度，把繁重的检索与章节产出工作派发出去，自己专注规划、装配与最终文件交付。

                    ## 可用工具与子智能体
                    - `delegate_to_subagent`：并行派发子任务的委派工具。仅在当前 Agent 已绑定子智能体时可用；一次调用可传多个 `tasks`，`mode="parallel"` 并行。**优先派发给这两类子智能体**（若已绑定）：
                      - `research-agent`（深度研究员）：围绕单个明确子问题做多轮联网/知识库检索，**直接返回章节级 markdown 片段**（含小标题、段落、句末引用），供主 Agent 原样装配到最终报告。**这是主力，按章节并行多开。**
                      - `fact-verifier`（事实核验员）：对关键论断做对抗式核验，逐条给出 支持 / 存疑 / 反驳 + 证据 + 置信度。
                    - `ask_user`：以 `questions` 数组一次提 1~3 个彼此独立的澄清问题；调用后必须等待用户回答，不再调其它工具。
                    - `write_todos`：主 Agent 自动注入，只记录完整待办快照，不占工具配额。
                    - `web_search(query, maxResults)`：主 Agent 自检索兜底；应尽量派发给 `research-agent`，仅在澄清范围或补一两个零散事实时才自查。
                    - `query_knowledge`：Agent 已绑定知识库时由系统注入，检索绑定库。
                    - `ocr_parse_file` + `sandbox_read_file`：本轮上传 PDF/图片时先 OCR，再按需读取，不臆造附件内容。
                    - `sandbox_write_file` / `sandbox_append_file` + `present_artifacts`（自动注入）：**默认走文件交付**——主 Agent 将最终报告写入 `outputs/reports/{topic}-{yyyyMMdd}.md`（topic 为研究主题英文 slug + 日期，避免覆盖历史报告），先用 `sandbox_write_file` 写头部与引言，再按章节 `sandbox_append_file` 追加，最后 `present_artifacts` 登记交付给用户。
                    - **禁止**调用 Yuxi 的 `task`、`ask_user_question`、`tavily_search`、`query_kb`、`read_file`、`write_file` 等旧名，或猜测任何未出现在当前工具列表中的名称。

                    ## 编排流程
                    ### 1. 澄清范围
                    问题不明确时，先用 `ask_user` 补充 2~3 个关键问题（研究目标、受众、范围边界、地域/时效、输出语言与形式），对齐验收标准后再开工。已经清晰的任务不要反复追问，采用的默认范围在最终报告开头一句话说明。

                    ### 2. 规划拆解（章节级 todo）
                    用 `write_todos` 把研究目标拆成**与报告章节一一对应**的子问题（2~8 项完整快照），每个子问题即未来报告的一个章节，写明：章节标题、要回答的子问题、需要的证据类型、预期字数下限。子问题应正交、覆盖完整，避免重叠或遗漏关键角度。同一时刻仅一个主待办为 `in_progress`。

                    ### 3. 并行派发章节调研
                    - 把互不依赖的章节在**一次 `delegate_to_subagent`** 调用中通过 `tasks` 并行派发给 `research-agent`，`mode="parallel"`。
                    - 每个 task 写清：章节标题、子问题目标、已知上下文、**期望产出格式（完整 markdown 章节：`## 章节标题` + 段落 + 句末 `(参考：xxx)` 引用 + 字数 ≥ 300）**。
                    - 何时派发 vs 自己直检：子问题复杂、需多轮检索、可隔离上下文、可并行时一律派发；仅在澄清范围、补一两个零散事实或快速校正方向时才自己少量 `web_search`（单次 maxResults ≤ 5，同一意图最多改写一次关键词）。
                    - 子问题之间有依赖时，先派发前置子问题，拿到结果后再派发后续。
                    - 已绑定 `query_knowledge` 时，先检索绑定知识库，再按需补充公开信息。

                    ### 4. 核验关键结论
                    对**影响最终结论的关键论断**、数字，以及子智能体之间相互冲突的发现，派发 `fact-verifier` 做对抗式核验。默认「证据不足即标注存疑」。核验未通过的结论不要写进正文，或必须明确降级标注；核验结论作为报告附录章节「证据核验记录」呈现。

                    ### 5. 装配文件产物（默认交付）
                    证据充分后，由你统一装配为完整 markdown 报告文件：
                    1. `sandbox_write_file` 写头部与引言：报告标题（`# 主题`）、研究范围一句话说明、目录、引言（问题定义 + 研究方法概述）。
                    2. 按章节 `sandbox_append_file` 追加：每个 research-agent 返回的章节 markdown **原样 append**；不重写章节正文，只在章节之间补一句过渡引导（如「以下从 X 角度展开」）。
                    3. `sandbox_append_file` 追加尾部章节：综合各章的「结论与建议」（这部分由你撰写，不是拼接）、完整「来源」清单（标题 + URL，去重）、可选「证据核验记录」附录。
                    4. `present_artifacts` 登记最终文件，交付给用户下载。

                    ### 6. 停止准则
                    信息饱和、或确认无法获取更多有效信息即停。明确标注证据缺口与不确定性，不臆断、不编造来源。

                    ## 引用规范
                    - 报告中关键结论、数据、观点必须绑定来源，句末标注「（参考：站点名或 URL）」。
                    - 文末单列「来源」章节，逐条列出标题与 URL；引用用户附件/知识库时标明文件名或路径。

                    ## 输出约束
                    - 最终**默认交付一份完整 markdown 文件**（而非「我打算怎么研究」），用户可直接下载使用。
                    - 会话回复中**简述交付**：报告主题、章节构成、1-3 条关键结论、文件路径；**不要把整份报告粘贴到对话**。
                    - 不外泄中间推理过程、原始检索日志，也不要把待办清单原样输出成正文。
                    - 报告语言与用户提问语言一致，使用正式、克制、可复核的书面表达。
                    """,
                    10
            ),
            new Definition(
                    "knowledge-grounded-qa",
                    "knowledge-grounded-qa",
                    "知识库严谨问答",
                    "BookOutlined",
                    "强制基于已绑定的知识库回答问题，适合企业内部文档、产品手册等场景，杜绝凭空发挥。",
                    List.of("query_knowledge", "list_knowledge_bases", "search_documents", "open_kb_document", "find_in_document", "get_mindmap"),
                    List.of(),
                    """
                    ### 技能：知识库严谨问答（knowledge-grounded-qa）
                    当用户要求基于当前 Agent 已绑定的知识库、内部资料、上传入库文档或知识图谱相关内容回答问题时，使用此技能。

                    ## 可用工具（仅调用当前实际存在于工具列表中的）
                    - `list_knowledge_bases`：列出当前会话可访问且已启用的知识库。
                    - `query_knowledge`：在绑定知识库中按问题检索内容，返回 `document_id` 和相关片段。
                    - `open_kb_document`：按 `documentId` 打开文档原文窗口，适合查看更完整上下文。
                    - `find_in_document`：在已知文档内用关键词或正则定位段落。
                    - `search_documents`：按文件名关键词搜索知识库中的文件，支持指定知识库或跨库，返回文件列表与分页信息。
                    - `get_mindmap`：查看知识库思维导图结构，仅在用户关心目录或知识框架时使用，不作为事实证据。
                    - **禁止**调用 Yuxi 的 `list_kbs`、`query_kb`、`find_kb_document`、`search_file`、`open_document` 等旧名。

                    ## 操作流程
                    1. 需要先确认当前会话有哪些知识库可用；不确定时调用 `list_knowledge_bases`，只用返回的 ID 继续调用其它工具，不能猜测 ID。
                    2. 针对用户问题选择最相关的知识库，使用 `query_knowledge` 检索；保留对象、动作、版本或时间范围。
                    3. 如果检索片段不足以支撑流程、条款或关键数字，使用返回的 `document_id` 调用 `open_kb_document` 补足原文上下文。
                    4. 用户点名某个文件而向量检索未命中时，先用 `search_documents(keyword)`，再用返回的文档 ID 打开原文。
                    5. 如果用户要求定位术语、指标、章节或原文证据，使用 `find_in_document` 在候选文档内查找。
                    6. 当用户关心知识库结构、文件分类或知识框架时，使用 `get_mindmap`。

                    ## 关键约束
                    - 只能访问当前会话配置和用户权限允许的知识库；不要编造 `document_id` 或知识库 ID。
                    - 回答只概括工具返回的内容，每项关键事实末尾标注「（参考：文档名）」。
                    - 检索无结果时明确说「绑定知识库未找到依据」，可给出带明确边界的通用建议，但不得伪装为知识库结论。
                    - 不大段复制原文，不把任何绑定库之外的资料当作内部规范。
                    """,
                    20
            ),
            new Definition(
                    "calculator-precise",
                    "calculator-precise",
                    "精确数值计算",
                    "CalculatorOutlined",
                    "遇到加减乘除等数值运算时强制调用计算器工具，避免大模型口算偏差。",
                    List.of("calculator"),
                    List.of(),
                    """
                    ### 技能：精确数值计算（calculator-precise）
                    **触发条件**：用户明确要求加、减、乘、除、比例、金额或单位换算的精确计算时使用；纯事实问答、估算讨论或没有运算的数字描述不调用。

                    **智元工具约束**：`calculator` 一次只接受 `a`、`b` 和 `operation`，其中 `operation` 只能是 `add`、`subtract`、`multiply`、`divide`。

                    **执行规则**：
                    1. 先识别单位和计算口径；百分比先转换为小数（如 15% 为 0.15），再用 `calculator` 运算。单位不能相加时先说明无法直接计算。
                    2. 多步表达式必须按依赖顺序多次调用，并将上一步实际 `result` 用作下一步输入；不可臆算中间值。
                    3. 除法前确保除数不为 0；工具报错时说明失败原因，不用模型口算替代。不要把工具不支持的开方、幂、三角函数伪装为已精确计算。
                    4. 最终先给结果及单位，再用简洁公式说明输入与中间值；没有明确的舍入规则时保留工具返回精度，不制造伪精度。
                    """,
                    30
            ),
            new Definition(
                    "image-create",
                    "image-create",
                    "图片创作",
                    "PictureOutlined",
                    "面向需要生成插画、海报、示意图等场景，基于明确的画面要求调用智元图像生成工具。",
                    List.of("image_generation", "ask_user"),
                    List.of(),
                    """
                    ### 技能：图片创作（image-create）
                    当用户要求生成图片、海报、插画、示意图、封面或创意图像时使用此技能。

                    ## 可用工具
                    - `image_generation(prompt, negativePrompt?)`：智元内置文生图工具，底层已封装 SiliconFlow / Qwen-Image，会把生成图片保存到本会话输出区并返回真实 `image_url`。不支持参考图编辑、尺寸选择或批量生成。
                    - `ask_user`：以 `questions` 一次提出 1~3 个独立澄清问题；调用后必须等待用户回答。
                    - **禁止**：调用或提及 Yuxi 沙盒 Python 脚本、`SILICONFLOW_API_KEY`、`present_artifacts`（图片工具已自动完成产物登记）、临时外部 URL 等旧流程；也不要猜测未在工具列表中出现的名称。

                    ## 操作流程
                    1. 明确用户要生成的图片主体、用途、风格、必须出现/避免的元素。信息不足但不影响生成时使用合理默认值并简短说明，不为可推断细节反复追问；若缺失会实质影响成图，才调用一次 `ask_user` 提 1~3 个独立问题。
                    2. 将确认后的意图整理成简洁具体的 `prompt`（建议英文效果更佳，覆盖主体、环境、构图、风格、光线/材质）；只在确有必要时填写 `negativePrompt`，不要加入与需求冲突的通用负面词。
                    3. 调用 `image_generation`，直接使用返回的 `image_url` 呈现图片给用户。工具会自动保存到会话输出区，不需要也不能再调用 `present_artifacts` 或编造临时 URL。
                    4. 若用户要求编辑既有图片、精确尺寸、透明背景或多张变体，先明确当前工具能力边界，让用户改为新的文生图描述或使用已绑定的其他工具；不假装完成不支持的操作。

                    ## 关键约束
                    - 未调用工具前不得声称图片已生成；不杜撰图片链接或模型参数。
                    - 不在报文中输出 `SILICONFLOW_API_KEY` 或其它敏感环境变量的值。
                    - 最终回复简要说明图片已生成并附 `image_url`；不要把外部临时 URL 当作最终结果展示。
                    """,
                    40
            ),
            new Definition(
                    "db-introspect",
                    "db-introspect",
                    "数据库探查与报表",
                    "DatabaseOutlined",
                    "面向开发/数据分析场景，安全地查询 PostgreSQL 元信息、执行只读查询并生成可视化报表，禁止任何写操作。",
                    List.of("pg_list_tables", "pg_describe_table", "pg_query"),
                    List.of(),
                    """
                    ### 技能：数据库探查与报表（db-introspect）
                    根据用户的指令，通过内置只读工具访问智元的 PostgreSQL 业务库，并结合图表工具（若已绑定）构建 SQL 查询报告。

                    ## 可用工具（仅调用当前实际存在于工具列表中的）
                    - `pg_list_tables`：列出可访问的业务表。
                    - `pg_describe_table(tableName)`：查看指定表结构、列类型与注释。
                    - `pg_query(sql)`：执行只读 `SELECT` / `SHOW` / `EXPLAIN` / `WITH` 查询。
                    - **禁止**：调用 Yuxi 的 `scripts/list_tables.py`、`scripts/describe_table.py`、`scripts/query.py`、`terminal` 或 MySQL 相关工具；不要读取或猜测 `MYSQL_HOST/USER/PASSWORD/DATABASE` 等环境变量。

                    ## 操作流程
                    1. 理解用户的指令，明确报表/探查的需求和目标。
                    2. 表或字段未知时，先调用 `pg_list_tables`，再仅对目标表调用 `pg_describe_table(tableName)`；只使用工具实际返回的表名与列名。
                    3. 依据已确认的 schema 组装只读 `pg_query`：选择需要的列、写明筛选条件，对汇总或样本查询说明统计口径。
                    4. 图表：仅当当前 Agent 已实际绑定并暴露对应 MCP 图表工具时才可调用其真实工具名，将图表以 `![描述](图片URL)` markdown 格式嵌入报表；若未绑定，直接以 Markdown 表格呈现结果并可建议用户绑定图表 MCP，不臆造图片链接。
                    5. 将结果用简洁自然语言 + Markdown 表格解释，标明行数、是否截断及其局限。

                    ## 安全边界
                    - `pg_query` 只允许只读 `SELECT`、`SHOW`、`EXPLAIN` 或 `WITH` 查询；严禁 INSERT、UPDATE、DELETE、DDL、事务控制、系统 schema、危险函数和绕过限制的注释。
                    - 每条数据查询显式写 `LIMIT`，且不超过 50；避免全表扫描；先选择需要的列，避免 `SELECT *` 和大字段全量回显。
                    - 不回显连接串、账号、密钥、令牌或不必要的个人敏感数据；遇到这些字段应聚合、脱敏或拒绝展示。
                    - 只返回报表相关的结论与关键中间值，不要把冗长 SQL 或原始敏感值直接抛给用户。
                    """,
                    50
            )
    );

    public static List<Definition> list() {
        return DEFS;
    }
}
