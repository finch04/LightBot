package com.lightbot.util;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Apache Tika 文档解析工具
 * <p>支持 PDF、DOC、DOCX、PPT、PPTX、XLS、XLSX、TXT、MD、HTML、CSV 等格式</p>
 *
 * @author finch
 * @since 2026-05-20
 */
@Slf4j
@Component
public class TikaUtil {

    private final Tika tika = new Tika();

    /** 支持的文件扩展名 */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "md", "txt", "pdf", "doc", "docx", "ppt", "pptx",
            "xls", "xlsx", "csv", "html", "htm"
    );

    /**
     * 判断文件类型是否支持
     *
     * @param extension 文件扩展名（不含点号）
     * @return 是否支持
     */
    public boolean isSupported(String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * 使用 Tika 解析文件内容为纯文本
     *
     * @param inputStream 文件输入流
     * @param filename    文件名（用于日志和类型推断）
     * @return 解析后的纯文本内容
     */
    public String parse(InputStream inputStream, String filename) {
        try {
            // 使用 Tika 解析文件，不依赖 Metadata 常量避免版本兼容问题
            String content = tika.parseToString(inputStream);
            log.info("[TikaUtil] 文档解析成功: filename={}, length={}", filename, content.length());
            return content;
        } catch (Exception e) {
            log.error("[TikaUtil] 文档解析失败: filename={}, error={}", filename, e.getMessage());
            throw new BizException(ErrorCode.DOCUMENT_PARSE_FAILED, filename);
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
