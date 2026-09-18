package com.infragen.infragen.domain.project.repository;

import com.infragen.infragen.domain.project.entity.ProjectCollaboratorInvitation;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectCollaboratorInvitationRepository
        extends JpaRepository<@NonNull ProjectCollaboratorInvitation, @NonNull Long> {

    /** 프로젝트 삭제 transaction에서 연결된 초대 레코드를 먼저 정리한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectCollaboratorInvitation invitation WHERE invitation.project.id = :projectId")
    void deleteByProjectId(@Param("projectId") Long projectId);

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
     * 같은 프로젝트·초대 대상에 만료되지 않은 PENDING 초대가 있는지 확인한다.
     */
    boolean existsByProjectIdAndInviteeIdAndStatusAndExpiresAtAfter(
            Long projectId,
            Long inviteeId,
            ProjectCollaboratorInvitationStatus status,
            LocalDateTime currentTime
    );

    /**
     * 응답 대상 회원의 초대를 잠근 상태로 조회한다.
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
            WHERE invitation.id = :invitationId
              AND invitation.invitee.id = :inviteeId
            """)
    Optional<ProjectCollaboratorInvitation> findByIdAndInviteeIdForUpdate(
            @Param("invitationId") Long invitationId,
            @Param("inviteeId") Long inviteeId
    );
}
