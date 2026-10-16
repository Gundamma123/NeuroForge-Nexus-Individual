package com.neuroforge.nexus.repository;

import com.neuroforge.nexus.entity.ProjectGithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectGithubRepositoryRepository extends JpaRepository<ProjectGithubRepository, Long> {
    Optional<ProjectGithubRepository> findByProjectId(Long projectId);
    // inside the interface, next to findByProjectId
    void deleteByProjectId(Long projectId);
}