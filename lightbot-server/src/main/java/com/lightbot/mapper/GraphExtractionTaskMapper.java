package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.GraphExtractionTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图谱抽取任务 Mapper
 *
 * @author finch
 * @since 2026-05-29
 */
@Mapper
public interface GraphExtractionTaskMapper extends BaseMapper<GraphExtractionTask> {
}
