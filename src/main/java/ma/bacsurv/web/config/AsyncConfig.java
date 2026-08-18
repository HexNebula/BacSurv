package ma.bacsurv.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Solving is CPU-bound: a small bounded pool keeps one machine responsive.
 * Queued jobs simply wait, which is the same behaviour a remote worker
 * queue would give later.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("solverExecutor")
    public Executor solverExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(Math.max(1, cores / 2));
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("solver-");
        executor.initialize();
        return executor;
    }
}
