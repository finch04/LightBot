package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话文件树响应（懒加载单层 entries + 全局统计）。
 *
 * @author finch
 * @since 2026-06-30
 */
@Data
@Schema(description = "会话文件树响应")
public class SessionFileTreeResponseVO {

    @Schema(description = "当前目录下的条目列表")
    private List<SessionFileEntryVO> entries = new ArrayList<>();

    @Schema(description = "会话文件统计")
    private SessionFileStatsVO stats;
}
