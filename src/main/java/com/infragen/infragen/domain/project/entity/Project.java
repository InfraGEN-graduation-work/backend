package com.infragen.infragen.domain.project.entity;

import java.util.Objects;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    public Project(
            String title,
            String description,
            ProjectStatus status,
            Member member
    ) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.member = member;
    }

    public void updateInfo(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** 기존 프로젝트의 graph와 이력을 유지하면서 단일 owner를 교체한다. */
    public void transferOwnershipTo(Member newOwner) {
        this.member = Objects.requireNonNull(newOwner);
    }
}
