-- ============================================================
-- 提示词评测功能：Prompt管理 + 评测系统（11张表 + 预制数据）
-- ============================================================

-- ==================== Prompt 管理 ====================

-- Prompt 定义表
CREATE TABLE prompt (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    latest_version  VARCHAR(32),
    tags            VARCHAR(512),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_key ON prompt (prompt_key) WHERE deleted = 0;
CREATE INDEX idx_prompt_user_id ON prompt (user_id);
COMMENT ON TABLE prompt IS 'Prompt定义表';

-- Prompt 版本表
CREATE TABLE prompt_version (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    version_desc    VARCHAR(512),
    template        TEXT            NOT NULL,
    variables       JSONB           DEFAULT '{}',
    model_config    JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pre',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_version ON prompt_version (prompt_key, version) WHERE deleted = 0;
CREATE INDEX idx_prompt_version_key ON prompt_version (prompt_key);
COMMENT ON TABLE prompt_version IS 'Prompt版本表';

-- Prompt 构建模板表
CREATE TABLE prompt_build_template (
    id                      BIGINT          NOT NULL,
    prompt_template_key     VARCHAR(128)    NOT NULL,
    tags                    VARCHAR(256),
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_build_template_key ON prompt_build_template (prompt_template_key) WHERE deleted = 0;
COMMENT ON TABLE prompt_build_template IS 'Prompt构建模板表';

-- ==================== 评测系统 ====================

-- 评测集表
CREATE TABLE eval_dataset (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    columns_config  JSONB           DEFAULT '[]',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_user_id ON eval_dataset (user_id);
COMMENT ON TABLE eval_dataset IS '评测集表';

-- 评测集版本表
CREATE TABLE eval_dataset_version (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    data_count      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    dataset_items   JSONB           DEFAULT '[]',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_dataset_version ON eval_dataset_version (dataset_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_dataset_version_dataset_id ON eval_dataset_version (dataset_id);
COMMENT ON TABLE eval_dataset_version IS '评测集版本表';

-- 评测数据项表
CREATE TABLE eval_dataset_item (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    data_content    JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_item_dataset_id ON eval_dataset_item (dataset_id);
COMMENT ON TABLE eval_dataset_item IS '评测数据项表';

-- 评估器表
CREATE TABLE eval_evaluator (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_evaluator_user_id ON eval_evaluator (user_id);
COMMENT ON TABLE eval_evaluator IS '评估器表';

-- 评估器版本表
CREATE TABLE eval_evaluator_version (
    id              BIGINT          NOT NULL,
    evaluator_id    BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    model_config    JSONB           DEFAULT '{}',
    prompt          TEXT,
    variables       JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_version ON eval_evaluator_version (evaluator_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_evaluator_version_evaluator_id ON eval_evaluator_version (evaluator_id);
COMMENT ON TABLE eval_evaluator_version IS '评估器版本表';

-- 评估器模板表
CREATE TABLE eval_evaluator_template (
    id                      BIGINT          NOT NULL,
    evaluator_template_key  VARCHAR(128)    NOT NULL,
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_template_key ON eval_evaluator_template (evaluator_template_key) WHERE deleted = 0;
COMMENT ON TABLE eval_evaluator_template IS '评估器模板表';

-- 实验表
CREATE TABLE eval_experiment (
    id                          BIGINT          NOT NULL,
    name                        VARCHAR(128)    NOT NULL,
    description                 VARCHAR(512),
    dataset_id                  BIGINT,
    dataset_version_id          BIGINT,
    dataset_version             VARCHAR(32),
    evaluation_object_config    JSONB           DEFAULT '{}',
    evaluator_config            JSONB           DEFAULT '[]',
    status                      VARCHAR(20)     NOT NULL DEFAULT 'draft',
    progress                    INT             NOT NULL DEFAULT 0,
    complete_time               TIMESTAMP,
    user_id                     BIGINT,
    task_id                     BIGINT,
    create_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                     SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_user_id ON eval_experiment (user_id);
CREATE INDEX idx_eval_experiment_status ON eval_experiment (status);
COMMENT ON TABLE eval_experiment IS '评测实验表';

-- 实验结果表
CREATE TABLE eval_experiment_result (
    id                      BIGINT          NOT NULL,
    experiment_id           BIGINT          NOT NULL,
    input                   TEXT,
    actual_output           TEXT,
    reference_output        TEXT,
    score                   DECIMAL(3,2),
    reason                  TEXT,
    evaluator_version_id    BIGINT,
    evaluator_name          VARCHAR(128),
    evaluation_time         TIMESTAMP,
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_result_experiment_id ON eval_experiment_result (experiment_id);
CREATE INDEX idx_eval_experiment_result_evaluator ON eval_experiment_result (evaluator_version_id);
COMMENT ON TABLE eval_experiment_result IS '实验结果表';

-- ==================== 预制数据 ====================

-- 评估器模板（3个）
INSERT INTO eval_evaluator_template (id, evaluator_template_key, template_desc, template, variables, model_config) VALUES
(10001, 'text_similarity', '文本相似度评估', '请评估以下两个文本的相似度，分数范围为0-1，保留两位小数。

文本1：{{reference_output}}

文本2：{{actual_output}}

相似度分数：', 'reference_output,actual_output', '{"temperature": 0.1}'),
(10002, 'code_quality', '代码质量评估', '请评估以下代码的质量，从可读性、效率和最佳实践三个方面进行分析，并给出0-1的总分，保留两位小数。

代码：
{{code}}

评估报告：', 'code', '{"temperature": 0.2}'),
(10003, 'sentiment_analysis', '情感分析评估', '请分析以下文本的情感倾向，输出-1到1之间的情感分数，其中-1表示非常负面，0表示中性，1表示非常正面，保留两位小数。

文本：{{text}}

情感分数：', 'text', '{"temperature": 0.1}');

-- Prompt 构建模板（3个）
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config) VALUES
(10001, 'general_assistant', '通用,助手', '通用AI助手模板', '你是一个专业的AI助手。请根据用户的问题提供准确、有帮助的回答。

角色：{{role}}
任务：{{task}}

用户输入：{{user_input}}', 'role,task,user_input', '{"temperature": 0.7}'),
(10002, 'code_reviewer', '代码,审查', '代码审查模板', '你是一个资深的代码审查专家。请对以下代码进行审查，指出问题并给出改进建议。

审查语言：{{language}}
审查重点：{{focus}}

代码：
{{code}}', 'language,focus,code', '{"temperature": 0.3}'),
(10003, 'translator', '翻译', '翻译专家模板', '你是一个专业的翻译专家，精通多种语言。请将以下文本翻译成目标语言。

源语言：{{source_lang}}
目标语言：{{target_lang}}

原文：{{text}}', 'source_lang,target_lang,text', '{"temperature": 0.3}');
