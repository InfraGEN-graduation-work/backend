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

    /**
     * version 발급을 위해 현재 project history 개수를 잠금 읽기로 조회한다.
     * LEFT JOIN으로 history가 없어도 project row는 반환되므로 history.id만 count한다.
     *
     * @param projectId 개수를 조회하고 잠글 project 식별자
     * @return 현재 project history 개수
     */
    @Query(value = """
        SELECT COUNT(history.id)
        FROM project project
        LEFT JOIN project_history history ON history.project_id = project.id
        WHERE project.id = :projectId
        FOR UPDATE OF project
        """, nativeQuery = true)
    long countByProjectIdForUpdate(@Param("projectId") Long projectId);

    long countByProjectId(Long projectId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectHistory ph WHERE ph.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);
}
