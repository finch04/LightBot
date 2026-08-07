package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.service.RegionService;
import com.lightbot.vo.RegionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行政区划地区库 API
 *
 * @author finch
 * @since 2026-08-05
 */
@Tag(name = "地区库")
@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping("/stats")
    @Operation(summary = "地区库统计（含省/市/区分布）")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.putAll(regionService.statsBreakdown());
        return Result.ok(m);
    }

    @GetMapping("/children")
    @Operation(summary = "懒加载子节点（parentCode 空=省级）")
    public Result<List<RegionVO>> children(@RequestParam(required = false) String parentCode) {
        return Result.ok(regionService.listChildren(parentCode));
    }

    @GetMapping("/search")
    @Operation(summary = "按名称/编码搜索")
    public Result<List<RegionVO>> search(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(regionService.search(keyword, limit));
    }

    @GetMapping("/{code}/path")
    @Operation(summary = "区划路径（根→本级，含自身）")
    public Result<List<RegionVO>> path(@PathVariable String code) {
        return Result.ok(regionService.listPath(code));
    }

    @GetMapping("/{code}/descendants")
    @Operation(summary = "本级及下级区划编码（含自身）")
    public Result<List<String>> descendants(@PathVariable String code) {
        return Result.ok(regionService.listSelfAndDescendantCodes(code));
    }

    @PostMapping("/seed")
    @Operation(summary = "空库时导入国标省市区种子")
    public Result<Map<String, Object>> seed() {
        int n = regionService.seedIfEmpty();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imported", n);
        m.put("count", regionService.countActive());
        return Result.ok(m);
    }

    @PostMapping("/reseed")
    @Operation(summary = "清空并重新导入国标省市区（慎用）")
    public Result<Map<String, Object>> reseed() {
        int n = regionService.reseed();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imported", n);
        m.put("count", regionService.countActive());
        return Result.ok(m);
    }
}
