package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.enums.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 枚举值查询接口
 *
 * @author finch
 * @since 2026-05-24
 */
@Tag(name = "枚举查询", description = "获取系统各类枚举值")
@RestController
@RequestMapping("/api/enums")
public class EnumController {

    @Operation(summary = "获取工具类型枚举")
    @GetMapping("/tool-types")
    public Result<List<EnumVO>> getToolTypes() {
        return Result.ok(toEnumVOList(ToolType.values()));
    }

    @Operation(summary = "获取模型提供商类型枚举")
    @GetMapping("/model-provider-types")
    public Result<List<EnumVO>> getModelProviderTypes() {
        return Result.ok(toEnumVOList(ModelProviderType.values()));
    }

    @Operation(summary = "获取 Agent 状态枚举")
    @GetMapping("/agent-statuses")
    public Result<List<EnumVO>> getAgentStatuses() {
        return Result.ok(toEnumVOList(AgentStatus.values()));
    }

    @Operation(summary = "获取模型类型枚举")
    @GetMapping("/model-types")
    public Result<List<EnumVO>> getModelTypes() {
        return Result.ok(toEnumVOList(ModelType.values()));
    }

    /**
     * 枚举转 VO 列表
     */
    private List<EnumVO> toEnumVOList(EnumDisplay[] enums) {
        return Arrays.stream(enums)
                .map(e -> new EnumVO(e.getCode(), e.getDesc()))
                .collect(Collectors.toList());
    }

    /**
     * 枚举值 VO
     */
    public static class EnumVO {
        private String value;
        private String label;

        public EnumVO() {}

        public EnumVO(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}