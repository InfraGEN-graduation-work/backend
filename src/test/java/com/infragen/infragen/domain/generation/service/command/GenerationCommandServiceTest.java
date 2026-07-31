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
        when(iaCGenerationService.generateDockerCompose(parsingResult)).thenReturn(bundle);
        when(projectHistoryCommandService.saveGeneratedHistory(projectId, memberId, files))
            .thenReturn(42L);

        // when
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            request,
            memberId
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
        verify(iaCGenerationService).generateDockerCompose(parsingResult);
        verify(projectHistoryCommandService).saveGeneratedHistory(projectId, memberId, files);
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
            () -> generationCommandService.generate(projectId, request, memberId)
        );

        // then
        assertEquals(ParsingErrorCode.EMPTY_NODES, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(request, projectId);
        verifyNoInteractions(iaCGenerationService, projectHistoryCommandService);
    }
}
