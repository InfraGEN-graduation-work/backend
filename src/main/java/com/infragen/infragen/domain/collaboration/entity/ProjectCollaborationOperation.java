package com.infragen.infragen.domain.collaboration.entity;

import com.infragen.infragen.domain.collaboration.enums.CollaborationOperationType;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "project_collaboration_operation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_collaboration_operation_project_operation",
                        columnNames = {"project_id", "operation_id"}
                ),
                @UniqueConstraint(
                        name = "uk_project_collaboration_operation_project_version",
                        columnNames = {"project_id", "server_version"}
                )
        }
)
public class ProjectCollaborationOperation extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_member_id", nullable = false)
    private Member actorMember;

    @Column(name = "operation_id", nullable = false, length = 100)
    private String operationId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "base_version", nullable = false)
    private Long baseVersion;

    @Column(name = "server_version", nullable = false)
    private Long serverVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false, length = 50)
    private CollaborationOperationType operationType;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, Object> payload;

    @Builder
    public ProjectCollaborationOperation(
            Project project,
            Member actorMember,
            String operationId,
            String clientId,
            Long baseVersion,
            Long serverVersion,
            CollaborationOperationType operationType,
            String nodeId,
            Map<String, Object> payload
    ) {
        this.project = project;
        this.actorMember = actorMember;
        this.operationId = operationId;
        this.clientId = clientId;
        this.baseVersion = baseVersion;
        this.serverVersion = serverVersion;
        this.operationType = operationType;
        this.nodeId = nodeId;
        this.payload = Map.copyOf(payload);
    }

    /**
     * 같은 operationId로 들어온 요청이 기존 operation의 재전송인지 확인한다.
     *
     * @param clientId 비교할 client 식별자
     * @param baseVersion 비교할 client 기준 version
     * @param operationType 비교할 operation 타입
     * @param nodeId 비교할 node 식별자
     * @param payload 비교할 operation payload
     * @return 기존 operation과 요청 내용이 같으면 true
     */
    public boolean isRetryOf(
            String clientId,
            Long baseVersion,
            CollaborationOperationType operationType,
            String nodeId,
            Map<String, Object> payload
    ) {
        return this.clientId.equals(clientId)
                && this.baseVersion.equals(baseVersion)
                && this.operationType.equals(operationType)
                && this.nodeId.equals(nodeId)
                && this.payload.equals(payload);
    }
}
