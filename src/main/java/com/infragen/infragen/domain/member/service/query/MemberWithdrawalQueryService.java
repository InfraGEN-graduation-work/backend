package com.infragen.infragen.domain.member.service.query;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.converter.MemberConverter;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴나 guest 이용 종료를 확정하기 전에 소유 프로젝트가 승계될지 삭제될지 미리 알려주는 조회를 맡는다.
 * 탈퇴가 프로젝트 삭제를 동반하므로, 사용자가 삭제 대상을 확인한 뒤 탈퇴하도록 화면에 보여줄 값을 만든다.
 * 조회만 하며 프로젝트나 회원을 잠그거나 변경하지 않는다. 결과는 조회 시점의 참고용이고,
 * 실제 탈퇴 시점의 승계·삭제 결과는 {@code MemberWithdrawalCommandService}가 다시 계산한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalQueryService {
    private final MemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    /**
     * 탈퇴할 회원(일반 회원 또는 guest)의 소유 프로젝트를 project ID 순으로 반환하고, 프로젝트마다 승계될지 삭제될지 표시한다.
     * 소유 프로젝트가 없으면 빈 목록을 반환한다.
     *
     * @param memberId 탈퇴하려는 회원 ID
     * @throws MemberException 회원이 없거나 비활성이면 MEMBER_NOT_FOUND
     */
    @Transactional(readOnly = true)
    public MemberResDTO.WithdrawalPreview getWithdrawalPreview(Long memberId) {
        memberRepository.findById(memberId)
                .filter(found -> Boolean.TRUE.equals(found.getIsActive()))
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        return MemberConverter.toWithdrawalPreview(
                projectRepository.findOwnedProjectWithdrawalPreviewsByMemberId(memberId));
    }
}
