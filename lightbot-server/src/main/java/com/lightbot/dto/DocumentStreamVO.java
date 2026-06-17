package com.lightbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.InputStream;

/**
 * 文档流式下载VO
 *
 * @author finch
 * @since 2026-06-17
 */
@Data
@AllArgsConstructor
public class DocumentStreamVO {

    private InputStream inputStream;
    private String fileName;
    private String contentType;
}
