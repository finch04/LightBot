-- 移除 workflow_test_run.confirm_form：人工确认信息已包含在 node_events 中（workflow_confirm_required 等事件）
ALTER TABLE workflow_test_run DROP COLUMN IF EXISTS confirm_form;
