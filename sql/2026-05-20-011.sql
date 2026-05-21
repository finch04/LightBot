-- 删除用户邮箱唯一索引，邮箱改为可选字段
DROP INDEX IF EXISTS uk_user_email;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email SET DEFAULT '';
COMMENT ON COLUMN users.email IS '用户邮箱（选填）';
