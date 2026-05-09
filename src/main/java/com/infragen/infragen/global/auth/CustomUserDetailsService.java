package com.infragen.infragen.global.auth;

import com.infragen.infragen.domain.member.converter.MemberConverter;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @NonNull
    @Override
    public UserDetails loadUserByUsername(@NonNull String memberId) { // jwt에서 추출한 memberId를 문자열 형태로 받음
        return memberRepository.findById(Long.parseLong(memberId))
                .map(member -> new CustomUserDetails(
                    MemberConverter.toResultDTO(member)
                )) // 조회된 Member 객체로 CustomUserDetails 객체 생성
                .orElseThrow(() -> new UsernameNotFoundException("해당하는 회원을 찾을 수 없습니다. ID: " + memberId));
    }
}
