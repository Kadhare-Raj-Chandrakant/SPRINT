package com.sprint.sprint.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullRequestContext {
    private String owner;
    private String repo;
    private int prNumber;
    private String title;
    private String description;
    private String authorLogin;
    private String baseRef;
    private String headRef;
    private String commitSha;
    private long installationId;
    private List<String> filesChanged;
    private TriageResult triageResult;
}
