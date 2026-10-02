package com.infragen.infragen.domain.parsing.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;

@DisplayName("그래프 구조 검증")
class ValidateGraphStructureTest {

    private ValidateGraphStructure validateGraphStructure;

    @BeforeEach
    void setUp() {
        validateGraphStructure = new ValidateGraphStructure();
    }

    @Test
    @DisplayName("유효한 그래프 — MySQL → Spring Boot")
    void validate_ValidGraph_DoesNotThrow() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            springBootNode("node-2")
        );
        List<EdgeDTO> edges = List.of(edge("node-1", "node-2"));

        // when / then
        // 순환 오탐이 발생하지 않도록 순환 검증 로직을 개선했기 때문에
        // 유효한 그래프에 대해서는 예외가 발생하지 않아야 함
        assertDoesNotThrow(() -> validateGraphStructure.validate(nodes, edges));
    }

    @Test
    @DisplayName("중복 엣지 — 순환 오탐 없이 통과")
    void validate_DuplicateEdge_DoesNotThrow() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            springBootNode("node-2")
        );
        List<EdgeDTO> edges = List.of(
            edge("node-1", "node-2"),
            edge("node-1", "node-2")
        );

        // when / then
        assertDoesNotThrow(() -> validateGraphStructure.validate(nodes, edges));
    }

    @Test
    @DisplayName("역방향 엣지 — PARSING400_10")
    void validate_InvalidDependencyDirection_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            springBootNode("node-2")
        );
        List<EdgeDTO> edges = List.of(edge("node-2", "node-1"));

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, edges)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_COMPONENT_DEPENDENCY, exception.getCode());
    }

    @Test
    @DisplayName("순환 참조 — PARSING400_9")
    void validate_CycleDetected_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            mysqlNode("node-2")
        );
        List<EdgeDTO> edges = List.of(
            edge("node-1", "node-2"),
            edge("node-2", "node-1")
        );

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, edges)
        );

        // then
        assertEquals(ParsingErrorCode.CYCLE_DETECTED, exception.getCode());
    }

    @Test
    @DisplayName("nodeId 누락 — PARSING400_14")
    void validate_MissingNodeId_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            new NodeDTO(null, "MYSQL", 0f, 0f, Map.of())
        );

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, List.of())
        );

        // then
        assertEquals(ParsingErrorCode.MISSING_NODE_ID, exception.getCode());
    }

    @Test
    @DisplayName("nodeId 중복 — PARSING400_15")
    void validate_DuplicateNodeId_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            springBootNode("node-1")
        );

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, List.of())
        );

        // then
        assertEquals(ParsingErrorCode.DUPLICATE_NODE_ID, exception.getCode());
    }

    @Test
    @DisplayName("존재하지 않는 노드 참조 — PARSING400_8")
    void validate_InvalidEdgeNode_Throws() {
        // given
        List<NodeDTO> nodes = List.of(mysqlNode("node-1"));
        List<EdgeDTO> edges = List.of(edge("node-1", "missing"));

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, edges)
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_EDGE_NODE, exception.getCode());
    }

    @Test
    @DisplayName("엣지 endpoint 누락 — PARSING400_16")
    void validate_InvalidEdgeEndpoint_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("node-1"),
            springBootNode("node-2")
        );
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("node-1");
        edge.setTargetNodeId(null);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, List.of(edge))
        );

        // then
        assertEquals(ParsingErrorCode.INVALID_EDGE_ENDPOINT, exception.getCode());
    }

    @Test
    @DisplayName("앱 하나에 같은 타입 DB 둘 연결 — PARSING400_25")
    void validate_SameTypeDatabasesToOneApplication_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("mysql-1"),
            mysqlNode("mysql-2"),
            springBootNode("app")
        );
        List<EdgeDTO> edges = List.of(
            edge("mysql-1", "app"),
            edge("mysql-2", "app")
        );

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, edges)
        );

        // then
        assertEquals(ParsingErrorCode.DUPLICATE_DEPENDENCY_TYPE, exception.getCode());
    }

    @Test
    @DisplayName("앱 하나에 Redis 둘 연결 — PARSING400_25")
    void validate_SameTypeCachesToOneApplication_Throws() {
        // given
        List<NodeDTO> nodes = List.of(
            redisNode("redis-1"),
            redisNode("redis-2"),
            springBootNode("app")
        );
        List<EdgeDTO> edges = List.of(
            edge("redis-1", "app"),
            edge("redis-2", "app")
        );

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> validateGraphStructure.validate(nodes, edges)
        );

        // then
        assertEquals(ParsingErrorCode.DUPLICATE_DEPENDENCY_TYPE, exception.getCode());
    }

    @Test
    @DisplayName("앱 하나에 MySQL과 PostgreSQL 연결 — 통과")
    void validate_DifferentTypeDatabasesToOneApplication_DoesNotThrow() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("mysql"),
            postgresqlNode("postgres"),
            springBootNode("app")
        );
        List<EdgeDTO> edges = List.of(
            edge("mysql", "app"),
            edge("postgres", "app")
        );

        // when
        Runnable validation = () -> validateGraphStructure.validate(nodes, edges);

        // then
        assertDoesNotThrow(validation::run);
    }

    @Test
    @DisplayName("MySQL 하나를 앱 둘이 공유 — 통과")
    void validate_SharedDatabaseAcrossApplications_DoesNotThrow() {
        // given
        List<NodeDTO> nodes = List.of(
            mysqlNode("mysql"),
            springBootNode("app-1"),
            springBootNode("app-2")
        );
        List<EdgeDTO> edges = List.of(
            edge("mysql", "app-1"),
            edge("mysql", "app-2")
        );

        // when
        Runnable validation = () -> validateGraphStructure.validate(nodes, edges);

        // then
        assertDoesNotThrow(validation::run);
    }

    private static NodeDTO mysqlNode(String nodeId) {
        return new NodeDTO(nodeId, "MYSQL", 100f, 200f, Map.of());
    }

    private static NodeDTO postgresqlNode(String nodeId) {
        return new NodeDTO(nodeId, "POSTGRESQL", 100f, 300f, Map.of());
    }

    private static NodeDTO redisNode(String nodeId) {
        return new NodeDTO(nodeId, "REDIS", 100f, 400f, Map.of());
    }

    private static NodeDTO springBootNode(String nodeId) {
        return new NodeDTO(nodeId, "SPRING_BOOT", 400f, 200f, Map.of());
    }

    private static EdgeDTO edge(String sourceNodeId, String targetNodeId) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        return edge;
    }
}
