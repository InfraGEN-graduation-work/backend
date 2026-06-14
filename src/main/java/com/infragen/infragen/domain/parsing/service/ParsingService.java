package com.infragen.infragen.domain.parsing.service;

import com.infragen.infragen.domain.parsing.dto.response.MySQLEnvComponent;
import com.infragen.infragen.domain.parsing.enums.ComponentType;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ParsingService {
    private final ObjectMapper objectMapper;
    private final ValidateGraphStructure validateGraphStructure;
    private final Map<ComponentType, ComponentParser> parserMap;

    public ParsingService(ObjectMapper objectMapper,
                          ValidateGraphStructure validateGraphStructure,
                          List<ComponentParser> parsers) {
        this.objectMapper = objectMapper;
        this.validateGraphStructure = validateGraphStructure;
        this.parserMap = parsers.stream()
                .collect(Collectors.toMap(ComponentParser::getSupportedType, Function.identity()));
    }
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

            JsonNode props = objectMapper.valueToTree(node.getProperties());

            int port = props.path("port").asInt();
            if (port < 1024 || port > 65535) {
                throw new ParsingException(ParsingErrorCode.INVALID_PORT_RANGE);
            }
            if (usedPorts.contains(port)) {
                throw new ParsingException(ParsingErrorCode.DUPLICATE_PORT);
            }
            usedPorts.add(port);

            ComponentType type = ComponentType.valueOf(node.getComponentType().toUpperCase());

            ComponentParser parser = parserMap.get(type);
            if (parser == null) {
                throw new ParsingException(ParsingErrorCode.UNSUPPORTED_COMPONENT_TYPE);
            }
            parser.parseAndAddToResult(node, props, port, result);
        });
        if (requestDTO.getEdges() != null) {
            result.setEdges(requestDTO.getEdges());
        }

        return result;
    }
}
