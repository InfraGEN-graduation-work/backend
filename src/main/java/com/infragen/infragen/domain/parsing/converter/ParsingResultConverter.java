package com.infragen.infragen.domain.parsing.converter;

import java.util.List;

import com.infragen.infragen.domain.parsing.dto.response.BaseComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.global.enums.ComponentType;

public class ParsingResultConverter {

    private ParsingResultConverter() {
    }

    public static <T extends BaseComponent> List<T> getComponentsByType(
        ParsingResultDTO result,
        Class<T> type
    ) {
        return result.getComponents().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();
    }

    public static List<BaseComponent> getComponentsByType(
        ParsingResultDTO result,
        ComponentType componentType
    ) {
        return result.getComponents().stream()
            .filter(component -> component.getComponentType() == componentType)
            .toList();
    }
}
