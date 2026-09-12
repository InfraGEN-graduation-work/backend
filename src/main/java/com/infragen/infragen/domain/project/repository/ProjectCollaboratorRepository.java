package com.infragen.infragen.domain.project.repository;

import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectCollaboratorRepository
        extends JpaRepository<@NonNull ProjectCollaborator, @NonNull Long> {
    /**
     * 프로젝트 삭제 transaction에서 대상 project의 membership만 일괄 삭제한다. 회원은 삭제하지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaborator collaborator WHERE collaborator.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

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
