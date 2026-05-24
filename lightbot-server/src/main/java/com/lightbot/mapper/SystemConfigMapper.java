package com.lightbot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lightbot.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置Mapper
 *
 * @author finch
 * @since 2026-05-24
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {
}