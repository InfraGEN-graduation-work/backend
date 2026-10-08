package com.infragen.infragen.domain.collaboration.repository;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectCollaborationOperationRepository
        extends JpaRepository<@NonNull ProjectCollaborationOperation, @NonNull Long> {
    /**
     * 프로젝트 삭제 transaction에서 version 보존 조건 없이 대상 project의 operation log를 일괄 삭제한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborationOperation operation WHERE operation.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    /**
     * 지정한 snapshot version 이하의 operation log를 삭제한다.
     *
     * @param projectId 삭제할 project 식별자
     * @param serverVersion snapshot으로 보존되는 마지막 version
     * @return 삭제된 operation 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ProjectCollaborationOperation operation
            WHERE operation.project.id = :projectId
              AND operation.serverVersion <= :serverVersion
            """)
    int deleteAllByProjectIdAndServerVersionLessThanEqual(
            @Param("projectId") Long projectId,
            @Param("serverVersion") Long serverVersion
    );

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

    /**
     * project의 operation log 중 serverVersion이 가장 작은 한 건을 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return 가장 오래된 operation log, log가 없으면 빈 값
     */
    Optional<ProjectCollaborationOperation> findFirstByProjectIdOrderByServerVersionAsc(Long projectId);

    /**
     * project의 operation log 중 serverVersion이 가장 큰 한 건을 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return 가장 최신 operation log, log가 없으면 빈 값
     */
    Optional<ProjectCollaborationOperation> findFirstByProjectIdOrderByServerVersionDesc(Long projectId);

    /**
     * {@code afterVersion < serverVersion <= untilVersion} 구간의 operation log를 오름차순으로 조회한다.
     *
     * <p>하한은 제외하고 상한은 포함한다. 상한은 호출자가 읽은 state version이며, 그 뒤에 저장된 log가
     * 같은 응답에 섞이지 않게 한다.
     *
     * @param projectId 조회할 project 식별자
     * @param afterVersion client가 이미 가진 version (제외)
     * @param untilVersion 응답 기준 serverVersion (포함)
     * @return 구간 안의 operation log 목록
     */
    @Query("""
            SELECT operation FROM ProjectCollaborationOperation operation
            WHERE operation.project.id = :projectId
              AND operation.serverVersion > :afterVersion
              AND operation.serverVersion <= :untilVersion
            ORDER BY operation.serverVersion ASC
            """)
    List<ProjectCollaborationOperation> findAllInVersionRange(
            @Param("projectId") Long projectId,
            @Param("afterVersion") Long afterVersion,
            @Param("untilVersion") Long untilVersion
    );
}
