package com.github.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Collaborator {

    @JsonProperty("login")
    private String login;         // GitHub username

    @JsonProperty("id")
    private Long id;

    @JsonProperty("type")
    private String type;          // "User" or "Bot"

    @JsonProperty("permissions")
    private Permissions permissions;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Permissions {
        private boolean admin;
        private boolean maintain;
        private boolean push;
        private boolean triage;
        private boolean pull;

        // Returns the highest permission level as a readable string
        public String getHighestPermission() {
            if (admin)    return "admin";
            if (maintain) return "maintain";
            if (push)     return "write";
            if (triage)   return "triage";
            return "read";
        }
    }
}