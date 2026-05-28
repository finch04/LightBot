-- ============================================================
-- 新增 Prompt 构建模板（5个常用场景）
-- ============================================================

INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config) VALUES
(10004, 'content_writer', '写作,内容创作', '内容创作专家模板，适用于文章、博客、营销文案等场景', '你是一位资深的内容创作专家，擅长撰写各类文体。请根据以下要求创作内容。

内容类型：{{content_type}}
目标受众：{{target_audience}}
风格要求：{{style}}
主题：{{topic}}

请直接输出内容：', 'content_type,target_audience,style,topic', '{"temperature": 0.8}'),

(10005, 'data_analyst', '数据分析', '数据分析专家模板，适用于数据解读、报表分析、趋势预测等场景', '你是一位专业的数据分析师。请根据以下数据和问题进行分析。

数据描述：{{data_description}}
分析目标：{{analysis_goal}}
数据样本：
{{data_sample}}

请提供分析结论和建议：', 'data_description,analysis_goal,data_sample', '{"temperature": 0.3}'),

(10006, 'customer_service', '客服', '智能客服模板，适用于售前咨询、售后支持、投诉处理等场景', '你是一位专业的客服代表，态度友好、耐心细致。请根据以下信息回复客户。

客服角色：{{service_role}}
客户问题：{{customer_issue}}
产品信息：{{product_info}}
回复语言：{{reply_lang}}

请给出专业回复：', 'service_role,customer_issue,product_info,reply_lang', '{"temperature": 0.5}'),

(10007, 'summarizer', '摘要,总结', '文本摘要专家模板，适用于长文摘要、会议纪要、报告精简等场景', '你是一位文本摘要专家。请对以下内容进行精准概括。

摘要类型：{{summary_type}}
摘要长度：{{summary_length}}
原文：
{{original_text}}

请输出摘要：', 'summary_type,summary_length,original_text', '{"temperature": 0.3}'),

(10008, 'email_composer', '邮件', '邮件撰写专家模板，适用于商务邮件、工作汇报、客户沟通等场景', '你是一位专业的邮件撰写助手。请根据以下信息撰写邮件。

邮件场景：{{scenario}}
收件人：{{recipient}}
邮件目的：{{purpose}}
关键要点：{{key_points}}
语气：{{tone}}

请输出完整邮件（含主题行）：', 'scenario,recipient,purpose,key_points,tone', '{"temperature": 0.6}');
