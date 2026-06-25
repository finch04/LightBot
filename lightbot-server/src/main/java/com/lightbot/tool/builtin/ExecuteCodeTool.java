package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.CodeExecResult;
import com.lightbot.service.sandbox.SandboxService;
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
 * <p>在安全沙盒中执行代码片段，支持 Java 和 JavaScript。</p>
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
          description = "在安全沙盒中执行代码片段并返回结果。支持 Java（默认）和 JavaScript。"
                  + "Java 代码直接写方法体，无需 class/main 声明。"
                  + "禁止文件/网络/进程操作。超时 5 秒。")
    public String execute(
            @ToolParam(description = "要执行的代码")
            @ToolParamMeta(example = "return java.time.LocalDateTime.now().toString()") String code,
            @ToolParam(description = "编程语言（java/javascript），默认 java", required = false)
            @ToolParamMeta(example = "java") String language) {
        log.info("[Tool:execute_code] 语言={}, 代码长度={}", language, code != null ? code.length() : 0);

        if (code == null || code.isBlank()) {
            return toJson(CodeExecResult.builder()
                    .success(false)
                    .error("代码不能为空")
                    .language(language != null ? language : "java")
                    .build());
        }

        try {
            CodeExecResult result = sandboxService.executeCode(code.trim(), language, null, null);
            return toJson(result);
        } catch (Exception e) {
            log.error("[Tool:execute_code] 执行异常", e);
            return toJson(CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + e.getMessage())
                    .language(language != null ? language : "java")
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
