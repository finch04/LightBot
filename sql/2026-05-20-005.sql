-- 统一 model_provider.type 为大写
UPDATE model_provider SET type = UPPER(type) WHERE type != UPPER(type);
