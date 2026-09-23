package com.infragen.infragen.domain.project.repository;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.repository.projection.ProjectAccessPreview;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    /**
     * 최초 collaboration state 생성을 위해 project row를 쓰기 잠금과 함께 조회한다.
     *
     * @param projectId 잠글 project 식별자
     * @return project가 존재하면 반환
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT project
            FROM Project project
            WHERE project.id = :projectId
            """)
    Optional<Project> findByIdForUpdate(@Param("projectId") Long projectId);

    /**
     * owner와 collaborator 프로젝트를 최신순으로 조회한다.
     * 현재 회원만 조인하고 project·member unique 제약으로 프로젝트당 최대 한 행을 반환한다.
     */
    @Query("""
            SELECT project.id AS projectId, project.title AS title,
                   project.description AS description, project.status AS status,
                   project.createdAt AS createdAt,
                   CASE WHEN project.member.id = :memberId THEN 'OWNER'
                        WHEN collaborator.role = com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole.EDITOR
                            THEN 'EDITOR'
                        ELSE 'VIEWER' END AS accessRole
            FROM Project project
            LEFT JOIN ProjectCollaborator collaborator
                ON collaborator.project.id = project.id AND collaborator.member.id = :memberId
            WHERE project.member.id = :memberId OR collaborator.id IS NOT NULL
            ORDER BY project.createdAt DESC, project.id DESC
            """)
    List<ProjectAccessPreview> findAllAccessibleByMemberId(@Param("memberId") Long memberId);

    boolean existsByIdAndMemberId(Long id, Long memberId);
    
    // 식별자와 소유자 ID 기반 단일 조회
    Optional<Project> findByIdAndMemberId(Long id, Long memberId);
}
