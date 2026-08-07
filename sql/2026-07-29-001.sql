-- 品牌文案：用户可见的 LightBot → 智元（已有环境升级）

-- 1. 默认 Agent 名称 / 提示词 / 欢迎语
UPDATE agent
SET name = '智元助手',
    system_prompt = '你是智元智能助手，请用中文回答用户问题。回答应简洁准确，遇到不确定的信息请如实告知。',
    welcome_message = E'## 你好，我是智元\n有什么可以帮你的？',
    update_time = CURRENT_TIMESTAMP
WHERE id = 1
  AND deleted = 0
  AND (name LIKE '%LightBot%' OR system_prompt LIKE '%LightBot%' OR welcome_message LIKE '%LightBot%');

-- 2. Landing 配置 JSON 中的品牌字符串
UPDATE system_config
SET config_value = replace(config_value, 'LightBot', '智元')
WHERE config_key = 'landing_config'
  AND config_value LIKE '%LightBot%';
