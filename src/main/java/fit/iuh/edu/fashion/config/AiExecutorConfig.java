package fit.iuh.edu.fashion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class AiExecutorConfig {

    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    public ExecutorService aiExecutor() {
        int corePoolSize = 5; // reasonable default; adjust based on server capacity
        int maxPoolSize = 10;
        long keepAliveSeconds = 60L;
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(50); // bounded queue

        ThreadFactory threadFactory = runnable -> {
            Thread t = new Thread(runnable);
            t.setName("ai-exec-" + t.getId());
            t.setDaemon(false);
            return t;
        };

        // Use CallerRunsPolicy to provide simple backpressure when queue is full
        RejectedExecutionHandler handler = new ThreadPoolExecutor.CallerRunsPolicy();

        return new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveSeconds, TimeUnit.SECONDS, queue, threadFactory, handler);
    }
}
