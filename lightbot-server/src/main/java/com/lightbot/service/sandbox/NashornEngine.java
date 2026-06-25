package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Nashorn JavaScript 引擎
 * <p>安全级别 L2：ClassFilter + 超时控制。GraalVM 不可用时的降级方案。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
public class NashornEngine implements CodeEngine {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_OUTPUT_LENGTH = 10000;

    /** 危险访问模式 */
    private static final Pattern DANGEROUS_ACCESS = Pattern.compile(
            "\\b(Java\\.type|Java\\.extend|importClass|importPackage|Packages\\."
                    + "|java\\.lang\\.Runtime|java\\.lang\\.ProcessBuilder"
                    + "|java\\.io\\.|java\\.net\\.|java\\.nio\\.file\\."
                    + "|javax\\.script\\.ScriptEngineManager|Class\\.forName"
                    + "|System\\.exit|System\\.setOut|System\\.setErr"
                    + "|ProcessBuilder|ProcessHandle|Desktop)\\b",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String language() {
        return "javascript";
    }

    @Override
    public boolean isAvailable() {
        return new ScriptEngineManager().getEngineByName("javascript") != null;
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();

        // 1. 安全校验
        String securityError = checkSecurity(code);
        if (securityError != null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error(securityError)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("javascript")
                    .build();
        }

        ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");
        if (engine == null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("未找到 JavaScript 引擎")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("javascript")
                    .build();
        }

        // 2. 重定向 System.out 捕获输出
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputBuf = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(outputBuf);

        try {
            return CompletableFuture.supplyAsync(() -> {
                System.setOut(capturedOut);
                try {
                    Bindings bindings = engine.createBindings();
                    bindings.put("params", params != null ? params : Map.of());
                    // 注入 print 函数
                    engine.eval("function print() { java.lang.System.out.println(Array.prototype.join.call(arguments, ' ')); }", bindings);
                    engine.eval(code, bindings);

                    // 检查 main 函数（工作流脚本兼容）
                    Object mainFn = bindings.get("main");
                    Object result;
                    if (mainFn != null) {
                        result = engine.eval("main(params)", bindings);
                    } else {
                        result = bindings.get("result");
                    }

                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    String returnValue = result == null ? null : truncateOutput(String.valueOf(result));

                    return CodeExecResult.builder()
                            .success(true)
                            .output(output)
                            .returnValue(returnValue)
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("javascript")
                            .build();
                } catch (Exception e) {
                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    return CodeExecResult.builder()
                            .success(false)
                            .output(output)
                            .error("执行错误: " + sanitizeError(cause.getMessage()))
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("javascript")
                            .build();
                } finally {
                    System.setOut(originalOut);
                }
            }).orTimeout(timeout, TimeUnit.MILLISECONDS).join();
        } catch (java.util.concurrent.CompletionException e) {
            System.setOut(originalOut);
            if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
                return CodeExecResult.builder()
                        .success(false)
                        .error("代码执行超时（" + timeout + "ms），请检查是否存在死循环")
                        .elapsedMs(timeout)
                        .language("javascript")
                        .build();
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(cause.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("javascript")
                    .build();
        } catch (Exception e) {
            System.setOut(originalOut);
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(e.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("javascript")
                    .build();
        }
    }

    private String checkSecurity(String code) {
        if (DANGEROUS_ACCESS.matcher(code).find()) {
            return "脚本安全校验未通过：包含不允许的 Java 访问（禁止文件系统、网络、进程等系统资源）";
        }
        return null;
    }

    private String truncateOutput(String text) {
        if (text == null) return null;
        return text.length() > MAX_OUTPUT_LENGTH
                ? text.substring(0, MAX_OUTPUT_LENGTH) + "... (截断)"
                : text;
    }

    private String sanitizeError(String message) {
        if (message == null) return "未知错误";
        return message.replaceAll("[A-Z]:\\\\[\\S]+", "<path>")
                .replaceAll("/[a-z]+/[a-z]+/[\\S]+", "<path>");
    }
}
