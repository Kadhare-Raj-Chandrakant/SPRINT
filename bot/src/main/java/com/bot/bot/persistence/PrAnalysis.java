package com.bot.bot.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "pr_analysis")
@Data
public class PrAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repo;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(name = "commit_sha", nullable = false)
    private String commitSha;

    @Column(nullable = false)
    private String tier;

    @Column(name = "security_flag", nullable = false)
    private Boolean securityFlag;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Lob
    @Column(name = "findings_json", columnDefinition = "TEXT")
    private String findingsJson;

    @Column(nullable = false)
    private String status;

    @Column(name = "installation_id")
    private String installationId;

    @Column(name = "alerted", nullable = false)
    private Boolean alerted = false;

    @Column(name = "action_taken", nullable = false)
    private Boolean actionTaken = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
