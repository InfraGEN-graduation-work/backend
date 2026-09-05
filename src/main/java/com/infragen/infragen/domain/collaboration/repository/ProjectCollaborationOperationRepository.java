package com.infragen.infragen.domain.collaboration.repository;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectCollaborationOperationRepository
        extends JpaRepository<@NonNull ProjectCollaborationOperation, @NonNull Long> {
    /**
     * project 안에서 operationId에 해당하는 log를 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @param operationId 중복 여부를 확인할 operation 식별자
     * @return 기존 operation log
     */
    Optional<ProjectCollaborationOperation> findByProjectIdAndOperationId(
            Long projectId,
            String operationId
    );

    /**
     * project의 operation log를 serverVersion 오름차순으로 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return project operation log 목록
     */
    List<ProjectCollaborationOperation> findAllByProjectIdOrderByServerVersionAsc(Long projectId);
}
