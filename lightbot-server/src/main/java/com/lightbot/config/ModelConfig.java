package com.lightbot.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型配置
 * <p>手动创建 EmbeddingModel Bean（排除自动配置后需要手动声明）</p>
 *
 * @author finch
 * @since 2026-05-19
 */
@Configuration
public class ModelConfig {

    @Bean
    public EmbeddingModel embeddingModel(@Value("${spring.ai.dashscope.api-key}") String apiKey) {
        DashScopeApi api = DashScopeApi.builder()
                .apiKey(apiKey)
                .build();
        return new DashScopeEmbeddingModel(api);
    }
}
