package com.infragen.infragen.domain.project.entity;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.infragen.infragen.global.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_edge")
public class ProjectEdge extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_node_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProjectNode sourceNode; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_node_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProjectNode targetNode;

    @Builder
    public ProjectEdge(
            Project project,
            ProjectNode sourceNode,
            ProjectNode targetNode
    ) {
        this.project = project;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }
}
