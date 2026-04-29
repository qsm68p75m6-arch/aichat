package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * 设计说明：
 * - 核心线程数 5：常驻线程，处理常规并发量
 * - 最大线程数 20：突发流量时可扩展到20个
 * - 队列容量 100：超出核心线程数的任务排队等待
 * - 拒绝策略 CallerRuns：队列满时由调用线程执行，避免直接丢弃
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * AI 流式调用专用线程池
     *
     * 为什么单独建？
     * - AI 调用耗时较长(10~60s)，不能阻塞 Tomcat 的 IO 线程
     * - 与其他异步任务隔离，AI 调用堆积不影响其他功能
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-async-");
        // 拒绝策略：队列满了让提交任务的线程自己跑（降级而非丢弃）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(60);
        executor.initialize();
        return executor;
    }
}
