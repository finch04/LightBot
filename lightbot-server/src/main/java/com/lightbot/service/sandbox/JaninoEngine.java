package com.lightbot.service.sandbox;

import com.lightbot.common.BizException;
import com.lightbot.dto.CodeExecResult;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.janino.SimpleCompiler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Janino Java 编译执行引擎
 * <p>安全级别 L3.5：编译期黑名单 + ClassLoader 隔离 + 超时控制。</p>
 * <p>将用户代码包装为类的静态方法，编译后在沙盒 ClassLoader 中执行。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
public class JaninoEngine implements CodeEngine {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_OUTPUT_LENGTH = 10000;

    /** 危险关键字黑名单 */
    private static final Pattern BLOCKED = Pattern.compile(
            "\\b(Runtime|ProcessBuilder|System\\.exit|System\\.setOut|System\\.setErr"
                    + "|Thread|ClassLoader|Class\\.forName|Method\\.invoke|Field\\.set"
                    + "|FileInputStream|FileOutputStream|RandomAccessFile|FileWriter|FileReader"
                    + "|Socket|ServerSocket|HttpURLConnection|URL\\.openConnection"
                    + "|javax\\.script|java\\.lang\\.reflect|sun\\.misc|java\\.io\\.|java\\.net\\."
                    + "|ProcessHandle|Desktop|FileSystem|Unsafe|Compiler)\\b");

    /** 安全 import 前缀 */
    private static final String SAFE_IMPORTS = """
            import java.time.*;
            import java.util.*;
            import java.math.*;
            import java.text.*;
            import java.util.stream.*;
            import java.util.regex.*;
            """;

    @Override
    public String language() {
        return "java";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();

        // 1. 安全校验：危险关键字拦截
        String securityError = checkSecurity(code);
        if (securityError != null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error(securityError)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("java")
                    .build();
        }

        // 2. 包装用户代码为完整 Java 类
        String className = "Sandbox_" + UUID.randomUUID().toString().replace("-", "");
        String wrappedCode = wrapCode(className, code);

        // 3. 编译
        Class<?> compiledClass;
        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.setParentClassLoader(new SandboxingClassLoader(getClass().getClassLoader()));
            compiler.cook(wrappedCode);
            compiledClass = compiler.getClassLoader().loadClass(className);
        } catch (Exception e) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("编译错误: " + sanitizeError(e.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("java")
                    .build();
        }

        // 4. 重定向 System.out 执行
        PrintStream originalOut = System.out;
        ByteArrayOutputStream outputBuf = new ByteArrayOutputStream();
        PrintStream capturedOut = new PrintStream(outputBuf);

        try {
            return CompletableFuture.supplyAsync(() -> {
                System.setOut(capturedOut);
                try {
                    Method runMethod = compiledClass.getMethod("run", Map.class);
                    Object result = runMethod.invoke(null, params);

                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    String returnValue = result == null ? null : truncateOutput(String.valueOf(result));

                    return CodeExecResult.builder()
                            .success(true)
                            .output(output)
                            .returnValue(returnValue)
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("java")
                            .build();
                } catch (Exception e) {
                    String output = truncateOutput(outputBuf.toString(StandardCharsets.UTF_8));
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    return CodeExecResult.builder()
                            .success(false)
                            .output(output)
                            .error("运行时错误: " + sanitizeError(cause.getMessage()))
                            .elapsedMs(System.currentTimeMillis() - start)
                            .language("java")
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
                        .language("java")
                        .build();
            }
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(cause.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("java")
                    .build();
        } catch (Exception e) {
            System.setOut(originalOut);
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(e.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("java")
                    .build();
        }
    }

    /**
     * 将用户代码包装为可编译执行的 Java 类
     */
    private String wrapCode(String className, String code) {
        String methodBody = code.strip();
        // 如果用户代码不以 return 开头且不包含分号结尾的语句，尝试自动加 return
        if (!methodBody.contains("return ") && !methodBody.contains(";") && !methodBody.contains("{")) {
            methodBody = "return " + methodBody;
        }
        // 如果用户代码以 return 开头但没有分号，补上
        if (methodBody.startsWith("return ") && !methodBody.strip().endsWith(";")) {
            methodBody = methodBody + ";";
        }

        return SAFE_IMPORTS + "\n"
                + "public class " + className + " {\n"
                + "    public static Object run(java.util.Map<String, Object> params) {\n"
                + "        " + methodBody + "\n"
                + "    }\n"
                + "}\n";
    }

    private String checkSecurity(String code) {
        if (BLOCKED.matcher(code).find()) {
            return "代码安全校验未通过：包含不允许的关键字（禁止文件/网络/进程/反射操作）";
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
