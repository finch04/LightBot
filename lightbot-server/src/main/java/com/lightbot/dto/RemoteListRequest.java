package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 远程 Skill 仓库列表请求
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
@Schema(description = "远程 Skill 仓库列表请求")
public class RemoteListRequest {

    @NotBlank(message = "source 不能为空")
    @Schema(description = "仓库来源，支持格式：owner/repo、https://github.com/owner/repo、owner/repo@branch")
    private String source;
}
