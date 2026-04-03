package com.github.report.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessReport {

    private String organization;
    private Instant generatedAt;
    private int totalRepositories;
    private int totalUsers;
    private List<UserAccess> report;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAccess {
        private String username;
        private String type;              // "User" or "Bot"
        private int repositoryCount;
        private List<RepoPermission> repositories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RepoPermission {
        private String name;
        private String fullName;
        private boolean isPrivate;
        private String permission;        // "admin", "write", "read" etc.
    }
}