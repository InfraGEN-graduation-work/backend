package com.infragen.infragen.domain.collaboration.entity;

import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "project_collaboration_state",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_collaboration_state_project",
                        columnNames = "project_id"
                )
        }
)
public class ProjectCollaborationState extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @Builder
    public ProjectCollaborationState(Project project) {
        this.project = project;
        this.serverVersion = 0L;
    }

    /**
     * project의 collaboration serverVersion을 다음 값으로 증가시킨다.
     */
    public void advanceServerVersion() {
        this.serverVersion++;
    }
}
