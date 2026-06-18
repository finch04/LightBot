package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.SkillImportPreview;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;
import com.lightbot.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Skill管理", description = "Skill 的增删改查与 Agent 绑定")
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "新增 Skill（全局或 Agent 私有）")
    @PostMapping
    public Result<Skill> create(@Valid @RequestBody SkillRequest request) {
        return Result.ok(skillService.create(request));
    }

    @Operation(summary = "更新 Skill（内置不可编辑）")
    @PutMapping
    public Result<Skill> update(@Valid @RequestBody SkillRequest request) {
        return Result.ok(skillService.update(request));
    }

    @Operation(summary = "删除 Skill（内置不可删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "分页查询全局 Skill 库")
    @GetMapping
    public Result<Page<Skill>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(skillService.listGlobal(pageNum, pageSize, keyword));
    }

    @Operation(summary = "获取所有启用中的全局 Skill（供 Agent 绑定下拉）")
    @GetMapping("/enabled")
    public Result<List<Skill>> listEnabled() {
        return Result.ok(skillService.listEnabled());
    }

    @Operation(summary = "启用/禁用 Skill")
    @PutMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        skillService.setEnabled(id, enabled);
        return Result.ok();
    }

    @Operation(summary = "获取 Agent 私有 Skill 列表（兼容旧版）")
    @GetMapping("/by-agent/{agentId}")
    public Result<List<Skill>> listByAgent(@PathVariable Long agentId,
                                           @RequestParam(required = false) String name) {
        return Result.ok(skillService.listByAgentId(agentId, name));
    }

    @Operation(summary = "ZIP 导入 Skill（阶段一：暂存草稿并返回预览）")
    @PostMapping("/import/preview")
    public Result<SkillImportPreview> importPreview(
            @RequestParam("file") MultipartFile file) throws Exception {
        return Result.ok(skillService.importZipStage(file.getInputStream()));
    }

    @Operation(summary = "ZIP 导入 Skill（阶段二：确认提交）")
    @PostMapping("/import/commit")
    public Result<Skill> importCommit(
            @RequestParam String draftId,
            @RequestParam(required = false) String targetSlug) {
        return Result.ok(skillService.importZipCommit(draftId, targetSlug));
    }

    @Operation(summary = "导出 Skill 为 ZIP")
    @GetMapping("/{id}/export")
    public Result<byte[]> exportZip(@PathVariable Long id) {
        return Result.ok(skillService.exportZip(id));
    }
}
