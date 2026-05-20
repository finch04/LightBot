package com.lightbot.model.chunking;

import lombok.Data;

/**
 * 分块参数
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class ChunkParams {

    /** 分块 token 上限 */
    private int chunkTokenNum = 512;

    /** 重叠百分比（0-99） */
    private int overlappedPercent = 10;

    /** 分隔符 */
    private String delimiter = "\n";
}
