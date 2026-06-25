package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.DefaultAiConfigDTO;
import com.lightbot.dto.DefaultModelsConfigDTO;
import jakarta.validation.Valid;
import com.lightbot.service.SystemConfigService;
import com.lightbot.service.TokenBudgetService;
import com.lightbot.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final TokenBudgetService tokenBudgetService;
    private final UserService userService;

    @Operation(summary = "获取默认AI配置（兼容旧接口，等同于默认对话模型）")
    @GetMapping("/default-ai")
    public Result<DefaultAiConfigDTO> getDefaultAiConfig() {
        return Result.ok(systemConfigService.getDefaultAiConfig());
    }

    @Operation(summary = "更新默认AI配置（兼容旧接口）")
    @PutMapping("/default-ai")
    public Result<Void> updateDefaultAiConfig(@Valid @RequestBody DefaultAiConfigDTO config) {
        userService.checkAdmin();
        systemConfigService.updateDefaultAiConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取默认对话模型配置")
    @GetMapping("/default-chat-model")
    public Result<DefaultAiConfigDTO> getDefaultChatModel() {
        return Result.ok(systemConfigService.getDefaultChatModelConfig());
    }

    @Operation(summary = "更新默认对话模型配置")
    @PutMapping("/default-chat-model")
    public Result<Void> updateDefaultChatModel(@Valid @RequestBody DefaultAiConfigDTO config) {
        userService.checkAdmin();
        systemConfigService.updateDefaultChatModelConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取默认向量模型配置")
    @GetMapping("/default-embedding-model")
    public Result<DefaultAiConfigDTO> getDefaultEmbeddingModel() {
        return Result.ok(systemConfigService.getDefaultEmbeddingModelConfig());
    }

    @Operation(summary = "更新默认向量模型配置")
    @PutMapping("/default-embedding-model")
    public Result<Void> updateDefaultEmbeddingModel(@Valid @RequestBody DefaultAiConfigDTO config) {
        userService.checkAdmin();
        systemConfigService.updateDefaultEmbeddingModelConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取默认TTS模型配置")
    @GetMapping("/default-tts-model")
    public Result<DefaultAiConfigDTO> getDefaultTtsModel() {
        return Result.ok(systemConfigService.getDefaultTtsModelConfig());
    }

    @Operation(summary = "更新默认TTS模型配置")
    @PutMapping("/default-tts-model")
    public Result<Void> updateDefaultTtsModel(@Valid @RequestBody DefaultAiConfigDTO config) {
        userService.checkAdmin();
        systemConfigService.updateDefaultTtsModelConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取默认重排模型配置")
    @GetMapping("/default-rerank-model")
    public Result<DefaultAiConfigDTO> getDefaultRerankModel() {
        return Result.ok(systemConfigService.getDefaultRerankModelConfig());
    }

    @Operation(summary = "更新默认重排模型配置")
    @PutMapping("/default-rerank-model")
    public Result<Void> updateDefaultRerankModel(@Valid @RequestBody DefaultAiConfigDTO config) {
        userService.checkAdmin();
        systemConfigService.updateDefaultRerankModelConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取所有默认模型配置")
    @GetMapping("/default-models")
    public Result<DefaultModelsConfigDTO> getAllDefaultModels() {
        return Result.ok(systemConfigService.getAllDefaultModels());
    }

    @Operation(summary = "健康检查（公开接口，无需认证）")
    @GetMapping("/health")
    public Result<Map<String, String>> health() {
        return Result.ok(Map.of("status", "ok"));
    }

    // ========== Token 预算管理 ==========

    @Operation(summary = "获取 Token 限额配置")
    @GetMapping("/token-budget/config")
    public Result<Map<String, Object>> getTokenBudgetConfig() {
        return Result.ok(tokenBudgetService.getConfig());
    }

    @Operation(summary = "更新 Token 限额配置")
    @PutMapping("/token-budget/config")
    public Result<Void> updateTokenBudgetConfig(@RequestBody Map<String, Object> config) {
        userService.checkAdmin();
        tokenBudgetService.updateConfig(config);
        return Result.ok();
    }

    @Operation(summary = "获取全局 Token 使用统计")
    @GetMapping("/token-budget/stats")
    public Result<Map<String, Object>> getTokenBudgetStats() {
        return Result.ok(tokenBudgetService.getGlobalStats());
    }

    @Operation(summary = "获取用户 Token 消耗排行")
    @GetMapping("/token-budget/ranking")
    public Result<List<Map<String, Object>>> getTokenBudgetRanking(
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(tokenBudgetService.getUserRanking(limit));
    }
}