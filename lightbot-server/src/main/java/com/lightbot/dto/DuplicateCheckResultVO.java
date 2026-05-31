package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 文档内容重复检测结果
 *
 * @author finch
 * @since 2026-05-30
 */
@Data
@Schema(description = "文档内容重复检测结果")
public class DuplicateCheckResultVO {

    @Schema(description = "是否存在重复文档")
    private boolean hasDuplicate;

    @Schema(description = "最高相似度（0-1）")
    private double maxSimilarity;

    @Schema(description = "最相似文档名称")
    private String mostSimilarDocName;

    @Schema(description = "使用的阈值")
    private double threshold;

    @Schema(description = "超阈值的文档列表")
    private List<DuplicateDetail> details;

    @Data
    @Schema(description = "重复文档详情")
    public static class DuplicateDetail {

        @Schema(description = "文档ID")
        private Long documentId;

        @Schema(description = "文档名称")
        private String documentName;

        @Schema(description = "相似度（0-1）")
        private double similarity;
    }

    public static DuplicateCheckResultVO noDuplicate(double threshold) {
        DuplicateCheckResultVO vo = new DuplicateCheckResultVO();
        vo.setHasDuplicate(false);
        vo.setMaxSimilarity(0);
        vo.setThreshold(threshold);
        vo.setDetails(List.of());
        return vo;
    }
}
