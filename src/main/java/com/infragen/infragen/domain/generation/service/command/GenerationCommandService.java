package com.infragen.infragen.domain.generation.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationCommandService {
    private final ProjectQueryService projectQueryService;
    private final ParsingService parsingService;
    private final IaCGenerationService iaCGenerationService;
    private final ProjectHistoryCommandService projectHistoryCommandService;

    @Transactional
    public GenerateResDTO.GenerateResultResDTO generate(
        Long projectId,
        ParsingReqDTO request,
        Long memberId
    ) {
        log.info("인프라 코드 생성 요청: projectId={}, memberId={}", projectId, memberId);

        projectQueryService.getOwnedProject(projectId, memberId);

        ParsingResultDTO parsingResult = parsingService.parsing(request, projectId);
        IaCFileDTO.BundleResDTO bundle = iaCGenerationService.generateDockerCompose(parsingResult);

        Long historyId = projectHistoryCommandService.saveGeneratedHistory(
            projectId, memberId, bundle.files());

        log.info("인프라 코드 생성 완료: projectId={}, fileCount={}, historyId={}",
            projectId, bundle.files().size(), historyId);

        return GenerateResDTO.GenerateResultResDTO.builder()
            .files(bundle.files().stream()
                .map(file -> GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName(file.fileName())
                    .content(file.content())
                    .build())
                .toList())
            .historyId(historyId)
            .build();
    }
}
