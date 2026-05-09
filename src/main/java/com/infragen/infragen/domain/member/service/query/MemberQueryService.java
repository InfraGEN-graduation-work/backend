package com.infragen.infragen.domain.member.service.query;

import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.converter.MemberConverter;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService {
    private final MemberRepository memberRepository;

    public Optional<MemberResDTO.MemberResultDTO> findBySocialIdAndProvider(String socialId, SocialProvider provider) {
        return memberRepository.findBySocialIdAndSocialProvider(socialId, provider)
                .map(MemberConverter::toResultDTO);
    }

    public Member findByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(AuthErrorCode.TOKEN_INVALID));
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new AuthException(AuthErrorCode.TOKEN_INVALID));
    }
}
