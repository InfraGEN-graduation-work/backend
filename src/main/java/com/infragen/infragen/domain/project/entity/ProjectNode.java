package com.infragen.infragen.domain.project.entity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.infragen.infragen.domain.project.enums.ComponentType;
import com.infragen.infragen.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_node")
public class ProjectNode extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private ComponentType componentType;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "position_x", precision = 10, scale = 3)
    private BigDecimal positionX;

    @Column(name = "position_y", precision = 10, scale = 3)
    private BigDecimal positionY;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json", nullable = false)
    private Map<String, Object> properties;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Builder
    public ProjectNode(
            ComponentType componentType,
            String nodeName,
            BigDecimal positionX,
            BigDecimal positionY,
            Map<String, Object> properties,
            Project project
    ) {
        this.componentType = componentType;
        this.nodeName = nodeName;
        this.positionX = positionX;
        this.positionY = positionY;
        this.properties = (properties != null) ? properties : new HashMap<>();
        this.project = project;
    }
}
