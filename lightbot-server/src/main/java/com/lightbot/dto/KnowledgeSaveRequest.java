package com.lightbot.dto;

import com.lightbot.enums.KnowledgeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识库创建/更新请求 DTO
 * <p>仅包含用户可编辑的字段，防止客户端设置 userId、documentCount 等内部字段</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Data
@Schema(description = "知识库创建/更新请求")
public class KnowledgeSaveRequest {

    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    @Size(max = 50, message = "知识库名称不超过50字")
    @Schema(description = "知识库名称")
    private String name;

    @Size(max = 50, message = "知识库描述不超过50字")
    @Schema(description = "知识库描述")
    private String description;

    @Schema(description = "向量化模型名称")
    private String embeddingModel;

    @Schema(description = "知识库类型：pg / milvus")
    private KnowledgeType type;

    @Schema(description = "扩展配置（JSON）")
    private String config;

    @Schema(description = "检索配置（JSON）")
    private String queryParams;

    @Schema(description = "是否启用知识图谱")
    private Boolean graphEnabled;
}
