package com.infragen.infragen.domain.parsing.parser;

import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.global.enums.ComponentType;

import tools.jackson.databind.JsonNode;

public interface ComponentParser {

    ComponentType getSupportedType();

    default boolean requiresPort() {
        return true;
    }

    BaseComponent parse(NodeDTO node, JsonNode props, int port);
}
