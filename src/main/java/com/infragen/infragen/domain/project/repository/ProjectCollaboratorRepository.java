package com.infragen.infragen.domain.project.repository;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;

public interface ProjectCollaboratorRepository
        extends JpaRepository<@NonNull ProjectCollaborator, @NonNull Long> {
    /**
     * 프로젝트 삭제 transaction에서 대상 project의 membership만 일괄 삭제한다. 회원은 삭제하지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborator collaborator WHERE collaborator.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    /**
     * 탈퇴 처리의 잠금 대상·잔여 관계 확인에 사용할 참여 project ID를 오름차순으로 반환한다.
     * 회원 Entity를 적재하거나 활성 상태로 범위를 줄이지 않는다.
     */
    @Query("""
            SELECT collaborator.project.id
            FROM ProjectCollaborator collaborator
            WHERE collaborator.member.id = :memberId
            ORDER BY collaborator.project.id ASC
            """)
    List<Long> findParticipatingProjectIdsOrderByProjectIdAsc(@Param("memberId") Long memberId);

    /**
     * 관련 project·회원 잠금을 확보한 탈퇴 transaction에서 해당 회원의 membership만 제거한다.
     * 미저장 변경을 flush한 뒤 캐시를 비우므로 호출자는 이후 필요한 Entity를 다시 조회한다.
     *
     * @return 제거한 membership 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborator collaborator WHERE collaborator.member.id = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    /**
     * project와 member가 가진 collaborator membership을 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @param memberId 조회할 member 식별자
     * @return 해당 membership이 있으면 반환
     */
    Optional<ProjectCollaborator> findByProjectIdAndMemberId(Long projectId, Long memberId);

    /**
     * project에 등록된 collaborator membership을 모두 조회한다.
     *
     * @param projectId 조회할 project 식별자
     * @return project collaborator 목록
     */
    List<ProjectCollaborator> findAllByProjectId(Long projectId);

    /** project 잠금 이후 활성 승계 후보를 조회하며, 후보별 회원 조회가 반복되지 않게 함께 가져온다. */
    @Query("""
            SELECT collaborator
            FROM ProjectCollaborator collaborator
            JOIN FETCH collaborator.member member
            WHERE collaborator.project.id = :projectId
              AND member.isActive = true
            """)
    List<ProjectCollaborator> findActiveSuccessionCandidatesByProjectId(
            @Param("projectId") Long projectId
    );

    /**
     * project에서 지정한 member의 collaborator membership을 삭제한다.
     *
     * @param projectId 삭제할 project 식별자
     * @param memberId 삭제할 collaborator member 식별자
     * @return 삭제된 membership 수
     */
    long deleteByProjectIdAndMemberId(Long projectId, Long memberId);

    /**
     * project와 member 사이의 collaborator membership 존재 여부를 확인한다.
     *
     * @param projectId 확인할 project 식별자
     * @param memberId 확인할 member 식별자
     * @return membership이 있으면 true
     */
    boolean existsByProjectIdAndMemberId(Long projectId, Long memberId);

    /**
     * project와 member가 지정한 collaborator 역할을 가지고 있는지 확인한다.
     *
     * @param projectId 확인할 project 식별자
     * @param memberId 확인할 member 식별자
     * @param role 확인할 collaborator 역할
     * @return 지정한 역할의 membership이 있으면 true
     */
    boolean existsByProjectIdAndMemberIdAndRole(
            Long projectId,
            Long memberId,
            ProjectCollaboratorRole role
    );
}
