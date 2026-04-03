package com.github.report.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;

@Configuration
@EnableCaching   // turns on Spring Cache
public class AppConfig {

    @Value("${github.api.token}")
    private String githubToken;

    @Value("${github.api.thread-pool-size:10}")
    private int threadPoolSize;

    // ── RestTemplate Bean ──────────────────────────────────────────
    // Pre-configured with GitHub auth header and timeouts
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5s to connect
        factory.setReadTimeout(15000);     // 15s to read response

        RestTemplate restTemplate = new RestTemplate(factory);

        // Add GitHub auth header to EVERY request automatically
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("Authorization", "Bearer " + githubToken);
            request.getHeaders().set("Accept", "application/vnd.github+json");
            request.getHeaders().set("X-GitHub-Api-Version", "2022-11-28");
            return execution.execute(request, body);
        });

        return restTemplate;
    }

    // ── Thread Pool Bean ───────────────────────────────────────────
    // Used by CompletableFuture for parallel GitHub API calls
    @Bean(name = "githubTaskExecutor")
    public Executor githubTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadPoolSize);
        executor.setMaxPoolSize(threadPoolSize * 2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("github-");   // easy to spot in logs
        executor.initialize();
        return executor;
    }
}