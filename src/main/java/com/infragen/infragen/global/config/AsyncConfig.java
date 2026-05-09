package com.infragen.infragen.global.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${notification.async.core-pool-size}")
    private int corePoolSize;

    @Value("${notification.async.max-pool-size}")
    private int maxPoolSize;

    @Value("${notification.async.queue-capacity}")
    private int queueCapacity;

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("Mail-Async-");
        executor.initialize();
        return executor;
    }

    /*
     * 비동기 스레드 내에서 발생한 예외가 메인 스레드로 전파되지 않아서
     * 발생할 수 있는 Silent Failure을 방지하기 위한 로깅 핸들러
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) -> {
            log.error("비동기 메서드 실행 중 예외 발생 - Method: {}, Message: {}",
                    method.getName(), ex.getMessage(), ex);
        };
    }
}