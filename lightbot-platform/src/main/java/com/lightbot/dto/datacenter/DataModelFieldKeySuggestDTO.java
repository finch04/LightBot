package com.lightbot.dto.datacenter;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 补全字段英文名请求
 *
 * @author finch
 * @since 2026-07-28
 */
@Data
public class DataModelFieldKeySuggestDTO {

    /**
     * 待补全的中文显示名（仅英文名为空的字段）
     */
    @NotEmpty(message = "待补全字段不能为空")
    @Size(max = 80, message = "单次最多补全80个字段")
    private List<String> names = new ArrayList<>();

    /**
     * 已占用的英文名（含已填写字段、系统字段），避免冲突
     */
    private List<String> occupiedKeys = new ArrayList<>();
}
