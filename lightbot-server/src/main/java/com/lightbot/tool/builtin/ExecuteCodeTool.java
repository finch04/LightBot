package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.dto.CodeExecResult;
import com.lightbot.service.sandbox.SandboxService;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置工具 — 代码执行
 * <p>在安全沙盒中执行代码片段，支持 Java、JavaScript 和 Python。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component("executeCodeTool")
@RequiredArgsConstructor
@SystemTool(displayName = "代码执行", description = "在安全沙盒中执行代码片段并返回结果",
        tags = {"code", "execution", "sandbox"},
        outputExample = "{\"success\":true,\"output\":\"\",\"returnValue\":\"2026-06-24T10:30:00\",\"error\":null,\"elapsedMs\":42,\"language\":\"java\"}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"success\":{\"type\":\"boolean\",\"description\":\"是否成功\"},\"output\":{\"type\":\"string\",\"description\":\"stdout输出\"},\"returnValue\":{\"type\":\"string\",\"description\":\"返回值\"},\"error\":{\"type\":\"string\",\"description\":\"错误信息\"},\"elapsedMs\":{\"type\":\"number\",\"description\":\"执行耗时(ms)\"},\"language\":{\"type\":\"string\",\"description\":\"使用的语言\"}}}")
public class ExecuteCodeTool {

    private final SandboxService sandboxService;
    private final ObjectMapper objectMapper;

    @Tool(name = "execute_code",
          description = "在安全沙盒中执行代码片段并返回结果。支持 Java（默认）、JavaScript 和 Python。"
                  + "\n【Java】直接写方法体（无需 import、class、main）。"
                  + "已预导入 java.time.*、java.util.*、java.math.*、java.text.*、java.util.stream.*、java.util.regex.*。"
                  + "如需返回值，用 return 语句。示例：return java.time.LocalDateTime.now().toString();"
                  + "\n【JavaScript】必须定义 function main(){} 作为入口（var 声明，ES5 语法，不支持 const/let/箭头函数）。"
                  + "示例：function main(){ var now = new Date(); return now.toISOString(); }"
                  + "\n【Python】必须定义 def main(): 作为入口。"
                  + "示例：def main(): return 'hello'"
                  + "\n所有语言禁止文件/网络/进程操作。超时 5 秒。")
    public String execute(
            @ToolParam(description = "要执行的代码（Java写方法体，JS/Python写含main函数的完整代码）")
            @ToolParamMeta(example = "return java.time.LocalDateTime.now().toString()") String code,
            @ToolParam(description = "编程语言（java/javascript/python），默认 java", required = false)
            @ToolParamMeta(example = "java") String language) {
        String lang = language != null ? language : "java";
        log.info("[Tool:execute_code] 语言={}, 代码长度={}", lang, code != null ? code.length() : 0);

        if (code == null || code.isBlank()) {
            return toJson(CodeExecResult.builder()
                    .success(false)
                    .error("代码不能为空")
                    .language(lang)
                    .build());
        }

        try {
            ToolEventEmitter.emit("正在执行 " + lang.toUpperCase() + " 代码...");
            CodeExecResult result = sandboxService.executeCode(code.trim(), language, null, null);
            if (result.isSuccess()) {
                ToolEventEmitter.emit("代码执行完成（" + result.getElapsedMs() + "ms）");
            } else {
                ToolEventEmitter.emit("代码执行失败: " + result.getError());
            }
            return toJson(result);
        } catch (BizException e) {
            // 环境不可用（引擎未就绪 / 语言不支持）
            log.warn("[Tool:execute_code] 环境异常: {}", e.getMessage());
            ToolEventEmitter.emit("代码执行失败: " + e.getMessage());
            return toJson(CodeExecResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .language(lang)
                    .build());
        } catch (Exception e) {
            log.error("[Tool:execute_code] 执行异常", e);
            ToolEventEmitter.emit("代码执行异常: " + e.getMessage());
            return toJson(CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + e.getMessage())
                    .language(lang)
                    .build());
        }
    }

    private String toJson(CodeExecResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"序列化失败\"}";
        }
    }
}
