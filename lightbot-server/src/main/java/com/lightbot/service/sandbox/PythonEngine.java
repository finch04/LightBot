package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Python 代码执行引擎（ProcessBuilder 子进程方式）
 * <p>安全级别 L4：OS 进程隔离，天然沙盒。依赖宿主机安装 Python 3。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
public class PythonEngine implements CodeEngine {

    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int MAX_OUTPUT_LENGTH = 10000;
    private static final String RESULT_START_MARKER = "__SANDBOX_RESULT_START__";
    private static final String RESULT_END_MARKER = "__SANDBOX_RESULT_END__";

    /** 危险 import 黑名单 */
    private static final Pattern BLOCKED_IMPORTS = Pattern.compile(
            "\\b(import\\s+(os|subprocess|shutil|socket|http|urllib|ftplib|smtplib|ctypes|sys|signal|multiprocessing|threading)"
                    + "|from\\s+(os|subprocess|shutil|socket|http|urllib|ftplib|smtplib|ctypes|sys|signal|multiprocessing|threading)\\s+import)");

    /** Python 3 解释器候选路径 */
    private static final String[] PYTHON_CANDIDATES = {"python3", "python"};

    @Override
    public String language() {
        return "python";
    }

    @Override
    public boolean isAvailable() {
        for (String cmd : PYTHON_CANDIDATES) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = p.waitFor();
                if (exitCode == 0 && output.contains("Python 3")) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        long timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        long start = System.currentTimeMillis();

        // 1. 安全校验：危险 import 拦截
        String securityError = checkSecurity(code);
        if (securityError != null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error(securityError)
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("python")
                    .build();
        }

        // 2. 包装用户代码
        String wrappedCode = wrapCode(code, params);

        // 3. 查找 Python 解释器
        String pythonCmd = findPython();
        if (pythonCmd == null) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("Python 执行环境不可用：服务器未安装 Python 3.8+。"
                            + "请在服务器上安装 Python 3（apt install python3 / yum install python3），"
                            + "并确保 python3 或 python 命令可用")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("python")
                    .build();
        }

        // 4. 写入临时文件
        File tempFile;
        try {
            tempFile = File.createTempFile("sandbox_", ".py");
            tempFile.deleteOnExit();
            Files.writeString(tempFile.toPath(), wrappedCode, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("创建临时文件失败: " + e.getMessage())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("python")
                    .build();
        }

        try {
            // 5. 启动子进程
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-X", "utf8", tempFile.getAbsolutePath());
            pb.redirectErrorStream(false);
            Map<String, String> env = pb.environment();
            env.clear();
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUNBUFFERED", "1");

            Process process = pb.start();

            // 6. 读取 stdout 和 stderr
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            // 7. 等待执行完成（带超时）
            boolean finished = process.waitFor(timeout, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CodeExecResult.builder()
                        .success(false)
                        .error("Python 执行超时（" + timeout + "ms），请检查是否存在死循环")
                        .elapsedMs(timeout)
                        .language("python")
                        .build();
            }

            int exitCode = process.exitValue();
            String output = truncateOutput(stripReturnValueBlock(stdout));
            String errorOutput = truncateOutput(stderr);

            if (exitCode == 0) {
                // 解析返回值（最后一行 stdout 为返回值标记）
                String returnValue = parseReturnValue(stdout);
                return CodeExecResult.builder()
                        .success(true)
                        .output(output)
                        .returnValue(returnValue)
                        .elapsedMs(System.currentTimeMillis() - start)
                        .language("python")
                        .build();
            } else {
                return CodeExecResult.builder()
                        .success(false)
                        .output(output)
                        .error("Python 执行失败 (exit=" + exitCode + "): " + sanitizeError(errorOutput))
                        .elapsedMs(System.currentTimeMillis() - start)
                        .language("python")
                        .build();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行被中断")
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("python")
                    .build();
        } catch (Exception e) {
            return CodeExecResult.builder()
                    .success(false)
                    .error("执行异常: " + sanitizeError(e.getMessage()))
                    .elapsedMs(System.currentTimeMillis() - start)
                    .language("python")
                    .build();
        } finally {
            tempFile.delete();
        }
    }

    /**
     * 将用户代码包装为可执行的 Python 脚本
     * <p>注入 params 变量，捕获 main() 返回值，通过标记输出。</p>
     */
    private String wrapCode(String code, Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        sb.append("import json, sys\n");
        sb.append("if hasattr(sys.stdout, 'reconfigure'):\n");
        sb.append("    sys.stdout.reconfigure(encoding='utf-8')\n");
        sb.append("    sys.stderr.reconfigure(encoding='utf-8')\n");

        // 注入 params
        sb.append("params = ");
        if (params != null && !params.isEmpty()) {
            sb.append("json.loads('").append(escapeJson(params)).append("')\n");
        } else {
            sb.append("{}\n");
        }

        sb.append("\n");
        sb.append(code.strip());
        sb.append("\n\n");

        // 调用 main() 并捕获返回值
        sb.append("try:\n");
        sb.append("    _result = main()\n");
        sb.append("    if _result is not None:\n");
        sb.append("        print('" + RESULT_START_MARKER + "')\n");
        sb.append("        if isinstance(_result, (dict, list)):\n");
        sb.append("            print(json.dumps(_result, ensure_ascii=False))\n");
        sb.append("        else:\n");
        sb.append("            print(_result)\n");
        sb.append("        print('" + RESULT_END_MARKER + "')\n");
        sb.append("except NameError:\n");
        sb.append("    pass\n");
        sb.append("except Exception as e:\n");
        sb.append("    print(f'Error: {e}', file=sys.stderr)\n");
        sb.append("    sys.exit(1)\n");

        return sb.toString();
    }

    /**
     * 解析 main() 的返回值（从标记中提取）
     */
    private String parseReturnValue(String stdout) {
        if (stdout == null) return null;
        int startIdx = stdout.indexOf(RESULT_START_MARKER);
        int endIdx = stdout.indexOf(RESULT_END_MARKER);
        if (startIdx >= 0 && endIdx > startIdx) {
            String value = stdout.substring(startIdx + RESULT_START_MARKER.length(), endIdx).trim();
            return truncateOutput(value);
        }
        return null;
    }

    /**
     * 从标准输出中剔除返回值标记块，避免 output 与 returnValue 重复展示。
     */
    private String stripReturnValueBlock(String stdout) {
        if (stdout == null) return null;
        int startIdx = stdout.indexOf(RESULT_START_MARKER);
        int endIdx = stdout.indexOf(RESULT_END_MARKER);
        if (startIdx < 0 || endIdx <= startIdx) {
            return stdout;
        }
        int blockEndIdx = endIdx + RESULT_END_MARKER.length();
        String before = stdout.substring(0, startIdx).stripTrailing();
        String after = stdout.substring(blockEndIdx).stripLeading();
        if (before.isEmpty()) return after;
        if (after.isEmpty()) return before;
        return before + System.lineSeparator() + after;
    }

    private String findPython() {
        for (String cmd : PYTHON_CANDIDATES) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                int exitCode = p.waitFor();
                if (exitCode == 0 && output.contains("Python 3")) {
                    return cmd;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String checkSecurity(String code) {
        if (BLOCKED_IMPORTS.matcher(code).find()) {
            return "Python 安全校验未通过：包含不允许的模块（禁止 os/subprocess/socket/http/sys 等系统模块）";
        }
        return null;
    }

    private String readStream(InputStream is) {
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String escapeJson(Map<String, Object> params) {
        try {
            // 简单 JSON 序列化，避免引入 ObjectMapper 依赖
            StringBuilder json = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) json.append(",");
                first = false;
                json.append("\"").append(escapeStr(entry.getKey())).append("\":");
                Object val = entry.getValue();
                if (val == null) {
                    json.append("null");
                } else if (val instanceof Number || val instanceof Boolean) {
                    json.append(val);
                } else {
                    json.append("\"").append(escapeStr(String.valueOf(val))).append("\"");
                }
            }
            json.append("}");
            return json.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private String escapeStr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
