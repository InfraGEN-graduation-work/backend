package com.infragen.infragen.domain.generation.generator;

import java.util.List;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.parsing.converter.ParsingResultConverter;
import com.infragen.infragen.domain.parsing.dto.response.MySQLComponent;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.dto.response.SpringBootComponent;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DockerComposeIaCGenerator implements IaCGenerator {

    @Override
    public OutputFormat getOutputFormat() {
        return OutputFormat.DOCKER_COMPOSE;
    }

    @Override
    public IaCFileDTO.BundleResDTO generate(ParsingResultDTO parsingResult) {
        log.debug("Docker Compose 생성 요청: projectId={}", parsingResult.getProjectId());

        List<SpringBootComponent> springBootNodes =
            ParsingResultConverter.getComponentsByType(parsingResult, SpringBootComponent.class);
        List<MySQLComponent> mysqlNodes =
            ParsingResultConverter.getComponentsByType(parsingResult, MySQLComponent.class);

        // TODO(B1): frontend generateDockerCompose.ts 로직 이식
        log.debug(
            "파싱된 컴포넌트: springBoot={}, mysql={}, total={}",
            springBootNodes.size(),
            mysqlNodes.size(),
            parsingResult.getComponents().size()
        );

        return IaCFileDTO.BundleResDTO.builder()
            .files(List.of(
                IaCFileDTO.FileContentResDTO.builder()
                    .fileName("docker-compose.yml")
                    .content("# IaC generation — Phase B1에서 구현 예정\n")
                    .build(),
                IaCFileDTO.FileContentResDTO.builder()
                    .fileName(".env")
                    .content("# IaC generation — Phase B1에서 구현 예정\n")
                    .build()
            ))
            .build();
    }
}
