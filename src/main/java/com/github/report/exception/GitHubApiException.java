package com.github.report.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class GitHubApiException extends RuntimeException {

    private final HttpStatus status;
    private final String details;

    public GitHubApiException(String message, HttpStatus status, String details) {
        super(message);
        this.status = status;
        this.details = details;
    }

    public GitHubApiException(String message, HttpStatus status) {
        this(message, status, null);
    }
}