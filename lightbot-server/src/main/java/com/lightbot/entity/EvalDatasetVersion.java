package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.EvalDatasetVersionStatus;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 评估数据集版本表
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@TableName("eval_dataset_version")
@Schema(description = "评估数据集版本表")
public class EvalDatasetVersion {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("dataset_id")
    @Schema(description = "数据集ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long datasetId;

    @TableField("version")
    @Schema(description = "版本号")
    private String version;

    @TableField("data_count")
    @Schema(description = "数据条数")
    private Integer dataCount;

    @TableField("status")
    @Schema(description = "版本状态")
    private EvalDatasetVersionStatus status;

    @TableField(value = "dataset_items", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Schema(description = "数据集条目快照")
    private String datasetItems;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
