-- 业务页默认对话内嵌：历史注册页去掉 drawer，避免模型误选侧栏

UPDATE business_page
SET allowed_modes = '["inline"]'::jsonb,
    update_time = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND (
    allowed_modes IS NULL
    OR allowed_modes @> '["drawer"]'::jsonb
  );
