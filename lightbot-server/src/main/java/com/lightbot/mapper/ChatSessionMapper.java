package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    @Update("UPDATE chat_session SET message_count = message_count + #{msgDelta}, " +
            "total_tokens = total_tokens + #{tokenDelta}, last_message_at = NOW() WHERE id = #{sessionId}")
    void incrementStats(@Param("sessionId") Long sessionId, @Param("msgDelta") int msgDelta, @Param("tokenDelta") long tokenDelta);
}
