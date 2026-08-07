package com.lightbot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.common.Result;
import com.lightbot.dto.automation.AutomationJobSaveDTO;
import com.lightbot.service.AutomationJobService;
import com.lightbot.vo.AutomationJobRunVO;
import com.lightbot.vo.AutomationJobVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 自动化定时任务接口
 *
 * @author finch
 * @since 2026-07-26
 */
@Tag(name = "自动化定时任务")
@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationJobController {

    private final AutomationJobService automationJobService;
    @Qualifier("lightBotExecutor")
    private final Executor lightBotExecutor;

    @GetMapping("/jobs")
    @Operation(summary = "任务配置列表")
    public Result<List<AutomationJobVO>> listJobs(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Boolean enabled,
                                                  @RequestParam(required = false) Long agentId) {
        return Result.ok(automationJobService.listMine(keyword, enabled, agentId));
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "任务配置详情")
    public Result<AutomationJobVO> getJob(@PathVariable Long id) {
        return Result.ok(automationJobService.getMine(id));
    }

    @PostMapping("/jobs")
    @Operation(summary = "新建定时任务")
    public Result<AutomationJobVO> create(@Valid @RequestBody AutomationJobSaveDTO dto) {
        return Result.ok(automationJobService.create(dto));
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "更新定时任务")
    public Result<AutomationJobVO> update(@PathVariable Long id, @Valid @RequestBody AutomationJobSaveDTO dto) {
        return Result.ok(automationJobService.update(id, dto));
    }

    @DeleteMapping("/jobs/{id}")
    @Operation(summary = "删除定时任务")
    public Result<Void> delete(@PathVariable Long id) {
        automationJobService.delete(id);
        return Result.ok();
    }

    @PostMapping("/jobs/{id}/enable")
    @Operation(summary = "启用任务")
    public Result<AutomationJobVO> enable(@PathVariable Long id) {
        return Result.ok(automationJobService.setEnabled(id, true));
    }

    @PostMapping("/jobs/{id}/disable")
    @Operation(summary = "停用任务")
    public Result<AutomationJobVO> disable(@PathVariable Long id) {
        return Result.ok(automationJobService.setEnabled(id, false));
    }

    @PostMapping("/jobs/{id}/run")
    @Operation(summary = "立即执行一次")
    public Result<Map<String, String>> runNow(@PathVariable Long id) {
        Long runId = automationJobService.prepareManualRun(id);
        lightBotExecutor.execute(() -> automationJobService.executeClaimedRun(runId));
        return Result.ok(Map.of("runId", String.valueOf(runId)));
    }

    @GetMapping("/runs")
    @Operation(summary = "执行记录分页")
    public Result<Page<AutomationJobRunVO>> pageRuns(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long agentId,
                                                     @RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(automationJobService.pageRuns(keyword, status, agentId, pageNum, pageSize));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "执行记录详情")
    public Result<AutomationJobRunVO> getRun(@PathVariable Long id) {
        return Result.ok(automationJobService.getRunMine(id));
    }
}
