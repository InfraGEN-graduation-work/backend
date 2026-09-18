package com.infragen.infragen.domain.member.repository;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import jakarta.persistence.LockModeType;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<@NonNull Member,@NonNull Long> {
    Optional<Member> findByEmail(String email);

    // 이메일 중복 체크
    boolean existsByEmail(String email);

    // 소셜 로그인 고유 식별자로 회원 조회
    Optional<Member> findBySocialIdAndSocialProvider(String socialId, SocialProvider socialProvider);

    /** 초대코드로 활성 회원을 조회한다. 비활성 회원은 Member의 SQL restriction이 제외한다. */
    Optional<Member> findByInvitationCode(String invitationCode);

    /** 코드가 없거나 구형인 회원을 갱신할 때 같은 회원의 동시 발급을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT member FROM Member member WHERE member.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    /** Soft-deleted row도 포함해 DB unique 제약과 같은 범위에서 코드 중복을 확인한다. */
    @Query(
            value = "SELECT COUNT(*) FROM member WHERE invitation_code = :invitationCode",
            nativeQuery = true
    )
    long countRowsByInvitationCode(@Param("invitationCode") String invitationCode);
}
