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
import com.infragen.infragen.domain.parsing.validator.EnvValueValidator;
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

    /**
     * graph request를 검증하고 component parser를 통해 generation 입력으로 변환한다.
     *
     * @param requestDTO parsing할 graph request
     * @param projectId parsing 결과에 연결할 project 식별자
     * @return 검증과 component parsing이 완료된 결과
     * @throws ParsingException node·edge·component·port 규칙을 위반한 경우
     */
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
                if (type == ComponentType.NGINX && (!props.path("port").isIntegralNumber()
                    || !props.path("port").canConvertToInt())) {
                    throw new ParsingException(ParsingErrorCode.INVALID_NGINX_PORT);
                }
                port = props.path("port").asInt();
                if (port < (type == ComponentType.NGINX ? 1 : 1024) || port > 65535) {
                    throw new ParsingException(type == ComponentType.NGINX
                        ? ParsingErrorCode.INVALID_NGINX_PORT : ParsingErrorCode.INVALID_PORT_RANGE);
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

    /**
     * 노드 속성의 사용자 이름과 비밀번호에 {@code .env} 금지 문자가 있는지 검사한다. 프로젝트 저장이 생성 요청 전에 같은 기준으로 거부하려고 쓴다.
     *
     * <p>검사 위치는 컴포넌트 파서와 같은 {@code EnvValueField}를 쓴다. 필수값 누락과 길이는 보지 않아 미완성 노드도 통과하고, 알 수 없는 컴포넌트 타입과 null
     * 속성은 건너뛴다. 그런 노드는 호출한 쪽의 기존 변환 단계가 처리한다.
     *
     * @param componentType 노드의 컴포넌트 타입 이름. 대소문자를 구분한다.
     * @param properties 노드 속성. null이면 건너뛴다.
     * @throws ParsingException 금지 문자가 있는 경우. 값과 필드 이름은 노출하지 않는다.
     */
    public void validateEnvValues(String componentType, Map<String, Object> properties) {
        if (componentType == null || properties == null) {
            return;
        }
        ComponentType type;
        try {
            type = ComponentType.valueOf(componentType);
        } catch (IllegalArgumentException e) {
            return;
        }
        EnvValueValidator.validateProperties(type, objectMapper.valueToTree(properties));
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
