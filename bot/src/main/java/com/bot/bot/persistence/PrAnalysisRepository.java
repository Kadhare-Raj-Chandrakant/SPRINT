package com.bot.bot.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrAnalysisRepository extends JpaRepository<PrAnalysis, Long> {

    @Query("SELECT p FROM PrAnalysis p WHERE p.owner = :owner AND p.repo = :repo AND p.prNumber = :prNumber ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PrAnalysis> findLatest(@Param("owner") String owner, @Param("repo") String repo, @Param("prNumber") int prNumber);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM PrAnalysis p WHERE p.owner = :owner AND p.repo = :repo AND p.prNumber = :prNumber AND p.commitSha = :commitSha")
    boolean existsByOwnerRepoPrSha(@Param("owner") String owner, @Param("repo") String repo, @Param("prNumber") int prNumber, @Param("commitSha") String commitSha);

    List<PrAnalysis> findByCreatedAtAfter(@Param("since") Instant since);
}
