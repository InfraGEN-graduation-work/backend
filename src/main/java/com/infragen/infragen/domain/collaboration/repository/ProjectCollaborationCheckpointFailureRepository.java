package com.infragen.infragen.domain.collaboration.repository;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;

public interface ProjectCollaborationCheckpointFailureRepository
        extends JpaRepository<@NonNull ProjectCollaborationCheckpointFailure, @NonNull Long> {
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
