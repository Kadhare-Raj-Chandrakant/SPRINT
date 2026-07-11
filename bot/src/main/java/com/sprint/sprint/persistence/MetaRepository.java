package com.sprint.sprint.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaRepository extends JpaRepository<Meta, String> {
    List<Meta> findByKeyStartingWith(String prefix);
}
