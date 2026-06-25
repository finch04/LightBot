package com.lightbot.service.sandbox;

import com.lightbot.common.BizException;
import com.lightbot.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码执行引擎注册表
 * <p>按语言查找可用引擎，自动降级。同语言多个引擎时按注入顺序选首选。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
public class EngineRegistry {

    private final Map<String, List<CodeEngine>> engines = new LinkedHashMap<>();

    public EngineRegistry(List<CodeEngine> allEngines) {
        for (CodeEngine engine : allEngines) {
            String lang = normalizeLanguage(engine.language());
            engines.computeIfAbsent(lang, k -> new ArrayList<>()).add(engine);
        }
        log.info("[EngineRegistry] 注册引擎: {}", engines.keySet());
    }

    /**
     * 获取指定语言的可用引擎（优先返回排在前面的）
     *
     * @param language 编程语言（null 默认 java）
     * @return 可用引擎
     * @throws BizException 无可用引擎时抛出
     */
    public CodeEngine resolve(String language) {
        String lang = normalizeLanguage(language);
        List<CodeEngine> candidates = engines.getOrDefault(lang, List.of());
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.SANDBOX_ENGINE_NOT_FOUND,
                    "不支持的编程语言: " + language + "（目前支持: " + String.join(", ", availableLanguages()) + "）");
        }
        return candidates.stream()
                .filter(CodeEngine::isAvailable)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.SANDBOX_ENGINE_NOT_FOUND,
                        buildEnvErrorMessage(lang)));
    }

    /**
     * 构建环境不可用的详细错误信息
     */
    private String buildEnvErrorMessage(String lang) {
        return switch (lang) {
            case "python" -> "Python 执行环境不可用：服务器未安装 Python 3，请联系管理员安装 Python 3.8+ 后重试";
            case "javascript" -> "JavaScript 执行环境不可用：JVM 未包含 Nashorn 引擎，请确认使用 JDK 内置 Nashorn 或引入 org.openjdk.nashorn:nashorn-core 依赖";
            case "java" -> "Java 执行环境不可用：Janino 编译器未正确加载，请确认 org.codehaus.janino:janino 依赖已引入";
            default -> "「" + lang + "」执行环境不可用，请联系管理员检查运行环境";
        };
    }

    /**
     * 获取所有可用语言列表
     */
    public List<String> availableLanguages() {
        List<String> result = new ArrayList<>();
        for (var entry : engines.entrySet()) {
            if (entry.getValue().stream().anyMatch(CodeEngine::isAvailable)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private String normalizeLanguage(String lang) {
        if (lang == null || lang.isBlank()) return "java";
        return switch (lang.toLowerCase()) {
            case "js", "javascript", "ecmascript" -> "javascript";
            case "py", "python" -> "python";
            case "java" -> "java";
            default -> lang.toLowerCase();
        };
    }
}
