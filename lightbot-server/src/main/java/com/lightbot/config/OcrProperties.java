package com.lightbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 配置属性
 *
 * @author finch
 * @since 2026-05-21
 */
@Data
@Component
@ConfigurationProperties(prefix = "lightbot.ocr")
public class OcrProperties {

    /**
     * 是否启用 OCR
     */
    private boolean enabled = false;

    /**
     * 模型存放路径（绝对路径或相对路径）
     */
    private String modelPath = "models";

    /**
     * 模型不存在时是否自动下载
     */
    private boolean autoDownload = true;
}
