package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.DocumentVersion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档版本历史Mapper
 *
 * @author finch
 * @since 2026-06-17
 */
@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {
}
