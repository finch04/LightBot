package com.lightbot.controller;

import com.lightbot.common.BizException;
import com.lightbot.common.Result;
import com.lightbot.dto.ApiKeyCreateDTO;
import com.lightbot.dto.LongMemoryKeyConfigUpdateDTO;
import com.lightbot.entity.ApiKey;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.LongMemoryPolicyService;
import com.lightbot.service.UserMemoryService;
import com.lightbot.service.UserService;
import com.lightbot.vo.ExternalMemoryUserSummaryVO;
import com.lightbot.vo.LongMemoryKeyConfigVO;
import com.lightbot.vo.UserMemoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企业 API Key 管理接口（仅管理员）
 *
 * @author finch
 * @since 2026-06-25
 */
@Tag(name = "企业 API Key 管理")
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;
    private final UserMemoryService userMemoryService;
    private final LongMemoryPolicyService longMemoryPolicyService;

    @GetMapping
    @Operation(summary = "查询企业 API Key 列表")
    public Result<List<ApiKey>> list() {
        userService.checkAdmin();
        return Result.ok(apiKeyService.listAll());
    }

    @PostMapping
    @Operation(summary = "创建企业 API Key")
    public Result<Map<String, Object>> create(@Valid @RequestBody ApiKeyCreateDTO request) {
        userService.checkAdmin();
        // userId 记为创建人（管理员）审计字段，不作为访问隔离维度
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.createApiKey(userId, request.getName(), request.getPermissions(),
                request.getExpiresAt(), request.getAgentIds(), request.getRateLimit(), request.getDailyQuota()));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用企业 API Key")
    public Result<Void> toggle(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.toggleEnabled(id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除企业 API Key")
    public Result<Void> delete(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.deleteApiKey(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/regenerate")
    @Operation(summary = "重新生成企业 API Key")
    public Result<Map<String, Object>> regenerate(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.regenerateApiKey(id, userId));
    }

    @GetMapping("/{id}/memory-config")
    @Operation(summary = "查询企业 API Key 长期记忆策略")
    public Result<LongMemoryKeyConfigVO> getMemoryConfig(@PathVariable Long id) {
        userService.checkAdmin();
        return Result.ok(longMemoryPolicyService.getKeyConfig(id));
    }

    @PutMapping("/{id}/memory-config")
    @Operation(summary = "更新企业 API Key 长期记忆策略")
    public Result<LongMemoryKeyConfigVO> updateMemoryConfig(@PathVariable Long id,
                                                            @Valid @RequestBody LongMemoryKeyConfigUpdateDTO request) {
        userService.checkAdmin();
        return Result.ok(longMemoryPolicyService.updateKeyConfig(id, request));
    }

    @GetMapping("/{id}/memory-users")
    @Operation(summary = "查询企业 API Key 下外部用户记忆汇总")
    public Result<List<ExternalMemoryUserSummaryVO>> listMemoryUsers(@PathVariable Long id) {
        userService.checkAdmin();
        ensureApiKeyExists(id);
        return Result.ok(userMemoryService.listExternalUserSummaries(id));
    }

    @GetMapping("/{id}/memories")
    @Operation(summary = "查询企业 API Key 下外部用户记忆明细")
    public Result<List<UserMemoryVO>> listMemories(@PathVariable Long id,
                                                   @RequestParam(required = false) String externalUserId,
                                                   @RequestParam(required = false) String status,
                                                   @RequestParam(required = false) String keyword) {
        userService.checkAdmin();
        ensureApiKeyExists(id);
        return Result.ok(userMemoryService.listExternalMemories(id, externalUserId, status, keyword));
    }

    @DeleteMapping("/{id}/memories")
    @Operation(summary = "清空企业 API Key 下指定外部用户的全部记忆")
    public Result<Map<String, Object>> clearMemories(@PathVariable Long id,
                                                     @RequestParam String externalUserId) {
        userService.checkAdmin();
        ensureApiKeyExists(id);
        int deleted = userMemoryService.clearExternalUserMemories(id, externalUserId);
        return Result.ok(Map.of("deleted", deleted));
    }

    @DeleteMapping("/{id}/memories/{memoryId}")
    @Operation(summary = "删除企业 API Key 下单条外部用户记忆")
    public Result<Void> deleteMemory(@PathVariable Long id, @PathVariable Long memoryId) {
        userService.checkAdmin();
        ensureApiKeyExists(id);
        userMemoryService.deleteExternalMemory(id, memoryId);
        return Result.ok();
    }

    private void ensureApiKeyExists(Long id) {
        if (apiKeyService.getById(id) == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
    }
}
