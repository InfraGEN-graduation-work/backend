package com.infragen.infragen.domain.generation.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationCommandService {
    private final ProjectRepository projectRepository;
    private final ParsingService parsingService;
    private final IaCGenerationService iaCGenerationService;

    @Transactional
    public GenerateResDTO.GenerateResultResDTO generate(
        Long projectId,
        ParsingReqDTO request,
        Long memberId
    ) {
        log.info("인프라 코드 생성 요청: projectId={}, memberId={}", projectId, memberId);

        projectRepository.findByIdAndMemberId(projectId, memberId)
            .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        ParsingResultDTO parsingResult = parsingService.parsing(request, projectId);
        IaCFileDTO.BundleResDTO bundle = iaCGenerationService.generateDockerCompose(parsingResult);

        log.info("인프라 코드 생성 완료: projectId={}, fileCount={}", projectId, bundle.files().size());

        return GenerateResDTO.GenerateResultResDTO.builder()
            .files(bundle.files().stream()
                .map(file -> GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName(file.fileName())
                    .content(file.content())
                    .build())
                .toList())
            .historyId(null)
            .build();
    }
}
