-- 为embedding表添加HNSW索引，提升向量检索速度
-- HNSW (Hierarchical Navigable Small World) 索引适合高维向量的近似最近邻搜索
-- 使用余弦距离 (vector_l2_ops) 作为距离度量
CREATE INDEX IF NOT EXISTS idx_embedding_vector_hnsw
    ON embedding USING hnsw (vector vector_l2_ops)
    WITH (m = 16, ef_construction = 200);

-- 说明：
-- m = 16: 每个节点的最大连接数，越大检索质量越高但内存占用越多
-- ef_construction = 200: 构建索引时的搜索范围，越大索引质量越高但构建越慢
-- 对于1536维的embedding向量，HNSW索引可将检索速度从O(n)提升到O(log n)
