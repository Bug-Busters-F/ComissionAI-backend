package com.bugbusters.backend.importbase.batch;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.configuration.support.MapJobRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.TaskExecutorJobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ImportBatchConfiguration {
    @Bean
    public Job importJob(JobRepository repository, Step importStep, ImportJobListener listener) {
        // The uploaded file is temporary; a failed import requires a new upload.
        return new JobBuilder("importJob", repository).preventRestart().listener(listener).start(importStep).build();
    }

    @Bean
    public Step importStep(JobRepository repository, PlatformTransactionManager transactionManager,
            ImportTasklet tasklet) {
        return new StepBuilder("importStep", repository).tasklet(tasklet, transactionManager).build();
    }

    @Bean
    public ThreadPoolTaskExecutor importTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Serialize imports to avoid races in resolve-or-create operations.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("import-batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }

    @Bean
    public TaskExecutorJobOperator importJobOperator(JobRepository repository,
            @Qualifier("importJob") Job job,
            @Qualifier("importTaskExecutor") ThreadPoolTaskExecutor executor) throws Exception {
        TaskExecutorJobOperator operator = new TaskExecutorJobOperator();
        MapJobRegistry registry = new MapJobRegistry();
        registry.register(job);
        operator.setJobRegistry(registry);
        operator.setJobRepository(repository);
        operator.setTaskExecutor(executor);
        return operator;
    }
}
