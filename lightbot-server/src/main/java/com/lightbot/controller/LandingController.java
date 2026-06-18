package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.SystemConfigService;
import com.lightbot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Landing 页面接口
 *
 * @author finch
 * @since 2026-06-18
 */
@Tag(name = "Landing", description = "Landing 页面配置")
@RestController
@RequestMapping("/api/landing")
@RequiredArgsConstructor
public class LandingController {

    private final SystemConfigService systemConfigService;
    private final UserService userService;

    /**
     * 获取 Landing 页面配置（公开，无需登录）
     *
     * @return Landing 配置 JSON 字符串
     */
    @GetMapping("/config")
    @Operation(summary = "获取Landing页面配置")
    public Result<String> getLandingConfig() {
        return Result.ok(systemConfigService.getLandingConfig());
    }

    /**
     * 更新 Landing 页面配置（仅管理员）
     *
     * @param config JSON 配置字符串
     */
    @PutMapping("/config")
    @Operation(summary = "更新Landing页面配置")
    public Result<Void> updateLandingConfig(@RequestBody String config) {
        userService.checkAdmin();
        systemConfigService.updateConfigValue("landing_config", config);
        return Result.ok();
    }
}
