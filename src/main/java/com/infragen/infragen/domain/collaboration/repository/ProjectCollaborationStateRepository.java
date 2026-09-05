package com.infragen.infragen.domain.collaboration.repository;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProjectCollaborationStateRepository
        extends JpaRepository<@NonNull ProjectCollaborationState, @NonNull Long> {
    /**
     * project의 collaboration version 상태를 쓰기 잠금과 함께 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return project collaboration 상태
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT state
            FROM ProjectCollaborationState state
            WHERE state.project.id = :projectId
            """)
    Optional<ProjectCollaborationState> findByProjectIdForUpdate(@Param("projectId") Long projectId);
}
