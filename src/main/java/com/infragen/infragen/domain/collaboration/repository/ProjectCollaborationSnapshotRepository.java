package com.infragen.infragen.domain.collaboration.repository;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectCollaborationSnapshotRepository
        extends JpaRepository<@NonNull ProjectCollaborationSnapshot, @NonNull Long> {
    /**
     * project에서 가장 최신 serverVersion의 snapshot을 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return 최신 snapshot이 있으면 반환
     */
    Optional<ProjectCollaborationSnapshot> findTopByProjectIdOrderByServerVersionDesc(Long projectId);

    /**
     * project의 특정 serverVersion snapshot을 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @param serverVersion 조회할 snapshot version
     * @return 해당 version의 snapshot이 있으면 반환
     */
    Optional<ProjectCollaborationSnapshot> findByProjectIdAndServerVersion(
            Long projectId,
            Long serverVersion
    );
}
