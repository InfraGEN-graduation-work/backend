package com.infragen.infragen.domain.member.repository;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<@NonNull Member,@NonNull Long> {
    Optional<Member> findByEmail(String email);

    // 이메일 중복 체크
    boolean existsByEmail(String email);

    // 소셜 로그인 고유 식별자로 회원 조회
    Optional<Member> findBySocialIdAndSocialProvider(String socialId, SocialProvider socialProvider);
}
