package com.sprint.sprint.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MaintainerConfigRepository extends JpaRepository<MaintainerConfig, String> {
}