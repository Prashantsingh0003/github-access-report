package com.github.report;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "com.github.report")
@EnableCaching
@EnableAsync
public class GithubAccessReportApplication {

	public static void main(String[] args) {
		SpringApplication.run(GithubAccessReportApplication.class, args);
		System.out.println("Application is running...");
	}
}