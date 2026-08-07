package com.lightbot.dto.askdata;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 问数关联保存
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
public class AskRelationSaveDTO {

    @Size(max = 128)
    private String name;

    @NotNull(message = "源数据集不能为空")
    private Long fromDatasetId;

    @NotBlank(message = "源字段不能为空")
    private String fromField;

    @NotNull(message = "目标数据集不能为空")
    private Long toDatasetId;

    @NotBlank(message = "目标字段不能为空")
    private String toField;
}
