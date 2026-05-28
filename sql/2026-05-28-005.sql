-- 评估基准表新增状态字段
ALTER TABLE eval_rag_benchmark ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ready';
COMMENT ON COLUMN eval_rag_benchmark.status IS '状态：generating-生成中, ready-就绪';
