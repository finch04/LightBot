package com.lightbot.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务类型
 *
 * @author finch
 * @since 2026-05-21
 */
@Getter
@AllArgsConstructor
public enum TaskType {

    DOCUMENT_UPLOAD("document_upload", "文档上传", "documentUploadExecutor"),
    DOCUMENT_INGEST("document_ingest", "文档入库", "documentIngestExecutor"),
    DOCUMENT_OCR("document_ocr", "文档OCR", "documentOcrExecutor"),
    EXPERIMENT_RUN("experiment_run", "实验执行", "experimentRunExecutor");

    @EnumValue
    private final String code;

    private final String desc;

    private final String beanName;

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static TaskType fromValue(String value) {
        for (TaskType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的任务类型: " + value);
    }
}
