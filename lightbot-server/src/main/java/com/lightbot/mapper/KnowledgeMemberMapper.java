package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.KnowledgeMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库成员 Mapper
 *
 * @author finch
 * @since 2026-05-19
 */
@Mapper
public interface KnowledgeMemberMapper extends BaseMapper<KnowledgeMember> {
}
