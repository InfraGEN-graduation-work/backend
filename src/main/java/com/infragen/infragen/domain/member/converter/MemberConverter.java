package com.infragen.infragen.domain.member.converter;

import java.util.List;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;
import com.infragen.infragen.domain.project.repository.projection.OwnedProjectWithdrawalPreview;

public final class MemberConverter {
    private MemberConverter() {
    }

    /**
     * Member entity를 MemberResultDTO로 변환한다.
     * @param member
     * @return
     */
    public static MemberResDTO.MemberResultDTO toResultDTO(Member member) {
        return MemberResDTO.MemberResultDTO.builder()
                .id(member.getId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .isActive(member.getIsActive())
                .createdAt(member.getCreatedAt())
                .build();
    }

    public static MemberResDTO.InvitationCode toInvitationCode(String invitationCode) {
        return MemberResDTO.InvitationCode.builder()
                .inviteCode(invitationCode)
                .build();
    }

    /**
     * 소유 프로젝트 조회 결과를 탈퇴 전 안내 응답으로 변환한다. 입력 순서를 유지하고 목록이 비면 빈 목록을 담는다.
     * 승계 후보 유무가 true일 때만 SUCCESSION으로 표시한다. 값이 없으면 삭제될 수 있는 쪽으로 알려
     * 사용자가 삭제를 놓치는 일이 없게 한다.
     */
    public static MemberResDTO.WithdrawalPreview toWithdrawalPreview(List<OwnedProjectWithdrawalPreview> previews) {
        List<MemberResDTO.WithdrawalOwnedProject> ownedProjects = previews.stream()
                .map(preview -> MemberResDTO.WithdrawalOwnedProject.builder()
                        .projectId(preview.getProjectId())
                        .title(preview.getTitle())
                        .outcome(Boolean.TRUE.equals(preview.getHasSuccessor())
                                ? WithdrawalProjectOutcome.SUCCESSION
                                : WithdrawalProjectOutcome.DELETION)
                        .build())
                .toList();
        return MemberResDTO.WithdrawalPreview.builder()
                .ownedProjects(ownedProjects)
                .build();
    }

    /**
     * 일반 회원가입 회원을 생성한다. (소셜 로그인과 달리 socialId·provider는 null)
     * @param request
     * @param encodedPassword
     * @return
     */
    public static Member toEntity(AuthReqDTO.SignupDTO request, String encodedPassword) {
        return Member.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
    }

    /**
     * 소셜 로그인 회원을 생성한다. (소셜 로그인 시 socialId·provider를 저장)
     * @param email
     * @param nickname
     * @param socialId
     * @param provider
     * @param encodedPassword
     * @return
     */
    public static Member toSocialEntity(String email, String nickname, String socialId, SocialProvider provider, String encodedPassword) {
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .isActive(true)
                .socialProvider(provider)
                .socialId(socialId)
                .build();
    }

    /**
     * guest member를 생성한다. (소셜 로그인과 달리 socialId·provider는 null)
     * @param email
     * @param nickname
     * @param encodedPassword
     * @return
     */
    public static Member toGuestEntity(String email, String nickname, String encodedPassword) {
        return Member.builder()
                .email(email)
                .password(encodedPassword)
                .nickname(nickname)
                .role(Role.ROLE_GUEST)
                .isActive(true)
                .build();
    }
}
