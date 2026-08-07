package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据模型分类保存
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataModelCategorySaveDTO {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 20, message = "分类名称不能超过20字")
    private String name;

    private Integer sortOrder;
}
