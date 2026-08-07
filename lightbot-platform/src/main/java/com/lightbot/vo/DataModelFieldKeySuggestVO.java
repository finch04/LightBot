package com.lightbot.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 补全字段英文名结果（与请求 names 顺序一一对应）
 *
 * @author finch
 * @since 2026-07-28
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataModelFieldKeySuggestVO {

    private List<String> keys = new ArrayList<>();
}
