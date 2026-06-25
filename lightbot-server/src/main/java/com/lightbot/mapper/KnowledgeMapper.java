package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeMapper extends BaseMapper<Knowledge> {

    @Update("UPDATE knowledge SET document_count = document_count + #{docDelta}, " +
            "chunk_count = chunk_count + #{chunkDelta}, " +
            "total_tokens = total_tokens + #{tokenDelta} WHERE id = #{id}")
    void incrementStats(@Param("id") Long id,
                        @Param("docDelta") int docDelta,
                        @Param("chunkDelta") int chunkDelta,
                        @Param("tokenDelta") long tokenDelta);
}
