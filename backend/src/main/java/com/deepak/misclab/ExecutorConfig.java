package com.deepak.misclab;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {
    /**
     * One virtual thread per task for blocking I/O (AWS SDK, Mongo, FX HTTP calls). Shared by the batch FX fan-out and
     * the parallel receipt writes; closed on shutdown so in-flight writes finish.
     */
    @Bean(destroyMethod = "close")
    ExecutorService receiptIoExecutor() { return Executors.newVirtualThreadPerTaskExecutor(); }
}
