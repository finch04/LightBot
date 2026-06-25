package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.SandboxPathValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 统一沙盒服务实现
 * <p>代码执行路由到 {@link EngineRegistry}，文件操作委托 {@link MinioUtil} + {@link SandboxPathValidator}。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxServiceImpl implements SandboxService {

    private final EngineRegistry engineRegistry;
    private final MinioUtil minioUtil;

    @Override
    public CodeExecResult executeCode(String code, String language,
                                      Map<String, Object> params, Long timeoutMs) {
        long timeout = timeoutMs != null && timeoutMs > 0 ? timeoutMs : 5000L;
        CodeEngine engine = engineRegistry.resolve(language);

        log.info("[Sandbox] 执行代码: language={}, engine={}, timeout={}ms",
                engine.language(), engine.getClass().getSimpleName(), timeout);

        CodeExecResult result = engine.execute(code, params, timeout);

        log.info("[Sandbox] 执行完成: success={}, elapsed={}ms, outputLen={}",
                result.isSuccess(), result.getElapsedMs(),
                result.getOutput() != null ? result.getOutput().length() : 0);

        return result;
    }

    @Override
    public String readFile(String path) {
        String normalized = SandboxPathValidator.normalize(path);
        SandboxPathValidator.checkReadable(normalized);
        byte[] bytes = minioUtil.downloadBytes(normalized);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeFile(String path, String content) {
        String normalized = SandboxPathValidator.normalize(path);
        SandboxPathValidator.checkWritable(normalized);
        minioUtil.uploadString(content, normalized, "application/octet-stream");
    }

    @Override
    public List<String> listFiles(String dirPath) {
        String normalized = SandboxPathValidator.normalize(dirPath);
        SandboxPathValidator.checkReadable(normalized);
        return minioUtil.listObjects(normalized);
    }

    @Override
    public void deleteFile(String path) {
        String normalized = SandboxPathValidator.normalize(path);
        SandboxPathValidator.checkWritable(normalized);
        minioUtil.delete(normalized);
    }

    @Override
    public boolean fileExists(String path) {
        String normalized = SandboxPathValidator.normalize(path);
        SandboxPathValidator.checkReadable(normalized);
        return minioUtil.exists(normalized);
    }
}
