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
    EXPERIMENT_RUN("experiment_run", "实验执行", "experimentRunExecutor"),
    BENCHMARK_GENERATE("benchmark_generate", "基准生成", "benchmarkGenerateExecutor"),
    BENCHMARK_IMPORT("benchmark_import", "基准导入", "benchmarkImportExecutor"),
    RAG_EVALUATION("rag_evaluation", "RAG评估", "ragEvaluationExecutor"),
    GRAPH_EXTRACTION("graph_extraction", "图谱抽取", "graphExtractionExecutor"),
    QA_PAIR_GENERATE("qa_pair_generate", "问答对生成", "qaPairGenerateExecutor");

    @EnumValue
    private final String code;

    private final String desc;

    private final String beanName;

    @JsonValue
    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static TaskType fromValue(String value) {
        for (TaskType e : values()) {
            if (e.code.equalsIgnoreCase(value) || e.desc.equals(value) || e.name().equalsIgnoreCase(value)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的任务类型: " + value);
    }
}
