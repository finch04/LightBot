package com.lightbot.vo;

import com.lightbot.dto.askdata.AskDataIntentIR;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 问数执行结果（含 Insight 基础字段）
 *
 * @author finch
 * @since 2026-07-30
 */
@Data
@Schema(description = "问数查询结果")
public class AskDataResultVO {

    private String summary;
    private List<String> assumptions = new ArrayList<>();
    private Map<String, Object> chart = new LinkedHashMap<>();
    private Map<String, Object> table = new LinkedHashMap<>();
    private List<String> followups = new ArrayList<>();
    private Map<String, Object> explain = new LinkedHashMap<>();
    private AskDataIntentIR plan;
    private Long elapsedMs;
}
