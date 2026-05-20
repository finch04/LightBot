package com.lightbot.model.chunking;

import java.util.List;

/**
 * 分块策略接口
 *
 * @author finch
 * @since 2026-05-20
 */
public interface ChunkStrategy {

    /**
     * 策略类型标识
     *
     * @return 类型名称（general / book / separator）
     */
    String getType();

    /**
     * 将文本内容按策略拆分为多个分块
     *
     * @param content 原始文本内容
     * @param params  分块参数
     * @return 分块后的内容列表
     */
    List<String> split(String content, ChunkParams params);
}
