package com.infragen.infragen.domain.collaboration.entity;

import com.infragen.infragen.domain.member.entity.Member;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "project_collaboration_checkpoint_failure",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_project_collaboration_checkpoint_failure_project_version",
                columnNames = {"project_id", "server_version"}
        )
)
public class ProjectCollaborationCheckpointFailure extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_payload", columnDefinition = "json", nullable = false)
    private Map<String, Object> graphPayload;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Builder
    public ProjectCollaborationCheckpointFailure(
            Project project,
            Member member,
            Long serverVersion,
            Map<String, Object> graphPayload,
            int attemptCount,
            String lastError
    ) {
        this.project = project;
        this.member = member;
        this.serverVersion = serverVersion;
        this.graphPayload = Map.copyOf(graphPayload);
        this.attemptCount = attemptCount;
        this.lastError = lastError;
    }

    public void recordFailure(String errorMessage) {
        this.attemptCount++;
        this.lastError = errorMessage;
    }
}
