package com.infragen.infragen.domain.project.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.infragen.infragen.domain.project.entity.ProjectHistory;

public interface ProjectHistoryRepository extends JpaRepository<ProjectHistory, Long> {
    List<ProjectHistory> findAllByProjectIdOrderByCreatedAtDesc(Long projectId);
    
    Optional<ProjectHistory> findByIdAndProjectId(Long id, Long projectId);

    long countByProjectId(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectHistory ph WHERE ph.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
