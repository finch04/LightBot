package com.lightbot.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业 API Key 下外部用户记忆汇总
 *
 * @author finch
 * @since 2026-08-01
 */
@Data
public class ExternalMemoryUserSummaryVO {

    /** 上层业务终端用户标识 */
    private String externalUserId;

    /** 启用中的记忆条数 */
    private Long activeCount;

    /** 记忆总条数（含停用） */
    private Long totalCount;

    /** 最近更新时间 */
    private LocalDateTime lastUpdateTime;
}
