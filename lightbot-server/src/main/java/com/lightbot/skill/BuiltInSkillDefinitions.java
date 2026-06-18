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
            String description,
            List<String> toolNames,
            List<String> skillDependencies,
            String promptTemplate,
            int sortOrder
    ) {}

    private static final List<Definition> DEFS = List.of(
            new Definition(
                    "deep-research",
                    "deep_research",
                    "深度研究",
                    "对话题进行多轮联网检索 + 结构化整理，适合调研报告、行业分析等需要事实证据的问题。",
                    List.of("web_search"),
                    List.of(),
                    """
                    ### 技能：深度研究（deep_research）
                    **触发条件**：用户问题需要外部最新信息、行业调研、对比分析、事实核查时启用本技能。

                    **执行流程**：
                    1. 先拆分用户问题为 2~4 个检索子问题（例如：背景、关键玩家、最新进展、风险/争议）。
                    2. 针对每个子问题调用 `web_search` 工具，单次 maxResults 控制在 5 以内。
                    3. 综合检索结果，提炼关键事实与数据；标注信息出处（站点名即可）。
                    4. 输出格式：先 1~2 句总结，再分小节列要点；结尾给出 1 条「可继续追问的方向」。

                    **禁止**：未调用工具就给出含具体数据/时间/链接的结论；任何来自检索的事实必须有出处。
                    """,
                    10
            ),
            new Definition(
                    "knowledge-grounded-qa",
                    "knowledge_grounded_qa",
                    "知识库严谨问答",
                    "强制基于已绑定的知识库回答问题，适合企业内部文档、产品手册等场景，杜绝凭空发挥。",
                    List.of("query_knowledge"),
                    List.of(),
                    """
                    ### 技能：知识库严谨问答（knowledge_grounded_qa）
                    **触发条件**：用户问题涉及绑定知识库可能覆盖的内容（产品/制度/规范/操作手册等）时启用本技能。

                    **执行流程**：
                    1. 先调用 `query_knowledge` 检索与问题最相关的片段（topK ≤ 5）。
                    2. 若检索命中：基于命中片段「概括总结」后回答，不得大段照搬，结尾以「参考：文档名」简注来源。
                    3. 若检索未命中：明确告知用户「知识库中未找到相关内容」，再基于自身常识给出**保守**回答，并提示用户可补充资料。
                    4. 涉及关键数字、流程、条款，必须以知识库内容为准，不允许自行编造。
                    """,
                    20
            ),
            new Definition(
                    "calculator-precise",
                    "calculator_precise",
                    "精确数值计算",
                    "遇到加减乘除等数值运算时强制调用计算器工具，避免大模型口算偏差。",
                    List.of("calculator"),
                    List.of(),
                    """
                    ### 技能：精确数值计算（calculator_precise）
                    **触发条件**：用户问题包含数学运算（含金额、单位换算、比例、增长率等）时启用本技能。

                    **执行规则**：
                    1. 任何两位以上的数值运算必须调用 `calculator` 工具，禁止直接给结果。
                    2. 多步运算应拆分为多次调用，每一步说明含义，最终汇总结论。
                    3. 输出格式：先给最终数值结果（必要时带单位），再用列表列出计算步骤与中间值。
                    """,
                    30
            ),
            new Definition(
                    "image-create",
                    "image_create",
                    "图片创作",
                    "面向需要生成插画、海报、示意图等场景，给出符合大模型审美的英文提示词并调用图像生成工具。",
                    List.of("image_generation"),
                    List.of(),
                    """
                    ### 技能：图片创作（image_create）
                    **触发条件**：用户明确要求「画 / 生成 / 设计 一张图、海报、插画、示意图」等图像产物时启用本技能。

                    **执行流程**：
                    1. 先用 1~2 句中文复述用户意图，与用户对齐风格、主体、构图。
                    2. 将意图转为英文提示词（主体 + 场景 + 风格 + 画质修饰词），调用 `image_generation` 工具。
                    3. 可一次提供 1 个负面提示（如「blurry, low quality, text, watermark」）。
                    4. 输出：附上生成图片，并解释你选择了哪些风格关键词，方便用户继续调优。
                    """,
                    40
            ),
            new Definition(
                    "db-introspect",
                    "db_introspect",
                    "数据库探查",
                    "面向开发/数据分析场景，安全地查询 PostgreSQL 元信息与样本数据，禁止任何写操作。",
                    List.of("pg_list_tables", "pg_describe_table", "pg_query"),
                    List.of(),
                    """
                    ### 技能：数据库探查（db_introspect）
                    **触发条件**：用户问题需要查询数据库结构或样例数据时启用本技能。

                    **安全约束**：
                    - 仅允许 SELECT 查询，禁止任何 INSERT / UPDATE / DELETE / DROP 等写操作。
                    - 单次查询必须显式带 LIMIT（默认 LIMIT 20）。

                    **执行流程**：
                    1. 不清楚表结构时，先 `pg_list_tables` 查看表清单。
                    2. 再用 `pg_describe_table` 查看目标表字段含义。
                    3. 最后 `pg_query` 拉取样例，结合用户问题给出结论。
                    4. 输出时务必说明所查询的字段含义与样本数量，避免误导。
                    """,
                    50
            )
    );

    public static List<Definition> list() {
        return DEFS;
    }
}
