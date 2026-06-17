-- ============================================================
-- Phase 1 优化：数据库索引 + 向量索引调优
-- ============================================================

-- 1. llm_trace 复合索引：可观测页面主要查询模式（traceSource + 时间范围）
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_llm_trace_source_time
    ON llm_trace (trace_source, create_time DESC);

-- 2. llm_trace Agent 维度查询索引
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_llm_trace_agent_source
    ON llm_trace (agent_id, trace_source);

-- 3. tool_calls 时间索引：可观测页面工具调用列表按时间排序
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_tool_calls_created_at
    ON tool_calls (created_at DESC);

-- 4. chat_session 复合索引：用户按 Agent 筛选会话
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_chat_session_user_agent
    ON chat_session (user_id, agent_id);

-- 5. 向量索引优化：删除冗余索引，统一为 cosine HNSW (ef_construction=200)
--    说明：cosine 和 L2 两个 HNSW 索引同时存在，每次 INSERT/UPDATE 都要维护两个索引
--    删除后统一使用 cosine 距离（RAG 场景 cosine 优于 L2），ef_construction 从 64 提升到 200
DROP INDEX CONCURRENTLY IF EXISTS idx_embedding_vector;
DROP INDEX CONCURRENTLY IF EXISTS idx_embedding_vector_hnsw;
CREATE INDEX idx_embedding_vector_hnsw ON embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
