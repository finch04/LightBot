package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.ApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * API Key Mapper
 *
 * @author finch
 * @since 2026-06-25
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    @Update("UPDATE api_key SET last_used_at = NOW() WHERE id = #{id}")
    void updateLastUsedAt(@Param("id") Long id);
}
