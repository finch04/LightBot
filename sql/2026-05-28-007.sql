-- RAG评估结果表新增AI分析字段
ALTER TABLE eval_rag_result ADD COLUMN analysis TEXT;
COMMENT ON COLUMN eval_rag_result.analysis IS 'AI评估分析报告';
