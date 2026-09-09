package com.infragen.infragen.domain.project.entity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectNodeTest {
    @Test
    void renameTo_changesNodeName() {
        // given
        ProjectNode node = node();

        // when
        node.renameTo("database");

        // then
        assertEquals("database", node.getNodeName());
    }

    @Test
    void moveTo_changesNodePosition() {
        // given
        ProjectNode node = node();

        // when
        node.moveTo(BigDecimal.valueOf(300), BigDecimal.valueOf(-100));

        // then
        assertEquals(BigDecimal.valueOf(300), node.getPositionX());
        assertEquals(BigDecimal.valueOf(-100), node.getPositionY());
    }

    private ProjectNode node() {
        return ProjectNode.builder()
                .nodeName("mysql")
                .nodeId("mysql-node-1")
                .positionX(BigDecimal.ZERO)
                .positionY(BigDecimal.ZERO)
                .build();
    }
}
