package com.infragen.infragen.domain.generation.generator;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.global.enums.ComponentType;

/** 배포 target 없이 Local runtime 산출물을 생성하는 generator 계약이다. */
public interface LocalIaCGenerator extends IaCGenerator {

    /** parsing 결과로 Local 산출물을 생성한다. */
    IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult);

    /**
     * 이 generator가 해당 타입을 Local 산출물에 담는지 알려 준다.
     *
     * <p>{@code false}인 타입은 {@link #generate}가 산출물에서 조용히 건너뛰므로, Local 단독 요청에서는
     * 호출하는 쪽이 먼저 거부해야 한다. 기본 구현은 모든 타입을 지원한다고 본다.
     *
     * @param componentType 확인할 component type
     * @return Local 산출물에 담을 수 있으면 {@code true}
     */
    default boolean supports(ComponentType componentType) {
        return true;
    }
}
