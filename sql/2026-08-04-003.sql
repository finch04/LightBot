-- 为历史业务页补全 formSchema（此前内置页无 schema，控制台无法渲染通用表单）

UPDATE business_page
SET form_schema = '{"fields":[{"key":"phone","label":"手机号码","type":"text","required":true},{"key":"amount","label":"充值金额（元）","type":"number","required":true},{"key":"carrier","label":"运营商","type":"text","required":false}]}'::jsonb,
    allowed_prop_keys = '["phone","amount","carrier","suggestedAmounts","minAmount","maxAmount"]'::jsonb,
    allowed_option_keys = '["primaryButtonText","cancelButtonText","hint","showInvoice"]'::jsonb,
    default_props = '{"suggestedAmounts":[50,100,200],"minAmount":10,"maxAmount":500}'::jsonb,
    update_time = CURRENT_TIMESTAMP
WHERE page_type = 'phone_recharge'
  AND deleted = 0
  AND (form_schema IS NULL OR form_schema::text IN ('null', '{}', '{"fields":[]}'));

UPDATE business_page
SET form_schema = '{"fields":[{"key":"accountNo","label":"户号","type":"text","required":true},{"key":"billType","label":"费用类型","type":"select","required":true,"options":[{"label":"电费","value":"electricity"},{"label":"水费","value":"water"},{"label":"燃气","value":"gas"}]},{"key":"amount","label":"缴费金额（元）","type":"number","required":true},{"key":"address","label":"地址","type":"text","required":false}]}'::jsonb,
    allowed_prop_keys = '["accountNo","billType","amount","address","suggestedAmounts"]'::jsonb,
    allowed_option_keys = '["primaryButtonText","cancelButtonText","hint"]'::jsonb,
    default_props = '{"billType":"electricity","suggestedAmounts":[100,200,500]}'::jsonb,
    update_time = CURRENT_TIMESTAMP
WHERE page_type = 'utility_bill_pay'
  AND deleted = 0
  AND (form_schema IS NULL OR form_schema::text IN ('null', '{}', '{"fields":[]}'));
