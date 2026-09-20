package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.auth.service.EmailVerificationService;
import com.infragen.infragen.domain.member.converter.MemberConverter;
import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService {
    private static final int MAX_INVITATION_CODE_GENERATION_ATTEMPTS = 10;

    private final MemberRepository memberRepository;
    private final ProjectCollaboratorInvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final EmailVerificationService emailVerificationService;

    // 일반 회원가입
    public MemberResDTO.MemberResultDTO createMember(AuthReqDTO.SignupDTO request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        emailVerificationService.verifyAndConsume(request.getEmail(), request.getVerificationCode());
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member newMember = MemberConverter.toEntity(request, encodedPassword);
        ensureUniqueInvitationCode(newMember);

        return MemberConverter.toResultDTO(memberRepository.save(newMember));
    }

    // 소셜 회원가입
    public MemberResDTO.MemberResultDTO createSocialMember(String email, String nickname, String socialId, SocialProvider provider) {
        // 이미 해당 소셜 정보로 가입된 회원이 있는지 먼저 확인
        return memberRepository.findBySocialIdAndSocialProvider(socialId, provider)
                .map(MemberConverter::toResultDTO)
                .orElseGet(() -> {
                    // 신규 가입인 경우, 이메일 중복 체크
                    if (email != null && memberRepository.existsByEmail(email)) {
                        log.warn("소셜 가입 실패: 이미 가입된 이메일입니다. email={}", email);
                        throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
                    }

                    // 신규 소셜 회원 생성
                    String randomPassword = UUID.randomUUID().toString();
                    String encodedPassword = passwordEncoder.encode(randomPassword);
                    Member newMember = MemberConverter.toSocialEntity(email, nickname, socialId, provider, encodedPassword);
                    ensureUniqueInvitationCode(newMember);

                    log.info("신규 소셜 회원 생성: provider={}, socialId={}", provider, socialId);
                    return MemberConverter.toResultDTO(memberRepository.save(newMember));
                }
            );
    }

    /** 요청마다 서로 다른 guest member를 생성한다. */
    @Transactional
    public MemberResDTO.MemberResultDTO createGuestMember() {
        String guestIdentifier = UUID.randomUUID().toString();
        String email = "guest-" + guestIdentifier + "@guest.infragen.local";
        String nickname = "게스트-" + guestIdentifier.substring(0, 8);
        String randomPassword = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(randomPassword);

        Member guestMember = MemberConverter.toGuestEntity(
                email,
                nickname,
                encodedPassword
        );
        ensureUniqueInvitationCode(guestMember);

        return MemberConverter.toResultDTO(memberRepository.save(guestMember));
    }

    /** 현재 회원의 초대코드를 보장한다. 구형 또는 누락된 코드는 row lock 아래에서 교체한다. */
    @Transactional
    public MemberResDTO.InvitationCode ensureInvitationCode(Long memberId) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        String previousCode = member.getInvitationCode();
        String invitationCode = member.ensureInvitationCode();

        if (!invitationCode.equals(previousCode)) {
            ensureUniqueInvitationCode(member);
        }

        return MemberConverter.toInvitationCode(member.getInvitationCode());
    }

    public MemberResDTO.MemberResultDTO updateMember(Long memberId, MemberReqDTO.UpdateMember request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        ensureNotGuest(member);
        if (member.getSocialProvider() != null && request.password() != null) {
            throw new MemberException(MemberErrorCode.CANNOT_CHANGE_SOCIAL_PASSWORD);
        }
        String nickname = request.nickname() != null ? request.nickname() : member.getNickname();
        String password = request.password() != null
                ? passwordEncoder.encode(request.password())
                : member.getPassword();
        member.updateProfile(nickname, password);
        return MemberConverter.toResultDTO(member);
    }

    @Transactional
    public void withdrawMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        ensureNotGuest(member);
        invitationRepository.deleteAllByMemberId(memberId);
        member.withdraw();
        tokenService.deleteRefreshToken(memberId);
    }

    private void ensureNotGuest(Member member) {
        if (member.getRole() == Role.ROLE_GUEST) {
            throw new MemberException(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED);
        }
    }

    private void ensureUniqueInvitationCode(Member member) {
        for (int attempt = 0; attempt < MAX_INVITATION_CODE_GENERATION_ATTEMPTS; attempt++) {
            long duplicateCount = member.getId() == null
                    ? memberRepository.countRowsByInvitationCode(member.getInvitationCode())
                    : memberRepository.countRowsByInvitationCodeExcludingMember(
                            member.getInvitationCode(), member.getId());
            if (duplicateCount == 0) {
                return;
            }
            if (member.getId() != null) {
                throw new MemberException(MemberErrorCode.INVITATION_CODE_GENERATION_FAILED);
            }
            member.regenerateInvitationCode();
        }
        throw new MemberException(MemberErrorCode.INVITATION_CODE_GENERATION_FAILED);
    }
}
