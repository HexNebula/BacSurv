package ma.bacsurv.web.config;

import org.springframework.beans.factory.annotation.Value;
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
    public Executor solverExecutor(
            @Value("${bacsurv.solver.threads:0}") int configuredThreads) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Runtime.getRuntime().availableProcessors();
        // half the machine by default: solving is CPU-bound and the box has
        // other work to do, such as serving the pages that poll the job
        int threads = configuredThreads > 0 ? configuredThreads : Math.max(1, cores / 2);
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("solver-");
        executor.initialize();
        return executor;
    }
}
