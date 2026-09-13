package com.infragen.infragen.domain.collaboration.repository;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;

public interface ProjectCollaborationCheckpointFailureRepository
        extends JpaRepository<@NonNull ProjectCollaborationCheckpointFailure, @NonNull Long> {
    /**
     * 프로젝트 삭제 transaction에서 대상 project의 checkpoint 실패 기록을 일괄 삭제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborationCheckpointFailure failure WHERE failure.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    Optional<ProjectCollaborationCheckpointFailure> findByProjectIdAndServerVersion(
            Long projectId,
            Long serverVersion
    );

    /**
     * checkpoint failure를 오래된 순서대로 100개 조회한다.
     * @return
     */
    List<ProjectCollaborationCheckpointFailure> findTop100ByOrderByCreatedAtAsc();
}
