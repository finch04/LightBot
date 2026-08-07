package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建数据模型
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class DataModelCreateDTO {

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 40, message = "模型名称不能超过40字")
    private String name;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    /**
     * 表名后缀（不含 sjc_data_ 前缀），小写字母开头
     */
    @NotBlank(message = "表名后缀不能为空")
    @Size(max = 48, message = "表名后缀过长")
    private String tableNameSuffix;

    @Size(max = 200, message = "描述不能超过200字")
    private String description;
}
