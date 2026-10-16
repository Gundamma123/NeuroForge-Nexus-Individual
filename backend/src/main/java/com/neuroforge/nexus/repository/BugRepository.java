package com.neuroforge.nexus.repository;

import com.neuroforge.nexus.entity.Bug;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;






public interface BugRepository extends JpaRepository<Bug, Long> {

    // JOINs pull in the project and user names; unused filters are passed as null.
    @Query("""
            SELECT b FROM Bug b
            JOIN FETCH b.project p
            JOIN FETCH b.reportedBy r
            LEFT JOIN FETCH b.assignedTo a
            WHERE (:status IS NULL OR b.status = :status)
              AND (:severity IS NULL OR b.severity = :severity)
              AND (:projectId IS NULL OR p.id = :projectId)
            ORDER BY b.createdAt DESC, b.id DESC
            """)
    List<Bug> search(@Param("status") String status,
                     @Param("severity") String severity,
                     @Param("projectId") Long projectId);

    @Query("""
            SELECT b FROM Bug b
            JOIN FETCH b.project
            JOIN FETCH b.reportedBy
            LEFT JOIN FETCH b.assignedTo
            WHERE b.id = :id
            """)
    Optional<Bug> findDetailedById(@Param("id") Long id);

    long countByStatus(String status);

    long countByStatusNot(String status);

    long countBySeverityAndStatusNot(String severity, String status);

    // Used when a project is deleted, so its bugs don't block the foreign key.
    @Transactional
    @Modifying
    @Query("DELETE FROM Bug b WHERE b.project.id = :projectId")
    void deleteAllForProject(@Param("projectId") Long projectId);


        List<Bug> findByProject_Id(Long projectId);
}