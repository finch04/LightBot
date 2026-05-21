package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.dto.SkillRequest;
import com.lightbot.entity.Skill;
import com.lightbot.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Skill管理", description = "Skill的增删改查")
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @Operation(summary = "新增Skill")
    @PostMapping
    public Result<Skill> create(@Valid @RequestBody SkillRequest request) {
        return Result.ok(skillService.create(request));
    }

    @Operation(summary = "更新Skill")
    @PutMapping
    public Result<Skill> update(@Valid @RequestBody SkillRequest request) {
        return Result.ok(skillService.update(request));
    }

    @Operation(summary = "删除Skill")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillService.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "获取Agent下的Skill列表")
    @GetMapping("/by-agent/{agentId}")
    public Result<List<Skill>> listByAgent(@PathVariable Long agentId,
                                           @RequestParam(required = false) String name) {
        return Result.ok(skillService.listByAgentId(agentId, name));
    }
}
