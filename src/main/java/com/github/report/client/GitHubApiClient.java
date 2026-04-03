package com.github.report.client;

import com.github.report.exception.GitHubApiException;
import com.github.report.model.Collaborator;
import com.github.report.model.Repository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class GitHubApiClient {

    private final RestTemplate restTemplate;

    @Value("${github.api.base-url}")
    private String baseUrl;

    @Value("${github.api.per-page}")
    private int perPage;

    public GitHubApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Fetch ALL repositories in an org (handles pagination) ──────────────
    public List<Repository> getOrgRepositories(String orgName) {
        List<Repository> allRepos = new ArrayList<>();
        int page = 1;

        log.debug("Fetching repositories for org: {}", orgName);

        while (true) {
            String url = String.format(
                    "%s/orgs/%s/repos?per_page=%d&page=%d&type=all",
                    baseUrl, orgName, perPage, page
            );

            try {
                ResponseEntity<Repository[]> response = restTemplate.getForEntity(
                        url, Repository[].class
                );

                Repository[] repos = response.getBody();

                // No more pages when response is empty
                if (repos == null || repos.length == 0) break;

                allRepos.addAll(Arrays.asList(repos));
                log.debug("Fetched page {} → {} repos so far", page, allRepos.size());

                // If we got less than perPage, this is the last page
                if (repos.length < perPage) break;

                page++;

            } catch (HttpClientErrorException e) {
                handleHttpError(e, "fetching repositories for org: " + orgName);
            }
        }

        log.debug("Total repositories fetched: {}", allRepos.size());
        return allRepos;
    }

    // ── Fetch ALL collaborators for a single repo (handles pagination) ──────
    public List<Collaborator> getRepoCollaborators(String orgName, String repoName) {
        List<Collaborator> allCollaborators = new ArrayList<>();
        int page = 1;

        log.debug("Fetching collaborators for repo: {}/{}", orgName, repoName);

        while (true) {
            String url = String.format(
                    "%s/repos/%s/%s/collaborators?per_page=%d&page=%d&affiliation=all",
                    baseUrl, orgName, repoName, perPage, page
            );

            try {
                ResponseEntity<Collaborator[]> response = restTemplate.getForEntity(
                        url, Collaborator[].class
                );

                Collaborator[] collaborators = response.getBody();

                if (collaborators == null || collaborators.length == 0) break;

                allCollaborators.addAll(Arrays.asList(collaborators));

                if (collaborators.length < perPage) break;

                page++;

            } catch (HttpClientErrorException e) {
                // Some repos return 403 if you don't have access — skip them
                if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                    log.warn("No access to collaborators for repo: {}/{}, skipping", orgName, repoName);
                    return Collections.emptyList();
                }
                handleHttpError(e, "fetching collaborators for repo: " + repoName);
            }
        }

        return allCollaborators;
    }

    // ── Validate that the org actually exists ───────────────────────────────
    public void validateOrganization(String orgName) {
        String url = String.format("%s/orgs/%s", baseUrl, orgName);

        try {
            restTemplate.getForEntity(url, String.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new GitHubApiException(
                        "Organization not found: " + orgName,
                        HttpStatus.NOT_FOUND
                );
            }
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new GitHubApiException(
                        "Invalid GitHub token — check your GITHUB_TOKEN",
                        HttpStatus.UNAUTHORIZED
                );
            }
            handleHttpError(e, "validating org: " + orgName);
        }
    }

    // ── Central error handler ───────────────────────────────────────────────
    private void handleHttpError(HttpClientErrorException e, String context) {
        log.error("GitHub API error while {}: {} {}", context, e.getStatusCode(), e.getMessage());

        if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            throw new GitHubApiException(
                    "GitHub authentication failed — check your token",
                    HttpStatus.UNAUTHORIZED,
                    e.getMessage()
            );
        }
        if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
            throw new GitHubApiException(
                    "GitHub API rate limit exceeded or insufficient permissions",
                    HttpStatus.FORBIDDEN,
                    e.getMessage()
            );
        }
        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new GitHubApiException(
                    "Resource not found on GitHub",
                    HttpStatus.NOT_FOUND,
                    e.getMessage()
            );
        }
        throw new GitHubApiException(
                "GitHub API error while " + context,
                HttpStatus.INTERNAL_SERVER_ERROR,
                e.getMessage()
        );
    }
}