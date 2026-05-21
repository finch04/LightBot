-- Agent表新增is_default字段，标记用户默认Agent
ALTER TABLE agent ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;
