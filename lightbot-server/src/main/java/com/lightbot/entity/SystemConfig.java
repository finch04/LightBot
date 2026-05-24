package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置实体
 *
 * @author finch
 * @since 2026-05-24
 */
@Data
@TableName("system_config")
@Schema(description = "系统配置表")
public class SystemConfig {

    @TableId("config_key")
    @Schema(description = "配置键")
    private String configKey;

    @TableField("config_value")
    @Schema(description = "配置值（JSON格式）")
    private String configValue;

    @TableField("description")
    @Schema(description = "配置描述")
    private String description;

    @TableField("create_time")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField("update_time")
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}