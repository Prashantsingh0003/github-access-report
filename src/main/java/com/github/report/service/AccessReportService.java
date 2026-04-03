package com.github.report.service;

import com.github.report.client.GitHubApiClient;
import com.github.report.model.AccessReport;
import com.github.report.model.Collaborator;
import com.github.report.model.Repository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AccessReportService {

    private final GitHubApiClient gitHubApiClient;
    private final Executor executor;

    public AccessReportService(
            GitHubApiClient gitHubApiClient,
            @Qualifier("githubTaskExecutor") Executor executor
    ) {
        this.gitHubApiClient = gitHubApiClient;
        this.executor = executor;
    }

    // ── Main method — cached for 10 mins so repeated calls don't hit GitHub ──
    @Cacheable(value = "accessReports", key = "#orgName")
    public AccessReport generateReport(String orgName) {
        log.info("Generating access report for org: {}", orgName);
        long startTime = System.currentTimeMillis();

        // Step 1 — validate org exists and token works
        gitHubApiClient.validateOrganization(orgName);

        // Step 2 — fetch all repositories
        List<Repository> repositories = gitHubApiClient.getOrgRepositories(orgName);
        log.info("Found {} repositories in org: {}", repositories.size(), orgName);

        if (repositories.isEmpty()) {
            return buildEmptyReport(orgName);
        }

        // Step 3 — fetch collaborators for ALL repos in parallel
        Map<Repository, List<Collaborator>> repoCollaboratorsMap =
                fetchAllCollaboratorsInParallel(repositories, orgName);

        // Step 4 — aggregate: flip from "repo → users" to "user → repos"
        List<AccessReport.UserAccess> userAccessList =
                aggregateByUser(repoCollaboratorsMap);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Report generated in {}ms — {} users, {} repos",
                elapsed, userAccessList.size(), repositories.size());

        // Step 5 — build and return final report
        return AccessReport.builder()
                .organization(orgName)
                .generatedAt(Instant.now())
                .totalRepositories(repositories.size())
                .totalUsers(userAccessList.size())
                .report(userAccessList)
                .build();
    }

    // ── Fetch collaborators for all repos in PARALLEL ──────────────────────
    private Map<Repository, List<Collaborator>> fetchAllCollaboratorsInParallel(
            List<Repository> repositories,
            String orgName
    ) {
        log.debug("Fetching collaborators for {} repos in parallel", repositories.size());

        // Create one CompletableFuture per repo — all run simultaneously
        List<CompletableFuture<AbstractMap.SimpleEntry<Repository, List<Collaborator>>>> futures =
                repositories.stream()
                        .map(repo -> CompletableFuture
                                .supplyAsync(() -> {
                                    List<Collaborator> collaborators =
                                            gitHubApiClient.getRepoCollaborators(orgName, repo.getName());
                                    return new AbstractMap.SimpleEntry<>(repo, collaborators);
                                }, executor)
                                .exceptionally(ex -> {
                                    // If one repo fails, log it and return empty — don't crash everything
                                    log.warn("Failed to fetch collaborators for repo: {} — {}",
                                            repo.getName(), ex.getMessage());
                                    return new AbstractMap.SimpleEntry<>(repo, Collections.emptyList());
                                })
                        )
                        .collect(Collectors.toList());

        // Wait for ALL futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Collect results into a map
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    // ── Flip "repo → users" into "user → repos" ────────────────────────────
    private List<AccessReport.UserAccess> aggregateByUser(
            Map<Repository, List<Collaborator>> repoCollaboratorsMap
    ) {
        // username → list of RepoPermission
        Map<String, List<AccessReport.RepoPermission>> userRepoMap = new HashMap<>();
        Map<String, String> userTypeMap = new HashMap<>();

        for (Map.Entry<Repository, List<Collaborator>> entry : repoCollaboratorsMap.entrySet()) {
            Repository repo = entry.getKey();
            List<Collaborator> collaborators = entry.getValue();

            for (Collaborator collaborator : collaborators) {
                String username = collaborator.getLogin();

                // Build the repo permission entry for this user
                AccessReport.RepoPermission repoPermission = AccessReport.RepoPermission.builder()
                        .name(repo.getName())
                        .fullName(repo.getFullName())
                        .isPrivate(repo.isPrivate())
                        .permission(
                                collaborator.getPermissions() != null
                                        ? collaborator.getPermissions().getHighestPermission()
                                        : "read"
                        )
                        .build();

                // Group by username
                userRepoMap
                        .computeIfAbsent(username, k -> new ArrayList<>())
                        .add(repoPermission);

                // Remember user type (User vs Bot)
                userTypeMap.putIfAbsent(username, collaborator.getType());
            }
        }

        // Convert map into sorted list of UserAccess objects
        return userRepoMap.entrySet().stream()
                .map(entry -> {
                    List<AccessReport.RepoPermission> repos = entry.getValue();
                    // Sort repos alphabetically per user
                    repos.sort(Comparator.comparing(AccessReport.RepoPermission::getName));

                    return AccessReport.UserAccess.builder()
                            .username(entry.getKey())
                            .type(userTypeMap.getOrDefault(entry.getKey(), "User"))
                            .repositoryCount(repos.size())
                            .repositories(repos)
                            .build();
                })
                // Sort users alphabetically
                .sorted(Comparator.comparing(AccessReport.UserAccess::getUsername))
                .collect(Collectors.toList());
    }

    // ── Empty report when org has no repos ─────────────────────────────────
    private AccessReport buildEmptyReport(String orgName) {
        return AccessReport.builder()
                .organization(orgName)
                .generatedAt(Instant.now())
                .totalRepositories(0)
                .totalUsers(0)
                .report(Collections.emptyList())
                .build();
    }
}