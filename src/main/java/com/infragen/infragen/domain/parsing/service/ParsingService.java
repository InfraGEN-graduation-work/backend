package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.enums.ComponentType;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ParsingService {
    private final ObjectMapper objectMapper;
    private final ValidateGraphStructure validateGraphStructure;

    public ParsingResultDTO parsing(ParsingReqDTO requestDTO , Long projectId){
        if (requestDTO.getNodes() == null || requestDTO.getNodes().isEmpty()){
            throw new ParsingException(ParsingErrorCode.EMPTY_NODES);
        }

        validateGraphStructure.validate(requestDTO.getNodes() , requestDTO.getEdges());

        ParsingResultDTO result = new ParsingResultDTO();
        if (!Objects.equals(requestDTO.getProjectId(), projectId)) {
            throw new ParsingException(ParsingErrorCode.PROJECT_ID_MISMATCH);
        }
        result.setProjectId(requestDTO.getProjectId());
        Set<Integer> usedPorts = new HashSet<>();

        requestDTO.getNodes().forEach(node -> {
            ComponentType type = ComponentType.valueOf(node.getComponentType());
            switch (type) {
                case SPRING_BOOT:
                    SpringBootComponent springBoot = springBootNode(node);

                    if (usedPorts.contains(springBoot.getPort())) {
                        throw new ParsingException(ParsingErrorCode.DUPLICATE_PORT);
                    }
                    usedPorts.add(springBoot.getPort());

                    result.getSpringBoot().add(springBoot);
                    break;
                case MYSQL:
                    MySQLComponent mySQL = mySQLNode(node);
                    result.getMySQL().add(mySQL);
                    break;
            }
        });
        if (requestDTO.getEdges() != null) {
            result.setEdges(requestDTO.getEdges());
        }
        return result;
    }

    private SpringBootComponent springBootNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        int port = props.path("port").asInt();
        String env = props.path("env").asString();
        if (port < 1024 || port > 65535) {
            throw new ParsingException(ParsingErrorCode.INVALID_PORT_RANGE);
        }
        return new SpringBootComponent(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                port,
                env
        );
    }

    private MySQLComponent mySQLNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        String dbName = props.path("databaseName").asString();
        String dbPass = props.path("rootPassword").asString();
        if(!dbName.matches("^[a-zA-Z0-9_]+$")){
            throw new ParsingException(ParsingErrorCode.INVALID_DB_NAME);
        }
        if(dbPass == null || dbPass.length() < 8){
            throw new ParsingException(ParsingErrorCode.INVALID_DB_PASSWORD);
        }
        return new MySQLComponent(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                dbName,
                dbPass
        );
    }
}