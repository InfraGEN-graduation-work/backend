package com.infragen.infragen.domain.project.entity;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "project_node",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_node_project_node_id",
                        columnNames = {"project_id", "node_id"}
                )
        }
)
public class ProjectNode extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_node_seq_gen")
    @SequenceGenerator(
            name = "project_node_seq_gen",
            sequenceName = "project_node_seq",
            initialValue = 1,
            allocationSize = 50
    )
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private ComponentType componentType;

    @Column(name = "node_name", nullable = false)
    private String nodeName;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

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
            String nodeId,
            BigDecimal positionX,
            BigDecimal positionY,
            Map<String, Object> properties,
            Project project
    ) {
        this.componentType = componentType;
        this.nodeName = nodeName;
        this.nodeId = nodeId;
        this.positionX = positionX;
        this.positionY = positionY;
        this.properties = (properties != null) ? properties : new HashMap<>();
        this.project = project;
    }

    /**
     * canvas node의 표시 이름을 변경한다.
     *
     * @param nodeName 변경할 표시 이름
     */
    public void renameTo(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * canvas node의 위치를 변경한다.
     *
     * @param positionX 변경할 X 좌표
     * @param positionY 변경할 Y 좌표
     */
    public void moveTo(BigDecimal positionX, BigDecimal positionY) {
        this.positionX = positionX;
        this.positionY = positionY;
    }
}
