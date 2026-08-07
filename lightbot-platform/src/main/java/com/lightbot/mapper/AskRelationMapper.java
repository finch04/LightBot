package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.AskRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问数关联 Mapper
 *
 * @author finch
 * @since 2026-07-30
 */
@Mapper
public interface AskRelationMapper extends BaseMapper<AskRelation> {
}
