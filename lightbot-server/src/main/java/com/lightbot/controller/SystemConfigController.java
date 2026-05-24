package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置接口
 *
 * @author finch
 * @since 2026-05-24
 */
@Tag(name = "系统配置", description = "全局系统配置管理")
@RestController
@RequestMapping("/api/system-config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @Operation(summary = "获取默认AI配置")
    @GetMapping("/default-ai")
    public Result<DefaultAiConfigDTO> getDefaultAiConfig() {
        return Result.ok(systemConfigService.getDefaultAiConfig());
    }

    @Operation(summary = "更新默认AI配置")
    @PutMapping("/default-ai")
    public Result<Void> updateDefaultAiConfig(@RequestBody DefaultAiConfigDTO config) {
        systemConfigService.updateDefaultAiConfig(config);
        return Result.ok();
    }
}