package com.infragen.infragen.domain.project.entity;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE project SET is_active = false WHERE id = ?")
@SQLRestriction("is_active = true")
@Table(name = "project")
public class Project extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status;

    @Column(name = "is_active")
    private boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public Project(
            String title,
            String description,
            ProjectStatus status,
            Member member,
            boolean isActive
    ) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.member = member;
        this.isActive = isActive;
    }
}
