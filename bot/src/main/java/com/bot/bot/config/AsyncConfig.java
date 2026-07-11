package com.bot.bot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Configures a dedicated thread pool for asynchronous PR review processing.
 * Prevents unbounded thread creation under webhook burst scenarios.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("reviewTaskExecutor")
    public Executor reviewTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("review-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Shared pool for parallel heuristic rule execution. Injected into
     * HeuristicsAnalysisEngine so we don't spin up a fresh thread pool per PR.
     */
    @Bean("heuristicExecutor")
    public ExecutorService heuristicExecutor() {
        return Executors.newFixedThreadPool(6);
    }
}
