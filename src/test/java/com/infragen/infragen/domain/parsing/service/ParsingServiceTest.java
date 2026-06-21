package com.infragen.infragen.domain.parsing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.parser.MySQLParser;
import com.infragen.infragen.domain.parsing.parser.SpringBootParser;
import com.infragen.infragen.domain.parsing.validator.ValidateGraphStructure;

import tools.jackson.databind.ObjectMapper;

@DisplayName("파싱 서비스")
class ParsingServiceTest {

    private ParsingService parsingService;

    @BeforeEach
    void setUp() {
        parsingService = new ParsingService(
            new ObjectMapper(),
            new ValidateGraphStructure(),
            List.of(new SpringBootParser(), new MySQLParser())
        );
    }

    @Test
    @DisplayName("유효한 요청 — 파싱 성공")
    void parsing_ValidRequest_ReturnsComponents() {
        ParsingReqDTO request = validRequest();

        ParsingResultDTO result = parsingService.parsing(request, 1L);

        assertEquals(1L, result.getProjectId());
        assertEquals(2, result.getComponents().size());
        assertEquals(1, result.getEdges().size());
    }

    @Test
    @DisplayName("노드 없음 — PARSING400_1")
    void parsing_EmptyNodes_Throws() {
        ParsingReqDTO request = new ParsingReqDTO();
        request.setProjectId(1L);
        request.setNodes(List.of());

        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parsingService.parsing(request, 1L)
        );

        assertEquals(ParsingErrorCode.EMPTY_NODES, exception.getCode());
    }

    @Test
    @DisplayName("프로젝트 ID 불일치 — PARSING400_2")
    void parsing_ProjectIdMismatch_Throws() {
        ParsingReqDTO request = validRequest();
        request.setProjectId(99L);

        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parsingService.parsing(request, 1L)
        );

        assertEquals(ParsingErrorCode.PROJECT_ID_MISMATCH, exception.getCode());
    }

    @Test
    @DisplayName("포트 중복 — PARSING400_4")
    void parsing_DuplicatePort_Throws() {
        ParsingReqDTO request = new ParsingReqDTO();
        request.setProjectId(1L);
        request.setNodes(List.of(mysqlNode("node-1", 8080), springBootNode("node-2", 8080)));
        request.setEdges(List.of(edge("node-1", "node-2")));

        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parsingService.parsing(request, 1L)
        );

        assertEquals(ParsingErrorCode.DUPLICATE_PORT, exception.getCode());
    }

    @Test
    @DisplayName("순환 참조 — PARSING400_9")
    void parsing_CycleDetected_Throws() {
        ParsingReqDTO request = new ParsingReqDTO();
        request.setProjectId(1L);
        request.setNodes(List.of(mysqlNode("node-1", 3306), mysqlNode("node-2", 3307)));
        request.setEdges(List.of(
            edge("node-1", "node-2"),
            edge("node-2", "node-1")
        ));

        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> parsingService.parsing(request, 1L)
        );

        assertEquals(ParsingErrorCode.CYCLE_DETECTED, exception.getCode());
    }

    private static ParsingReqDTO validRequest() {
        ParsingReqDTO request = new ParsingReqDTO();
        request.setProjectId(1L);
        request.setNodes(List.of(mysqlNode("node-1", 3306), springBootNode("node-2")));
        request.setEdges(List.of(edge("node-1", "node-2")));
        return request;
    }

    private static NodeDTO mysqlNode(String nodeId, int port) {
        Map<String, Object> env = new HashMap<>();
        env.put("databaseName", "appdb");
        env.put("username", "user");
        env.put("userPassword", "userpass12");
        env.put("rootPassword", "rootpass12");

        Map<String, Object> properties = new HashMap<>();
        properties.put("imageVersion", "mysql:8.0");
        properties.put("containerName", "mysql");
        properties.put("volumeName", "mysql_data");
        properties.put("port", port);
        properties.put("env", env);

        return new NodeDTO(nodeId, "MYSQL", 100f, 200f, properties);
    }

    private static NodeDTO springBootNode(String nodeId, int port) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("name", "app");
        properties.put("port", port);
        properties.put("javaVersion", "17");
        properties.put("containerName", "spring-app");

        return new NodeDTO(nodeId, "SPRING_BOOT", 400f, 200f, properties);
    }

    private static NodeDTO springBootNode(String nodeId) {
        return springBootNode(nodeId, 8080);
    }

    private static EdgeDTO edge(String sourceNodeId, String targetNodeId) {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId(sourceNodeId);
        edge.setTargetNodeId(targetNodeId);
        return edge;
    }
}
