package com.bot.bot.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrAnalysisRepositoryTest {

    @Mock
    private PrAnalysisRepository repository;

    private PrAnalysis createAnalysis(String owner, String repo, int prNumber, String commitSha, Instant createdAt) {
        PrAnalysis a = new PrAnalysis();
        a.setOwner(owner);
        a.setRepo(repo);
        a.setPrNumber(prNumber);
        a.setCommitSha(commitSha);
        a.setTier("critical");
        a.setSecurityFlag(true);
        a.setSummary("Test summary");
        a.setFindingsJson("[]");
        a.setStatus("completed");
        a.setInstallationId("inst-1");
        a.setCreatedAt(createdAt);
        return a;
    }

    @Test
    void saveAndFindLatestReturnsRow() {
        PrAnalysis saved = createAnalysis("owner1", "repo1", 1, "abc123", Instant.now());
        when(repository.save(saved)).thenReturn(saved);
        when(repository.findLatest("owner1", "repo1", 1)).thenReturn(Optional.of(saved));

        PrAnalysis persisted = repository.save(saved);
        Optional<PrAnalysis> found = repository.findLatest("owner1", "repo1", 1);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(persisted.getId());
        assertThat(found.get().getCommitSha()).isEqualTo("abc123");
    }

    @Test
    void findLatestReturnsNewestByCreatedAt() throws Exception {
        Instant old = Instant.now();
        Thread.sleep(10);
        Instant recent = Instant.now();

        PrAnalysis oldAnalysis = createAnalysis("owner2", "repo2", 2, "old-sha", old);
        PrAnalysis newAnalysis = createAnalysis("owner2", "repo2", 2, "new-sha", recent);

        when(repository.findLatest("owner2", "repo2", 2)).thenReturn(Optional.of(newAnalysis));

        Optional<PrAnalysis> found = repository.findLatest("owner2", "repo2", 2);
        assertThat(found).isPresent();
        assertThat(found.get().getCommitSha()).isEqualTo("new-sha");
    }

    @Test
    void findLatestReturnsEmptyWhenNoMatch() {
        when(repository.findLatest("nonexistent", "nope", 999)).thenReturn(Optional.empty());

        Optional<PrAnalysis> found = repository.findLatest("nonexistent", "nope", 999);
        assertThat(found).isEmpty();
    }

    @Test
    void existsByOwnerRepoPrShaReturnsTrueForExisting() {
        when(repository.existsByOwnerRepoPrSha("owner3", "repo3", 3, "sha-exists")).thenReturn(true);

        boolean exists = repository.existsByOwnerRepoPrSha("owner3", "repo3", 3, "sha-exists");
        assertThat(exists).isTrue();
    }

    @Test
    void existsByOwnerRepoPrShaReturnsFalseForNonExisting() {
        when(repository.existsByOwnerRepoPrSha("no", "way", 0, "nope")).thenReturn(false);

        boolean exists = repository.existsByOwnerRepoPrSha("no", "way", 0, "nope");
        assertThat(exists).isFalse();
    }
}
