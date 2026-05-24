package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.enums.ToolType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

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

    /**
     * 枚举转 VO 列表
     */
    private List<EnumVO> toEnumVOList(Enum<?>[] enums) {
        return java.util.Arrays.stream(enums)
                .map(e -> {
                    EnumVO vo = new EnumVO();
                    // 获取 code 字段（如果存在）
                    try {
                        var codeField = e.getClass().getDeclaredField("code");
                        codeField.setAccessible(true);
                        vo.setValue(codeField.get(e).toString());
                    } catch (Exception ignored) {
                        vo.setValue(e.name().toLowerCase());
                    }
                    // 获取 desc 字段（如果存在）
                    try {
                        var descField = e.getClass().getDeclaredField("desc");
                        descField.setAccessible(true);
                        vo.setLabel(descField.get(e).toString());
                    } catch (Exception ignored) {
                        vo.setLabel(e.name());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 枚举值 VO
     */
    public static class EnumVO {
        private String value;
        private String label;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }
}