package com.lightbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
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
    private final ObjectMapper objectMapper;

    /** 主标题最大长度 */
    private static final int MAX_TITLE = 30;
    /** 单条副标题最大长度 */
    private static final int MAX_SUBTITLE = 30;
    /** 描述最大长度 */
    private static final int MAX_DESCRIPTION = 200;
    /** 功能标题最大长度 */
    private static final int MAX_FEATURE_TITLE = 20;
    /** 功能描述最大长度 */
    private static final int MAX_FEATURE_DESC = 40;
    /** GitHub 地址最大长度 */
    private static final int MAX_GITHUB = 200;
    /** 版权信息最大长度 */
    private static final int MAX_COPYRIGHT = 100;

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
        validateLandingConfig(config);
        systemConfigService.updateConfigValue("landing_config", config);
        return Result.ok();
    }

    /**
     * 校验 Landing 配置字段长度
     */
    private void validateLandingConfig(String config) {
        try {
            JsonNode root = objectMapper.readTree(config);

            if (root.has("title") && root.get("title").asText("").length() > MAX_TITLE) {
                throw new BizException("主标题不能超过" + MAX_TITLE + "字");
            }
            if (root.has("description") && root.get("description").asText("").length() > MAX_DESCRIPTION) {
                throw new BizException("描述文字不能超过" + MAX_DESCRIPTION + "字");
            }
            if (root.has("github") && root.get("github").asText("").length() > MAX_GITHUB) {
                throw new BizException("GitHub 地址不能超过" + MAX_GITHUB + "字符");
            }
            if (root.has("copyright") && root.get("copyright").asText("").length() > MAX_COPYRIGHT) {
                throw new BizException("版权信息不能超过" + MAX_COPYRIGHT + "字");
            }
            if (root.has("subtitles") && root.get("subtitles").isArray()) {
                for (JsonNode sub : root.get("subtitles")) {
                    if (sub.asText("").length() > MAX_SUBTITLE) {
                        throw new BizException("副标题不能超过" + MAX_SUBTITLE + "字");
                    }
                }
            }
            if (root.has("features") && root.get("features").isArray()) {
                for (JsonNode feat : root.get("features")) {
                    if (feat.has("title") && feat.get("title").asText("").length() > MAX_FEATURE_TITLE) {
                        throw new BizException("功能标题不能超过" + MAX_FEATURE_TITLE + "字");
                    }
                    if (feat.has("desc") && feat.get("desc").asText("").length() > MAX_FEATURE_DESC) {
                        throw new BizException("功能描述不能超过" + MAX_FEATURE_DESC + "字");
                    }
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("配置格式错误");
        }
    }
}
