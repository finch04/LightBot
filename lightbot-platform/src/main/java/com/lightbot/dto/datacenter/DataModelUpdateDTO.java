package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新数据模型基础信息
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataModelUpdateDTO {

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 40, message = "模型名称不能超过40字")
    private String name;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @Size(max = 200, message = "描述不能超过200字")
    private String description;
}
