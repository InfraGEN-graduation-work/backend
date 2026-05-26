package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQL;
import com.infragen.infragen.domain.parsing.dto.response.SpringBoot;
import lombok.RequiredArgsConstructor;
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
            throw new ParsingException("node가 없습니다.");
        }

        validateGraphStructure.validate(requestDTO.getNodes() , requestDTO.getEdges());

        ParsingResultDTO result = new ParsingResultDTO();
        if (!Objects.equals(requestDTO.getProjectId(), projectId)) {
            throw new ParsingException("프로젝트 id가 다릅니다.");
        }
        result.setProjectId(requestDTO.getProjectId());
        Set<Integer> usedPorts = new HashSet<>();

        requestDTO.getNodes().forEach(node -> {
            String type = node.getComponentType();
            if (type == null || type.isEmpty()) throw new ParsingException("컴포넌트 설정이 누락되었습니다.");
            switch (type) {
                case "SPRING_BOOT":
                    SpringBoot springBoot = springBootNode(node);

                    if (usedPorts.contains(springBoot.getPort())) {
                        throw new ParsingException("중복된 포트 번호가 존재합니다: " + springBoot.getPort());
                    }
                    usedPorts.add(springBoot.getPort());

                    result.getSpringBoot().add(springBoot);
                    break;
                case "MYSQL":
                    MySQL mySQL = mySQLNode(node);
                    result.getMySQL().add(mySQL);
                    break;
            }
        });

        return result;
    }

    private SpringBoot springBootNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        int port = props.path("port").asInt();
        String env = props.path("env").asString();
        if (port < 1024 || port > 65535) {
            throw new ParsingException("포트 번호는 1024 ~ 65535 사이여야 합니다. 입력값: " + port);
        }
        return new SpringBoot(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                port,
                env
        );
    }

    private MySQL mySQLNode(NodeDTO node){
        JsonNode props = objectMapper.valueToTree(node.getProperties());
        String dbName = props.path("databaseName").asString();
        String dbPass = props.path("rootPassword").asString();
        if(!dbName.matches("^[a-zA-Z0-9_]+$")){
            throw new ParsingException("데이터베이스 이름에는 영문 , 숫자 , 언더바만 사용할 수 있습니다.");
        }
        if(dbPass == null || dbPass.length() < 8){
            throw new ParsingException("MySQL 루트 비밀번호는 최소 8자리 이상이어야 합니다.");
        }
        return new MySQL(
                node.getNodeId(),
                node.getPositionX(),
                node.getPositionY(),
                dbName,
                dbPass
        );
    }
}