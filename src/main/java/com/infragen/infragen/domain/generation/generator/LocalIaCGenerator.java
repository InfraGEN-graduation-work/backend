package com.infragen.infragen.domain.generation.generator;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

/** 배포 target 없이 Local runtime 산출물을 생성하는 generator 계약이다. */
public interface LocalIaCGenerator extends IaCGenerator {

    /** parsing 결과로 Local 산출물을 생성한다. */
    IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult);
}
