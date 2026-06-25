package com.lightbot.service.sandbox;

import com.lightbot.dto.CodeExecResult;

import java.util.Map;

/**
 * 代码执行引擎接口
 * <p>每种语言实现一个引擎，通过 {@link EngineRegistry} 按语言路由。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
public interface CodeEngine {

    /**
     * 支持的语言标识（如 "java"、"javascript"）
     */
    String language();

    /**
     * 引擎是否可用（依赖是否存在）
     */
    boolean isAvailable();

    /**
     * 执行代码
     *
     * @param code      代码内容
     * @param params    传入参数（代码中可通过 params 访问）
     * @param timeoutMs 超时时间（毫秒）
     * @return 执行结果
     */
    CodeExecResult execute(String code, Map<String, Object> params, long timeoutMs);
}
