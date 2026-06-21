package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 系统初始化状态VO
 *
 * @author finch
 * @since 2026-06-21
 */
@Data
@AllArgsConstructor
@Schema(description = "系统初始化状态")
public class InitStatusVO {

    @Schema(description = "是否已初始化（true=已有用户，false=需要初始化）")
    private Boolean initialized;
}
