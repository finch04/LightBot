-- 内置默认Agent（id=1），用于未指定agentId时的兜底
-- 使用 ON CONFLICT 避免重复执行时报错
INSERT INTO agent (id, user_id, name, description, agent_type, system_prompt, welcome_message, status, version, create_time, update_time, deleted)
VALUES (
    1,
    1,
    'LightBot 助手',
    '默认AI助手',
    'CHAT',
    '你是 LightBot 智能助手，请用中文回答用户问题。回答应简洁准确，遇到不确定的信息请如实告知。',
    '## 你好，我是 LightBot
有什么可以帮你的？',
    'PUBLISHED',
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
)
ON CONFLICT (id) DO NOTHING;
