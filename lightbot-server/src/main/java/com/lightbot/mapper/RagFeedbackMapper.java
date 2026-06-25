package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.RagFeedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 检索反馈 Mapper
 *
 * @author finch
 * @since 2026-06-25
 */
@Mapper
public interface RagFeedbackMapper extends BaseMapper<RagFeedback> {
}
