package com.lightbot.controller;

import com.lightbot.entity.ApiKey;
import com.lightbot.service.ApiKeyService;
import com.lightbot.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API Key 管理接口
 *
 * @author finch
 * @since 2026-06-25
 */
@Tag(name = "API Key管理")
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @Operation(summary = "查询当前用户的API Key列表")
    public Result<List<ApiKey>> list() {
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.listByUserId(userId));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    @Operation(summary = "创建API Key")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        String name = (String) body.get("name");
        String permissions = (String) body.get("permissions");
        String expiresAt = (String) body.get("expiresAt");
        List<String> agentIds = body.get("agentIds") instanceof List
                ? (List<String>) body.get("agentIds") : null;
        Integer rateLimit = body.get("rateLimit") instanceof Number
                ? ((Number) body.get("rateLimit")).intValue() : null;
        Integer dailyQuota = body.get("dailyQuota") instanceof Number
                ? ((Number) body.get("dailyQuota")).intValue() : null;
        return Result.ok(apiKeyService.createApiKey(userId, name, permissions,
                expiresAt, agentIds, rateLimit, dailyQuota));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用API Key")
    public Result<Void> toggle(@PathVariable Long id) {
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.toggleEnabled(id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除API Key")
    public Result<Void> delete(@PathVariable Long id) {
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.deleteApiKey(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/regenerate")
    @Operation(summary = "重新生成API Key")
    public Result<Map<String, Object>> regenerate(@PathVariable Long id) {
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.regenerateApiKey(id, userId));
    }
}
