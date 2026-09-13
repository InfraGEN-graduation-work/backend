package com.infragen.infragen.domain.collaboration.repository;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;

import jakarta.persistence.LockModeType;

public interface ProjectCollaborationStateRepository
        extends JpaRepository<@NonNull ProjectCollaborationState, @NonNull Long> {
    /**
     * 프로젝트 삭제 transaction에서 대상 project의 version 상태를 삭제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborationState state WHERE state.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    /**
     * project의 collaboration state 존재 여부를 잠금 없이 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return collaboration state가 있으면 반환
     */
    Optional<ProjectCollaborationState> findByProjectId(Long projectId);

    /**
     * project의 collaboration version 상태를 쓰기 잠금과 함께 조회한다.
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
