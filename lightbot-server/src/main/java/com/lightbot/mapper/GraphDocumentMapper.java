package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.GraphDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图谱文档关联 Mapper
 *
 * @author finch
 * @since 2026-05-30
 */
@Mapper
public interface GraphDocumentMapper extends BaseMapper<GraphDocument> {
}
