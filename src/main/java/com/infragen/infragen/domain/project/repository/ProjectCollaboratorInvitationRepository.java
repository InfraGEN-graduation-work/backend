package com.infragen.infragen.domain.project.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Tuple;

public interface ProjectCollaboratorInvitationRepository
        extends JpaRepository<@NonNull ProjectCollaboratorInvitation, @NonNull Long> {

    /** 프로젝트 삭제 transaction에서 연결된 초대 레코드를 먼저 정리한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaboratorInvitation invitation WHERE invitation.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

    /** 회원 탈퇴 transaction에서 관련된 초대 레코드를 먼저 정리한다. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            DELETE FROM project_collaborator_invitation
            WHERE invitee_member_id = :memberId
               OR invited_by_member_id = :memberId
               OR responded_by_member_id = :memberId
            """, nativeQuery = true)
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    /** 발신자 또는 대상인 PENDING 초대의 project ID만 반환한다. 만료된 대기도 포함하며 entity는 적재하지 않는다. */
    @Query("""
            SELECT DISTINCT invitation.project.id
            FROM ProjectCollaboratorInvitation invitation
            WHERE invitation.status = com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus.PENDING
              AND (invitation.invitedBy.id = :memberId OR invitation.invitee.id = :memberId)
            ORDER BY invitation.project.id ASC
            """)
    List<Long> findPendingRelatedProjectIdsOrderByProjectIdAsc(@Param("memberId") Long memberId);

    /** project 선잠금 뒤 관련 PENDING 초대를 잠근다. 완료 이력은 반환하지 않고 만료된 대기는 포함한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM ProjectCollaboratorInvitation invitation
            WHERE invitation.project.id = :projectId
              AND invitation.status = com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus.PENDING
              AND (invitation.invitedBy.id = :memberId OR invitation.invitee.id = :memberId)
            ORDER BY invitation.id ASC
            """)
    List<ProjectCollaboratorInvitation> findPendingRelatedByProjectIdAndMemberIdForUpdate(
            @Param("projectId") Long projectId,
            @Param("memberId") Long memberId
    );

    /**
     * 프로젝트의 발신 초대를 최신순으로 조회한다.
     */
    @EntityGraph(attributePaths = "invitee")
    List<ProjectCollaboratorInvitation> findAllByProjectIdOrderByCreatedAtDescIdDesc(
            Long projectId
    );

    /**
     * 회원에게 도착한 초대를 최신순으로 조회한다.
     */
    @EntityGraph(attributePaths = {"project", "invitedBy"})
    List<ProjectCollaboratorInvitation> findAllByInviteeIdOrderByCreatedAtDescIdDesc(
            Long inviteeId
    );

    /**
     * 탈퇴한 대상도 포함해 프로젝트 발신 이력을 최신순·ID 역순으로 조회한다.
     * 회원 Entity를 적재하지 않으며 비활성 회원의 표시명만 마스킹한다. 호출자가 현재 owner를 확인한다.
     */
    @Query(value = """
            SELECT invitation.id AS invitationId,
                   CASE WHEN invitee.is_active = true THEN invitee.nickname
                        ELSE '탈퇴회원' END AS memberNickname,
                   invitation.role AS role,
                   invitation.status AS status,
                   invitation.created_at AS createdAt,
                   invitation.expires_at AS expiresAt,
                   invitation.responded_at AS respondedAt
            FROM project_collaborator_invitation invitation
            JOIN member invitee ON invitee.id = invitation.invitee_member_id
            WHERE invitation.project_id = :projectId
            ORDER BY invitation.created_at DESC, invitation.id DESC
            """, nativeQuery = true)
    List<Tuple> findSentHistoryByProjectId(@Param("projectId") Long projectId);

    /**
     * 지정 회원에게 온 이력을 최신순·ID 역순으로 조회하며 탈퇴한 초대자의 표시명만 마스킹한다.
     * 초대자·응답자 Entity를 적재하지 않는다. inviteeId에는 현재 인증 회원의 ID를 전달한다.
     */
    @Query(value = """
            SELECT invitation.id AS invitationId,
                   project.title AS projectTitle,
                   CASE WHEN inviter.is_active = true THEN inviter.nickname
                        ELSE '탈퇴회원' END AS memberNickname,
                   invitation.role AS role,
                   invitation.status AS status,
                   invitation.created_at AS createdAt,
                   invitation.expires_at AS expiresAt
            FROM project_collaborator_invitation invitation
            JOIN project project ON project.id = invitation.project_id
            JOIN member inviter ON inviter.id = invitation.invited_by_member_id
            WHERE invitation.invitee_member_id = :inviteeId
            ORDER BY invitation.created_at DESC, invitation.id DESC
            """, nativeQuery = true)
    List<Tuple> findReceivedHistoryByInviteeId(@Param("inviteeId") Long inviteeId);

    /**
     * 같은 프로젝트·초대 대상에 만료되지 않은 PENDING 초대가 있는지 확인한다.
     */
    boolean existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
            Long projectId,
            Long inviteeId,
            ProjectCollaboratorInvitationStatus status,
            LocalDateTime currentTime
    );

    /** 초대 객체를 미리 적재하지 않고 응답자의 project 잠금 대상을 찾는다. 상태는 잠금 후 재검증한다. */
    @Query("""
            SELECT invitation.project.id
            FROM ProjectCollaboratorInvitation invitation
            WHERE invitation.id = :invitationId
              AND invitation.invitee.id = :inviteeId
            """)
    Optional<Long> findProjectIdByIdAndInviteeId(
            @Param("invitationId") Long invitationId,
            @Param("inviteeId") Long inviteeId
    );

    /**
     * 활성 응답 대상 회원의 초대를 잠근 상태로 조회한다. 응답 경로는 project를 먼저 잠근다.
     * PENDING 응답 경쟁을 직렬화하도록 호출 transaction이 끝날 때까지 row lock을 유지한다.
     *
     * @param invitationId 조회할 초대 식별자
     * @param inviteeId 응답 권한을 확인할 회원 식별자
     * @return 해당 회원에게 온 초대가 있으면 반환
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT invitation
            FROM ProjectCollaboratorInvitation invitation
            JOIN invitation.invitee invitee
            WHERE invitation.id = :invitationId
              AND invitee.id = :inviteeId
              AND invitee.isActive = true
            """)
    Optional<ProjectCollaboratorInvitation> findByIdAndInviteeIdForUpdate(
            @Param("invitationId") Long invitationId,
            @Param("inviteeId") Long inviteeId
    );
}
