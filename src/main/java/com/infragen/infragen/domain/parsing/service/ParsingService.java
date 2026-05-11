package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQL;
import com.infragen.infragen.domain.parsing.dto.response.SpringBoot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class ParsingService {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    public ParsingResultDTO parsing(ParsingReqDTO requestDTO){
        ParsingResultDTO Result = new ParsingResultDTO();
        if (requestDTO.getNodes() == null) return Result;
        requestDTO.getNodes().forEach(node -> {
            String type = node.getComponentType();
            if (type == null) return;
            switch (type) {
                case "SPRING_BOOT":
                    Result.getSpringBoot().add(SpringBootNode(node));
                    break;
                case "MYSQL":
                    Result.getMySQL().add(MySQLNode(node));
                    break;
            }
        });

        return Result;
    }

    private SpringBoot SpringBootNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        return new SpringBoot(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                props.path("port").asInt(),
                props.path("env").asText()
        );
    }

    private MySQL MySQLNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        return new MySQL(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                props.path("databaseName").asText(),
                props.path("rootPassword").asText()
        );
    }
}