package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 远程 Skill 安装准备请求
 *
 * @author finch
 * @since 2026-06-18
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "远程 Skill 安装准备请求")
public class RemotePrepareRequest extends RemoteListRequest {

    @NotEmpty(message = "skills 不能为空")
    @Schema(description = "需要安装的 Skill slug 列表")
    private List<String> skills;
}
