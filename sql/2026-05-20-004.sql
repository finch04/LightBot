-- Agent表新增icon字段
ALTER TABLE agent ADD COLUMN icon VARCHAR(32);
COMMENT ON COLUMN agent.icon IS 'Agent图标（emoji或图标标识）';
