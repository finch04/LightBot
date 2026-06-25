package com.lightbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一线程池配置
 * <p>替代各 Service 中散落的 newCachedThreadPool / newFixedThreadPool，
 * 集中管理线程资源，防止高并发下线程数失控。</p>
 *
 * @author finch
 * @since 2026-06-24
 */
@Configuration
public class ThreadPoolConfig {

    /**
     * 共享有界线程池：RAG 检索、工具搜索等并发任务
     */
    @Bean(name = "lightBotExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor lightBotExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("lightbot-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
