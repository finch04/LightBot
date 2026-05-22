package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * RAG检索引用信息VO
 * <p>用于在流式对话中返回检索到的文献引用信息</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Data
@Schema(description = "RAG检索引用信息")
public class RagReferenceVO {

    @Schema(description = "文献名称")
    private String documentName;

    @Schema(description = "分块内容预览（前200字符）")
    private String contentPreview;

    @Schema(description = "相似度分数")
    private Double score;

    @Schema(description = "知识库ID")
    private Long knowledgeId;

    @Schema(description = "文档ID")
    private Long documentId;

    @Schema(description = "分块ID")
    private Long chunkId;
}
