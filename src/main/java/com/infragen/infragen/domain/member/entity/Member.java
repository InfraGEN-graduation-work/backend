package com.infragen.infragen.domain.member.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.global.entity.BaseEntity;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE member SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_provider_social_id", columnNames = { "social_provider", "social_id" }),
        @UniqueConstraint(name = "uk_member_invitation_code", columnNames = { "invitation_code" })
})
public class Member extends BaseEntity {
    private static final SecureRandom INVITATION_CODE_RANDOM = new SecureRandom();
    private static final String INVITATION_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITATION_CODE_LENGTH = 8;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider")
    private SocialProvider socialProvider;

    @Column(name = "social_id")
    private String socialId;

    @Column(name = "invitation_code", length = 32)
    private String invitationCode;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Member(
            String email,
            String password,
            String nickname,
            Role role,
            Boolean isActive,
            SocialProvider socialProvider,
            String socialId
    ) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
        this.isActive = isActive;
        this.socialProvider = socialProvider;
        this.socialId = socialId;
        this.invitationCode = createInvitationCode();
    }

    // 회원 탈퇴 처리. 식별 정보를 마스킹해 unique 제약을 해제하고, 동일 이메일·소셜 계정 재가입을 허용
    public void withdraw() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return;
        }
        this.email = createMaskedEmail(this.id);
        this.socialId = null;
        this.socialProvider = null;
        this.nickname = "탈퇴회원";
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, String encodedPassword) {
        this.nickname = nickname;
        this.password = encodedPassword;
    }

    /** 초대코드가 없거나 이전 형식인 회원에게 새 코드를 발급하고 유효한 값은 유지한다. */
    public String ensureInvitationCode() {
        if (!isValidInvitationCode(this.invitationCode)) {
            regenerateInvitationCode();
        }
        return this.invitationCode;
    }

    /** 초대코드 충돌이 확인된 회원에게 새 후보 코드를 할당한다. */
    public void regenerateInvitationCode() {
        if (this.id != null && isValidInvitationCode(this.invitationCode)) {
            throw new MemberException(MemberErrorCode.INVITATION_CODE_IMMUTABLE);
        }
        this.invitationCode = createInvitationCode();
    }

    private static String createMaskedEmail(Long memberId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "withdrawn_" + memberId + "_" + suffix + "@deleted.infragen.local";
    }

    private static String createInvitationCode() {
        StringBuilder invitationCode = new StringBuilder(INVITATION_CODE_LENGTH);
        for (int index = 0; index < INVITATION_CODE_LENGTH; index++) {
            invitationCode.append(INVITATION_CODE_CHARACTERS.charAt(
                    INVITATION_CODE_RANDOM.nextInt(INVITATION_CODE_CHARACTERS.length())
            ));
        }
        return invitationCode.toString();
    }

    // 초대코드 유효성 검사: null이 아니고, 길이가 8이며, 허용된 문자만 포함되어야 한다.
    private static boolean isValidInvitationCode(String invitationCode) {
        return invitationCode != null
                && invitationCode.length() == INVITATION_CODE_LENGTH
                && invitationCode.chars()
                        .allMatch(character -> INVITATION_CODE_CHARACTERS.indexOf(character) >= 0);
    }
}
