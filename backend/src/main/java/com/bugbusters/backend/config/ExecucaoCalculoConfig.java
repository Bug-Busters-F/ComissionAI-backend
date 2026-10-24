package com.bugbusters.backend.config;

import org.springframework.context.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecucaoCalculoConfig {
    @Bean("execucaoCalculoExecutor")
    TaskExecutor execucaoCalculoExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("calculo-competencia-"); executor.initialize();
        return executor;
    }
}
