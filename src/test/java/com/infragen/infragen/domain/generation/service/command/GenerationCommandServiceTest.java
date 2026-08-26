package com.infragen.infragen.domain.generation.service.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenerationCommandService")
class GenerationCommandServiceTest {

    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ParsingService parsingService;

    @Mock
    private IaCGenerationService iaCGenerationService;

    @Mock
    private ProjectHistoryCommandService projectHistoryCommandService;

    @InjectMocks
    private GenerationCommandService generationCommandService;

    @Test
    @DisplayName("유효한 요청 — 파일과 historyId 반환")
    void generate_ValidRequest_ReturnsFilesAndHistoryId() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ParsingReqDTO request = new ParsingReqDTO();
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        List<IaCFileDTO.FileContentResDTO> files = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("docker-compose.yml")
                .content("services:\n  mysql:\n    image: mysql:8.0\n")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName(".env")
                .content("MYSQL_HOST=localhost\n")
                .build()
        );
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(files)
            .build();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(request, projectId)).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.DOCKER_COMPOSE))
            .thenReturn(bundle);
        when(projectHistoryCommandService.saveGeneratedHistory(projectId, memberId, files))
            .thenReturn(42L);

        // when
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            request,
            memberId,
            OutputFormat.DOCKER_COMPOSE
        );

        // then
        assertAll(
            () -> assertEquals(42L, result.historyId()),
            () -> assertEquals(2, result.files().size()),
            () -> assertEquals("docker-compose.yml", result.files().get(0).fileName()),
            () -> assertEquals(files.get(0).content(), result.files().get(0).content()),
            () -> assertEquals(".env", result.files().get(1).fileName()),
            () -> assertEquals(files.get(1).content(), result.files().get(1).content())
        );
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(request, projectId);
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.DOCKER_COMPOSE);
        verify(projectHistoryCommandService).saveGeneratedHistory(projectId, memberId, files);
    }

    @Test
    @DisplayName("TERRAFORM 형식 — Terraform generator로 라우팅하고 history 저장")
    void generate_TerraformFormat_RoutesToTerraformGenerator() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ParsingReqDTO request = new ParsingReqDTO();
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        List<IaCFileDTO.FileContentResDTO> files = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("Dockerfile")
                .content("FROM eclipse-temurin:17-jre\n")
                .build()
        );
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(files)
            .build();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(request, projectId)).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.TERRAFORM))
            .thenReturn(bundle);
        when(projectHistoryCommandService.saveGeneratedHistory(projectId, memberId, files))
            .thenReturn(43L);

        // when
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            request,
            memberId,
            OutputFormat.TERRAFORM
        );

        // then
        assertAll(
            () -> assertEquals(43L, result.historyId()),
            () -> assertEquals("Dockerfile", result.files().get(0).fileName()),
            () -> assertEquals(files.get(0).content(), result.files().get(0).content())
        );
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(request, projectId);
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.TERRAFORM);
        verify(projectHistoryCommandService).saveGeneratedHistory(projectId, memberId, files);
    }

    @Test
    @DisplayName("지원하지 않는 출력 형식 — GENERATION400_1")
    void generate_UnsupportedFormat_ThrowsGenerationException() {
        // given
        ParsingReqDTO request = new ParsingReqDTO();

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generationCommandService.generate(1L, request, 2L, null)
        );

        // then
        assertEquals(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT, exception.getCode());
        verifyNoInteractions(
            projectQueryService,
            parsingService,
            iaCGenerationService,
            projectHistoryCommandService
        );
    }

    @Test
    @DisplayName("파싱 실패 — 생성과 이력 저장을 호출하지 않음")
    void generate_ParsingFailure_DoesNotGenerateOrSaveHistory() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ParsingReqDTO request = new ParsingReqDTO();
        ParsingException parsingException = new ParsingException(ParsingErrorCode.EMPTY_NODES);

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(request, projectId)).thenThrow(parsingException);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> generationCommandService.generate(
                projectId,
                request,
                memberId,
                OutputFormat.DOCKER_COMPOSE
            )
        );

        // then
        assertEquals(ParsingErrorCode.EMPTY_NODES, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(request, projectId);
        verifyNoInteractions(iaCGenerationService, projectHistoryCommandService);
    }

    @Test
    @DisplayName("IaC 생성 실패 — 이력 저장을 호출하지 않음")
    void generate_GenerationFailure_DoesNotSaveHistory() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        ParsingReqDTO request = new ParsingReqDTO();
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        IaCGenerationException generationException = new IaCGenerationException(
            IaCGenerationErrorCode.INVALID_COMPONENT_STATE
        );

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(request, projectId)).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.DOCKER_COMPOSE))
            .thenThrow(generationException);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generationCommandService.generate(
                projectId,
                request,
                memberId,
                OutputFormat.DOCKER_COMPOSE
            )
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(request, projectId);
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.DOCKER_COMPOSE);
        verifyNoInteractions(projectHistoryCommandService);
    }
}
