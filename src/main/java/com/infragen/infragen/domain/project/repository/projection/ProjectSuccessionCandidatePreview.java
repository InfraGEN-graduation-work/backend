package com.infragen.infragen.domain.project.repository.projection;

import java.time.LocalDateTime;

import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;

/**
 * 소유권 자동 승계에서 새 owner 후보가 될 참여자 한 명의 선정용 값이다.
 * 계정 유형·참여 역할·참여 시각·membership ID로 후보 우선순위를 정하는 데만 쓰며,
 * Member Entity를 적재하지 않는 read 경계라 API 응답으로 노출하지 않는다.
 */
public interface ProjectSuccessionCandidatePreview {
    Long getMembershipId();
    Long getMemberId();
    Role getMemberRole();
    ProjectCollaboratorRole getCollaboratorRole();

    /** 참여 기록(project_collaborator)이 생성된 시각이며 회원 가입 시각이 아니다. */
    LocalDateTime getJoinedAt();
}
