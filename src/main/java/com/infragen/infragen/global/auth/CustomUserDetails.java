package com.infragen.infragen.global.auth;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record CustomUserDetails(MemberResDTO.MemberResultDTO memberDTO) implements UserDetails {
    public Long getMemberId() {
        return memberDTO.id();
    }

    // 사용자의 권한 목록 반환
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(() -> memberDTO.role().toString());
    }

    @Override
    public String getPassword() {
        return ""; // 소셜 로그인 사용 및 폼 로그인을 사용하지 않기 때문에 빈 문자열만 반환
    }

    @Override
    public String getUsername() {
        return String.valueOf(memberDTO.id());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return memberDTO.isActive();
    }
}
