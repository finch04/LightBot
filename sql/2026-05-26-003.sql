-- llm_trace 增加 trace_source，列表仅展示用户对话类 trace
ALTER TABLE llm_trace ADD COLUMN IF NOT EXISTS trace_source VARCHAR(32);
COMMENT ON COLUMN llm_trace.trace_source IS '来源：chat=用户对话；辅助 LLM 调用不写入';
UPDATE llm_trace SET trace_source = 'chat' WHERE trace_source IS NULL;
CREATE INDEX IF NOT EXISTS idx_llm_trace_trace_source ON llm_trace (trace_source);
