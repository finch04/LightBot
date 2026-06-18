package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landing 页面接口（公开，无需登录）
 *
 * @author finch
 * @since 2026-06-18
 */
@Tag(name = "Landing", description = "Landing 页面配置（公开接口）")
@RestController
@RequestMapping("/api/landing")
@RequiredArgsConstructor
public class LandingController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取 Landing 页面配置
     *
     * @return Landing 配置 JSON 字符串
     */
    @GetMapping("/config")
    @Operation(summary = "获取Landing页面配置")
    public Result<String> getLandingConfig() {
        return Result.ok(systemConfigService.getLandingConfig());
    }
}
