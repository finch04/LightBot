package com.lightbot.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.dto.datacenter.DataModelSchema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据模型返回（schema 已解析为对象）
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataModelVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;

    private String name;
    private String tableName;
    private String description;
    private DataModelSchema schema;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
