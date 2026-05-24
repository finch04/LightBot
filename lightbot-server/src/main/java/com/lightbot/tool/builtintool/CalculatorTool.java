package com.lightbot.tool.builtintool;

import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 系统内置工具 — 数学计算器
 * <p>执行基本数学运算，包括加减乘除</p>
 *
 * @author finch
 * @since 2026-05-22
 */
@Slf4j
@Component("calculatorTool")
@SystemTool(displayName = "数学计算器", description = "执行基本数学运算，包括加减乘除")
public class CalculatorTool {

    @Tool(name = "calculator",
          description = "执行基本数学运算，包括加减乘除。当用户需要精确数学计算时调用此工具。")
    public String calculate(
            @ToolParam(description = "第一个操作数")
            @ToolParamMeta(example = "10") double a,
            @ToolParam(description = "第二个操作数")
            @ToolParamMeta(example = "5") double b,
            @ToolParam(description = "运算类型：add（加）、subtract（减）、multiply（乘）、divide（除）")
            @ToolParamMeta(example = "add") String operation) {
        log.info("[Tool:calculator] 计算: a={}, b={}, op={}", a, b, operation);

        double result = switch (operation.toLowerCase()) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> {
                if (b == 0) throw new ArithmeticException("除数不能为零");
                yield a / b;
            }
            default -> throw new IllegalArgumentException("不支持的运算类型: " + operation + "，仅支持 add/subtract/multiply/divide");
        };

        String formatted = result == Math.floor(result) && !Double.isInfinite(result)
                ? String.valueOf((long) result)
                : String.format("%.6g", result);
        return formatted;
    }
}
