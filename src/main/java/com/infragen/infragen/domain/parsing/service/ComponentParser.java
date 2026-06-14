package com.infragen.infragen.domain.parsing.service;
import com.infragen.infragen.domain.parsing.enums.ComponentType;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingResultDTO;
import tools.jackson.databind.JsonNode;

public interface ComponentParser {
    ComponentType getSupportedType();
    void parseAndAddToResult(NodeDTO node, JsonNode props, int port, ParsingResultDTO result);
}