package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.PromptCreateRequest;
import com.lightbot.dto.PromptRunRequest;
import com.lightbot.dto.PromptTemplateCreateRequest;
import com.lightbot.dto.PromptVersionCreateRequest;
import com.lightbot.entity.Prompt;
import com.lightbot.entity.PromptBuildTemplate;
import com.lightbot.entity.PromptVersion;
import com.lightbot.service.EvalChatService;
import com.lightbot.service.PromptBuildTemplateService;
import com.lightbot.service.PromptService;
import com.lightbot.service.PromptVersionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import cn.dev33.satoken.stp.StpUtil;

import java.util.List;

/**
 * Prompt管理接口
 *
 * @author finch
 * @since 2026-05-27
 */
@Slf4j
@Tag(name = "Prompt管理", description = "Prompt的增删改查及版本管理")
@RestController
@RequestMapping("/api/prompts")
@RequiredArgsConstructor
public class PromptController {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L;

    private final PromptService promptService;
    private final PromptVersionService promptVersionService;
    private final PromptBuildTemplateService promptBuildTemplateService;
    private final EvalChatService evalChatService;

    @Operation(summary = "创建Prompt")
    @PostMapping
    public Result<Prompt> create(@Valid @RequestBody PromptCreateRequest request) {
        return Result.ok(promptService.create(request.getPromptKey(), request.getDescription(), request.getTags(), StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "获取Prompt详情")
    @GetMapping("/{id}")
    public Result<Prompt> getById(@PathVariable Long id) {
        return Result.ok(promptService.getById(id));
    }

    @Operation(summary = "获取Prompt列表")
    @GetMapping
    public Result<Page<Prompt>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(promptService.list(pageNum, pageSize, keyword, StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "更新Prompt")
    @PutMapping
    public Result<Void> update(@RequestParam Long id, @Valid @RequestBody PromptCreateRequest request) {
        promptService.update(id, request.getDescription(), request.getTags());
        return Result.ok();
    }

    @Operation(summary = "删除Prompt")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "创建Prompt版本")
    @PostMapping("/versions")
    public Result<PromptVersion> createVersion(@Valid @RequestBody PromptVersionCreateRequest request) {
        return Result.ok(promptVersionService.create(
                request.getPromptKey(), request.getVersion(), request.getVersionDesc(),
                request.getTemplate(), request.getVariables(), request.getModelConfig(),
                request.getToolConfig(), request.getStatus(), null));
    }

    @Operation(summary = "获取Prompt版本列表")
    @GetMapping("/{promptKey}/versions")
    public Result<List<PromptVersion>> listVersions(@PathVariable String promptKey) {
        return Result.ok(promptVersionService.listByKey(promptKey));
    }

    @Operation(summary = "获取Prompt版本详情")
    @GetMapping("/versions/detail")
    public Result<PromptVersion> getVersionDetail(
            @RequestParam String promptKey, @RequestParam String version) {
        return Result.ok(promptVersionService.getByKeyAndVersion(promptKey, version));
    }

    @Operation(summary = "获取Prompt构建模板列表")
    @GetMapping("/templates")
    public Result<List<PromptBuildTemplate>> listTemplates() {
        return Result.ok(promptBuildTemplateService.listAll());
    }

    @Operation(summary = "获取Prompt构建模板详情")
    @GetMapping("/templates/{key}")
    public Result<PromptBuildTemplate> getTemplate(@PathVariable String key) {
        return Result.ok(promptBuildTemplateService.getByKey(key));
    }

    @Operation(summary = "创建Prompt构建模板")
    @PostMapping("/templates")
    public Result<PromptBuildTemplate> createTemplate(@Valid @RequestBody PromptTemplateCreateRequest request) {
        return Result.ok(promptBuildTemplateService.create(
                request.getPromptTemplateKey(), request.getTemplateDesc(), request.getTemplate(),
                request.getVariables(), request.getModelConfig(), request.getTags()));
    }

    @Operation(summary = "更新Prompt构建模板")
    @PutMapping("/templates")
    public Result<Void> updateTemplate(@RequestParam Long id, @Valid @RequestBody PromptTemplateCreateRequest request) {
        promptBuildTemplateService.update(id, request.getTemplateDesc(), request.getTemplate(),
                request.getVariables(), request.getModelConfig(), request.getTags());
        return Result.ok();
    }

    @Operation(summary = "删除Prompt构建模板")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        promptBuildTemplateService.removeById(id);
        return Result.ok();
    }

    /**
     * 流式运行Prompt（SSE）
     * <p>支持直接传入模板内容调试，或通过promptKey+version引用已保存版本</p>
     */
    @Operation(summary = "流式运行Prompt调试")
    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@Valid @RequestBody PromptRunRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        String template = request.getTemplate();
        String modelConfig = request.getModelConfig();

        // 如果没有直接传入模板，从版本中加载
        if (template == null || template.isBlank()) {
            PromptVersion version = promptVersionService.getByKeyAndVersion(
                    request.getPromptKey(), request.getVersion());
            if (version != null) {
                template = version.getTemplate();
                if (modelConfig == null || modelConfig.isBlank()) {
                    modelConfig = version.getModelConfig();
                }
            }
        }

        if (template == null || template.isBlank()) {
            emitter.completeWithError(new IllegalArgumentException("模板内容为空"));
            return emitter;
        }

        String finalTemplate = template;
        String finalModelConfig = modelConfig;
        Flux<String> flux = evalChatService.callPromptStream(finalModelConfig, finalTemplate, request.getVariables());
        flux.publishOn(Schedulers.boundedElastic())
                .subscribe(
                        chunk -> {
                            try {
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (Exception e) {
                                // client disconnected
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );

        return emitter;
    }
}
