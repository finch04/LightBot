package com.lightbot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * LLM 调用 WebClient 配置
 * <p>提供统一的 WebClient.Builder，Spring AI 内部使用 Reactor Netty 时自动复用连接池</p>
 *
 * @author finch
 * @since 2026-06-21
 */
@Slf4j
@Configuration
public class LlmConnectionPoolConfig {

    @Bean("llmWebClientBuilder")
    public WebClient.Builder llmWebClientBuilder() {
        log.info("[LLM连接池] WebClient.Builder 初始化完成");
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));
    }
}
