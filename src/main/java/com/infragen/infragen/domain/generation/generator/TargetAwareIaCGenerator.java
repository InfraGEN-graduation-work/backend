package com.infragen.infragen.domain.generation.generator;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;

/** 배포 대상 설정이 필요한 IaC generator의 확장 계약이다. */
public interface TargetAwareIaCGenerator extends IaCGenerator {

    /** provider target을 반영해 IaC 파일을 생성한다. */
    IaCFileDTO.BundleResDTO generate(
        ParsingResultDTO parsingResult,
        DeploymentTargetReqDTO.Target deploymentTarget
    );
}
