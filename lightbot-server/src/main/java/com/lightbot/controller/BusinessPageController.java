package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.BusinessPageHtmlGenerateDTO;
import com.lightbot.dto.BusinessPageHtmlNormalizeDTO;
import com.lightbot.dto.BusinessPageKeyConfigUpdateDTO;
import com.lightbot.dto.BusinessPageUpsertDTO;
import com.lightbot.entity.BusinessPage;
import com.lightbot.service.BusinessPageService;
import com.lightbot.service.UserService;
import com.lightbot.vo.BusinessPageKeyConfigVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务办理页管理（管理员）
 *
 * @author finch
 * @since 2026-08-04
 */
@Tag(name = "业务办理页")
@RestController
@RequestMapping("/api/business-pages")
@RequiredArgsConstructor
public class BusinessPageController {

    private final BusinessPageService businessPageService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "业务办理页列表（管理端）")
    public Result<List<BusinessPage>> list() {
        userService.checkAdmin();
        return Result.ok(businessPageService.listAllForAdmin());
    }

    @GetMapping("/enabled")
    @Operation(summary = "已启用业务办理页（建设者可选）")
    public Result<List<Map<String, Object>>> listEnabled() {
        return Result.ok(businessPageService.listEnabledDefinitions().stream()
                .map(d -> Map.<String, Object>of(
                        "pageType", d.pageType(),
                        "displayName", d.displayName(),
                        "description", d.description() != null ? d.description() : "",
                        "builtin", d.builtin()
                ))
                .toList());
    }

    @GetMapping("/runtime/{pageType}")
    @Operation(summary = "对话渲染用：按 pageType 取启用中的页面内容（含 pageHtml/pageUrl）")
    public Result<Map<String, Object>> runtime(@PathVariable String pageType) {
        return businessPageService.resolveEnabled(pageType)
                .map(d -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("pageType", d.pageType());
                    body.put("displayName", d.displayName());
                    body.put("defaultTitle", d.defaultTitle() != null ? d.defaultTitle() : "");
                    if (d.pageHtml() != null && !d.pageHtml().isBlank()) {
                        body.put("pageHtml", d.pageHtml());
                    }
                    if (d.pageUrl() != null && !d.pageUrl().isBlank()) {
                        body.put("pageUrl", d.pageUrl());
                    }
                    return Result.ok(body);
                })
                .orElseGet(() -> Result.fail(404, "业务页不存在或已禁用"));
    }

    @PostMapping
    @Operation(summary = "创建/更新业务办理页")
    public Result<BusinessPage> upsert(@Valid @RequestBody BusinessPageUpsertDTO dto) {
        userService.checkAdmin();
        return Result.ok(businessPageService.upsert(dto));
    }

    @PostMapping("/generate-html")
    @Operation(summary = "AI 辅助生成业务页 HTML")
    public Result<Map<String, String>> generateHtml(@Valid @RequestBody BusinessPageHtmlGenerateDTO dto) {
        userService.checkAdmin();
        return Result.ok(Map.of("html", businessPageService.generateHtml(dto)));
    }

    @PostMapping("/normalize-html")
    @Operation(summary = "AI 对齐平台样式（规范化业务页 HTML）")
    public Result<Map<String, String>> normalizeHtml(@Valid @RequestBody BusinessPageHtmlNormalizeDTO dto) {
        userService.checkAdmin();
        return Result.ok(Map.of("html", businessPageService.normalizeHtml(dto)));
    }

    @PutMapping("/{id}/enabled")
    @Operation(summary = "启用/禁用业务办理页")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {
        userService.checkAdmin();
        businessPageService.setEnabled(id, Boolean.TRUE.equals(enabled));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除业务办理页")
    public Result<Void> delete(@PathVariable Long id) {
        userService.checkAdmin();
        businessPageService.deleteCustom(id);
        return Result.ok();
    }

    @GetMapping("/api-keys/{apiKeyId}/config")
    @Operation(summary = "查询 API Key 业务页白名单")
    public Result<BusinessPageKeyConfigVO> getKeyConfig(@PathVariable Long apiKeyId) {
        userService.checkAdmin();
        return Result.ok(businessPageService.getKeyConfig(apiKeyId));
    }

    @PutMapping("/api-keys/{apiKeyId}/config")
    @Operation(summary = "更新 API Key 业务页白名单")
    public Result<BusinessPageKeyConfigVO> updateKeyConfig(@PathVariable Long apiKeyId,
                                                           @RequestBody BusinessPageKeyConfigUpdateDTO dto) {
        userService.checkAdmin();
        return Result.ok(businessPageService.updateKeyConfig(apiKeyId, dto));
    }
}
