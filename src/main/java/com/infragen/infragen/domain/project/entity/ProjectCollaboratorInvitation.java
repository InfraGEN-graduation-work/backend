package com.infragen.infragen.domain.project.entity;

import java.time.LocalDateTime;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorInvitationStatus;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트별 초대 대상, 역할, 만료 및 응답 상태를 저장한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_collaborator_invitation")
public class ProjectCollaboratorInvitation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_member_id", nullable = false)
    private Member invitedBy; // 초대한 회원을 저장한다.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_member_id", nullable = false)
    private Member invitee; // 초대받은 회원을 저장한다.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectCollaboratorRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectCollaboratorInvitationStatus status; // 초대 상태를 저장한다. (PENDING, ACCEPTED, DECLINED)

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt; // 초대가 만료되는 시각을 저장한다.

    @Column(name = "responded_at")
    private LocalDateTime respondedAt; // 초대에 대한 응답 시각을 저장한다. (수락 또는 거절 시각)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_member_id")
    private Member respondedBy; // 초대에 응답한 회원을 저장한다. (수락 또는 거절한 회원)

    /**
     * 프로젝트 초대 정보를 만들고 초기 상태를 PENDING으로 둔다.
     */
    @Builder
    public ProjectCollaboratorInvitation(
            Project project,
            Member invitedBy,
            Member invitee,
            ProjectCollaboratorRole role,
            LocalDateTime expiresAt
    ) {
        this.project = project;
        this.invitedBy = invitedBy;
        this.invitee = invitee;
        this.role = role;
        this.status = ProjectCollaboratorInvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    /**
     * 초대 응답자와 수락 시각을 기록한다.
     *
     * @param responder 초대를 수락한 회원
     * @param respondedAt 수락 시각
     */
    public void accept(Member responder, LocalDateTime respondedAt) {
        recordResponse(
                responder,
                respondedAt,
                ProjectCollaboratorInvitationStatus.ACCEPTED
        );
    }

    /**
     * 초대 응답자와 거절 시각을 기록한다.
     *
     * @param responder 초대를 거절한 회원
     * @param respondedAt 거절 시각
     */
    public void decline(Member responder, LocalDateTime respondedAt) {
        recordResponse(
                responder,
                respondedAt,
                ProjectCollaboratorInvitationStatus.DECLINED
        );
    }

    // 초대 응답자와 응답 시각, 응답 상태를 기록한다.
    private void recordResponse(
            Member responder,
            LocalDateTime respondedAt,
            ProjectCollaboratorInvitationStatus responseStatus
    ) {
        this.status = responseStatus;
        this.respondedBy = responder;
        this.respondedAt = respondedAt;
    }
}
