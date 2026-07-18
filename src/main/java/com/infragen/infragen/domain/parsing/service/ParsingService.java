package com.infragen.infragen.domain.parsing.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.parser.ComponentParser;
import com.infragen.infragen.domain.parsing.validator.ValidateGraphStructure;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ParsingService {

    private final ObjectMapper objectMapper;
    private final ValidateGraphStructure validateGraphStructure;
    private final Map<ComponentType, ComponentParser> parserMap;

    public ParsingService(
        ObjectMapper objectMapper,
        ValidateGraphStructure validateGraphStructure,
        List<ComponentParser> parsers
    ) {
        this.objectMapper = objectMapper;
        this.validateGraphStructure = validateGraphStructure;
        this.parserMap = parsers.stream()
            .collect(Collectors.toMap(ComponentParser::getSupportedType, parser -> parser));
    }

    public ParsingResultDTO parsing(ParsingReqDTO requestDTO, Long projectId) {
        if (requestDTO.getNodes() == null || requestDTO.getNodes().isEmpty()) {
            throw new ParsingException(ParsingErrorCode.EMPTY_NODES);
        }

        validateGraphStructure.validate(requestDTO.getNodes(), requestDTO.getEdges());

        ParsingResultDTO result = new ParsingResultDTO();
        result.setProjectId(projectId);
        Set<Integer> usedPorts = new HashSet<>();

        requestDTO.getNodes().forEach(node -> {
            JsonNode props = objectMapper.valueToTree(node.getProperties());

            ComponentType type = resolveComponentType(node.getComponentType());

            ComponentParser parser = parserMap.get(type);

            if (parser == null) {
                throw new ParsingException(ParsingErrorCode.UNSUPPORTED_COMPONENT_TYPE);
            }

            int port = 0;

            if (parser.requiresPort()) {
                port = props.path("port").asInt();
                if (port < 1024 || port > 65535) {
                    throw new ParsingException(ParsingErrorCode.INVALID_PORT_RANGE);
                }
                if (usedPorts.contains(port)) {
                    throw new ParsingException(ParsingErrorCode.DUPLICATE_PORT);
                }
                usedPorts.add(port);
            }

            BaseComponent component = parser.parse(node, props, port);
            result.getComponents().add(component);
        });

        if (requestDTO.getEdges() != null) {
            result.setEdges(requestDTO.getEdges());
        }

        return result;
    }

    private ComponentType resolveComponentType(String componentType) {
        if (componentType == null || componentType.isBlank()) {
            throw new ParsingException(ParsingErrorCode.MISSING_COMPONENT_TYPE);
        }
        
        try {
            return ComponentType.valueOf(componentType.toUpperCase());
        } catch (IllegalArgumentException e) { // 지원하지 않는 컴포넌트 타입인 경우의 예외 처리
            throw new ParsingException(ParsingErrorCode.UNSUPPORTED_COMPONENT_TYPE);
        }
    }
}
