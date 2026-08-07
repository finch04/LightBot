package com.lightbot.dto.askdata;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能问数 Intent IR（模型产出，引擎执行）
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AskDataIntentIR {

    /** 数据集 code 或 id 字符串 */
    private String dataset;

    /**
     * lookup | aggregate | trend | compare | rank | distribute
     */
    private String intent = "aggregate";

    private List<String> metrics = new ArrayList<>();

    private List<String> dimensions = new ArrayList<>();

    private List<AskDataFilter> filters = new ArrayList<>();

    private List<AskDataOrderBy> orderBy = new ArrayList<>();

    private Integer limit;

    private Integer pageNum;

    private Integer pageSize;

    /** 趋势粒度 day|week|month */
    private String timeGrain;

    private String keyword;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AskDataFilter {
        private String dim;
        private String field;
        private String op = "eq";
        private Object value;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AskDataOrderBy {
        private String metric;
        private String field;
        private String dim;
        private String dir = "desc";
    }
}
