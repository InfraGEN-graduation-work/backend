package com.infragen.infragen.domain.project.entity;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "project_collaborator",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_collaborator_project_member",
                        columnNames = {"project_id", "member_id"}
                )
        }
)
public class ProjectCollaborator extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectCollaboratorRole role;

    /**
     * project와 member의 collaborator 역할을 저장할 membership을 생성한다.
     *
     * @param project collaborator가 참여할 project
     * @param member project에 참여할 member
     * @param role collaborator에게 부여할 역할
     */
    @Builder
    public ProjectCollaborator(
            Project project,
            Member member,
            ProjectCollaboratorRole role
    ) {
        this.project = project;
        this.member = member;
        this.role = role;
    }

    /**
     * collaborator의 project 역할을 변경한다.
     *
     * @param role 변경할 collaborator 역할
     */
    public void changeRole(ProjectCollaboratorRole role) {
        this.role = role;
    }
}
