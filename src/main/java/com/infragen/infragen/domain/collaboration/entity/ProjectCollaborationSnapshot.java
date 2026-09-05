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
        name = "project_collaboration_snapshot",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_collaboration_snapshot_project_version",
                        columnNames = {"project_id", "server_version"}
                )
        }
)
public class ProjectCollaborationSnapshot extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", nullable = false)
    private Member updatedBy;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_payload", columnDefinition = "json", nullable = false)
    private Map<String, Object> graphPayload;

    @Builder
    public ProjectCollaborationSnapshot(
            Project project,
            Member updatedBy,
            Long serverVersion,
            Map<String, Object> graphPayload
    ) {
        this.project = project;
        this.updatedBy = updatedBy;
        this.serverVersion = serverVersion;
        this.graphPayload = Map.copyOf(graphPayload);
    }
}
