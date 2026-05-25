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
import com.infragen.infragen.global.entity.BaseEntity;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE member SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = "uk_social_provider_social_id", columnNames = { "social_provider", "social_id" })
})
public class Member extends BaseEntity {
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

    @Column(nullable = false)
    private Boolean isActive;

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
    }
}
