package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.member.converter.MemberConverter;
import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
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
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    // 일반 회원가입
    public MemberResDTO.MemberResultDTO createMember(AuthReqDTO.SignupDTO request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL);
        }
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        Member newMember = MemberConverter.toEntity(request, encodedPassword);

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

                    log.info("신규 소셜 회원 생성: provider={}, socialId={}", provider, socialId);
                    return MemberConverter.toResultDTO(memberRepository.save(newMember));
                }
            );
    }

    public MemberResDTO.MemberResultDTO updateMember(Long memberId, MemberReqDTO.UpdateMember request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
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

    public void withdrawMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
        member.withdraw();
    }
}
