package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * GraalVM Polyglot JavaScript 引擎（预留）
 * <p>需要添加 GraalVM 依赖和 Maven 仓库后才能启用。当前为占位实现，始终返回不可用。</p>
 * <p>启用步骤：</p>
 * <ol>
 *   <li>在 pom.xml 添加 GraalVM Maven 仓库</li>
 *   <li>添加 polyglot + js 依赖</li>
 *   <li>恢复此文件的 GraalVM 实现</li>
 * </ol>
 *
 * @author finch
 * @since 2026-06-24
 */
@Slf4j
@Component
public class GraalVmEngine implements CodeEngine {

    @Override
    public String language() {
        return "javascript";
    }

    @Override
    public boolean isAvailable() {
        // GraalVM 依赖未引入，始终不可用，自动降级到 NashornEngine
        return false;
    }

    @Override
    public CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs) {
        throw new UnsupportedOperationException("GraalVM 引擎未启用，请使用 Nashorn 引擎");
    }
}
