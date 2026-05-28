-- 2026-05-28-003: 插入预置Prompt构建模板（参考spring-ai-alibaba-admin项目）

-- 1. 对话式AI模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10004, 'conversational_ai', 'chat,dialogue', '对话式AI模板',
        '你是一个{{role}}，具有以下特点：
{{personality}}

在与用户对话时，请遵循以下原则：
1. {{principle_1}}
2. {{principle_2}}
3. {{principle_3}}

用户：{{user_input}}

请回复：',
        'role,personality,principle_1,principle_2,principle_3,user_input',
        '{"temperature": 0.7, "maxTokens": 2000}',
        '{}');

-- 2. 社交媒体推销文案模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10005, 'social_media_promotion', 'social,promotion', '社交媒体推销文案生成模板',
        '你是一个擅长撰写社交媒体文案的 AI 助手，请根据提供的产品信息生成一条适合发布在{{platform}}平台的推广文案。

要求：
1. 使用轻松、亲切的口吻，像朋友分享好物；
2. 结尾添加相关话题标签，如 #好物推荐；

产品信息：
{{product_info}}',
        'platform,product_info',
        '{"temperature": 0.8, "maxTokens": 500}',
        '{}');

-- 3. 商品推广文案模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10006, 'product_promotion', 'goods,promotion', '商品推广Prompt模板',
        '请为以下商品写一段推广文案：

商品名称：{{product_name}}
商品特点：{{features}}
目标人群：{{target_audience}}

要求：
1. 突出商品卖点
2. 语言简洁有力
3. 吸引目标人群购买',
        'product_name,features,target_audience',
        '{"temperature": 0.7, "maxTokens": 300}',
        '{}');

-- 4. 任务执行模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10007, 'task_executor', 'task,execution', '任务执行模板',
        '你是一个专业的{{domain}}专家，请完成以下任务：

## 任务描述
{{task_description}}

## 输入信息
{{input_data}}

## 输出要求
{{output_requirements}}

## 约束条件
{{constraints}}

请按要求完成任务：',
        'domain,task_description,input_data,output_requirements,constraints',
        '{"temperature": 0.3, "maxTokens": 3000}',
        '{}');

-- 5. 分析报告模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10008, 'analysis_report', 'analysis,report', '分析报告模板',
        '请对以下{{analysis_subject}}进行深入分析：

## 分析对象
{{subject_details}}

## 分析维度
{{analysis_dimensions}}

## 参考标准
{{reference_standards}}

## 报告结构
1. 摘要
2. 详细分析
3. 关键发现
4. 结论和建议

请生成完整的分析报告：',
        'analysis_subject,subject_details,analysis_dimensions,reference_standards',
        '{"temperature": 0.4, "maxTokens": 4000}',
        '{}');

-- 6. 创意生成模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10009, 'creative_generator', 'creative,generation', '创意生成模板',
        '请为{{project_type}}项目生成创意方案：

## 项目背景
{{background}}

## 目标群体
{{target_audience}}

## 核心需求
{{core_requirements}}

## 创意约束
{{creative_constraints}}

## 输出要求
- 提供3-5个不同的创意方向
- 每个方向包含核心概念和执行要点
- 评估可行性和预期效果

请开始生成创意：',
        'project_type,background,target_audience,core_requirements,creative_constraints',
        '{"temperature": 0.9, "maxTokens": 3000}',
        '{}');

-- 7. 问题解决模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10010, 'problem_solver', 'problem,solution', '问题解决模板',
        '作为{{expert_role}}，请帮助解决以下问题：

## 问题描述
{{problem_description}}

## 现状分析
{{current_situation}}

## 已尝试方案
{{attempted_solutions}}

## 限制条件
{{limitations}}

## 解决方案要求
1. 分析问题根因
2. 提供多个可选方案
3. 评估方案的可行性和风险
4. 推荐最优方案和实施步骤

请提供解决方案：',
        'expert_role,problem_description,current_situation,attempted_solutions,limitations',
        '{"temperature": 0.5, "maxTokens": 3500}',
        '{}');

-- 8. 教学辅导模板
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config)
VALUES (10011, 'teaching_assistant', 'education,teaching', '教学辅导模板',
        '你是一位经验丰富的{{subject}}老师，请为学生提供学习指导：

## 学生信息
- 学习水平：{{student_level}}
- 学习目标：{{learning_goal}}

## 教学内容
{{teaching_content}}

## 学生问题
{{student_question}}

## 教学要求
1. 用简单易懂的语言解释
2. 提供具体的例子
3. 给出练习建议
4. 鼓励学生思考

请开始教学：',
        'subject,student_level,learning_goal,teaching_content,student_question',
        '{"temperature": 0.6, "maxTokens": 2500}',
        '{}');