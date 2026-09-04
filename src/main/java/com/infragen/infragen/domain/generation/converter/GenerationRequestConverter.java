package com.infragen.infragen.domain.generation.converter;

import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;

/** Generate 요청에서 parsing 전용 graph 요청을 생성한다. */
public final class GenerationRequestConverter {

    private GenerationRequestConverter() {
    }

    /** 배포 설정을 제외하고 graph nodes·edges만 parsing 요청으로 전달한다. */
    public static ParsingReqDTO toParsingRequest(GenerateReqDTO.Request request) {
        return new ParsingReqDTO(request.nodes(), request.edges());
    }
}
