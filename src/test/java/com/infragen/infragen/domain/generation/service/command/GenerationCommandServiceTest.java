package com.infragen.infragen.domain.generation.service.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.service.IaCGenerationService;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;
import com.infragen.infragen.domain.parsing.dto.response.ParsingResultDTO;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.domain.parsing.service.ParsingService;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

import java.util.Map;

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
        GenerateReqDTO.Request request = localRequest();
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        List<IaCFileDTO.FileContentResDTO> files = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("local/docker-compose.yml")
                .content("services:\n  mysql:\n    image: mysql:8.0\n")
                .build(),
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("local/.env")
                .content("MYSQL_HOST=localhost\n")
                .build()
        );
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(files)
            .build();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(any(ParsingReqDTO.class), eq(projectId))).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.DOCKER_COMPOSE))
            .thenReturn(bundle);
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
            () -> assertEquals("local/docker-compose.yml", result.files().get(0).fileName()),
            () -> assertEquals(files.get(0).content(), result.files().get(0).content()),
            () -> assertEquals("local/.env", result.files().get(1).fileName()),
            () -> assertEquals(files.get(1).content(), result.files().get(1).content())
        );
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        ArgumentCaptor<ParsingReqDTO> parsingRequestCaptor =
            ArgumentCaptor.forClass(ParsingReqDTO.class);
        verify(parsingService).parsing(parsingRequestCaptor.capture(), eq(projectId));
        assertSame(request.nodes(), parsingRequestCaptor.getValue().getNodes());
        assertSame(request.edges(), parsingRequestCaptor.getValue().getEdges());
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.DOCKER_COMPOSE);
        verify(projectHistoryCommandService).saveGeneratedHistory(projectId, memberId, files);
    }

    @Test
    @DisplayName("AWS 배포 옵션 — Terraform generator로 라우팅하고 history 저장")
    void generate_AwsDeploymentOption_RoutesToTerraformGenerator() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        DeploymentTargetReqDTO.Target deploymentTarget = awsTarget();
        GenerateReqDTO.Request request = new GenerateReqDTO.Request(
            null,
            null,
            DeploymentOption.AWS,
            false,
            deploymentTarget
        );
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        List<IaCFileDTO.FileContentResDTO> files = List.of(
            IaCFileDTO.FileContentResDTO.builder()
                .fileName("cloud/Dockerfile")
                .content("FROM eclipse-temurin:17-jre\n")
                .build()
        );
        IaCFileDTO.BundleResDTO bundle = IaCFileDTO.BundleResDTO.builder()
            .files(files)
            .build();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(any(ParsingReqDTO.class), eq(projectId))).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.TERRAFORM, deploymentTarget))
            .thenReturn(bundle);
        when(projectHistoryCommandService.saveGeneratedHistory(projectId, memberId, files))
            .thenReturn(43L);

        // when
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            request,
            memberId
        );

        // then
        assertAll(
            () -> assertEquals(43L, result.historyId()),
            () -> assertEquals("cloud/Dockerfile", result.files().get(0).fileName()),
            () -> assertEquals(files.get(0).content(), result.files().get(0).content())
        );
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(any(ParsingReqDTO.class), eq(projectId));
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.TERRAFORM, deploymentTarget);
        verify(projectHistoryCommandService).saveGeneratedHistory(projectId, memberId, files);
    }

    @Test
    @DisplayName("OCI 배포 옵션과 AWS target — 잘못된 target 오류 반환")
    void generate_OciOptionWithAwsTarget_ThrowsInvalidComponentState() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        GenerateReqDTO.Request request = new GenerateReqDTO.Request(
            null,
            null,
            DeploymentOption.OCI,
            false,
            awsTarget()
        );
        ParsingResultDTO parsingResult = new ParsingResultDTO();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(any(ParsingReqDTO.class), eq(projectId)))
            .thenReturn(parsingResult);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generationCommandService.generate(projectId, request, memberId)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(any(ParsingReqDTO.class), eq(projectId));
        verifyNoInteractions(iaCGenerationService, projectHistoryCommandService);
    }

    @Test
    @DisplayName("파싱 실패 — 생성과 이력 저장을 호출하지 않음")
    void generate_ParsingFailure_DoesNotGenerateOrSaveHistory() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        GenerateReqDTO.Request request = localRequest();
        ParsingException parsingException = new ParsingException(ParsingErrorCode.EMPTY_NODES);

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(any(ParsingReqDTO.class), eq(projectId))).thenThrow(parsingException);

        // when
        ParsingException exception = assertThrows(
            ParsingException.class,
            () -> generationCommandService.generate(
                projectId,
                request,
                memberId
            )
        );

        // then
        assertEquals(ParsingErrorCode.EMPTY_NODES, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(any(ParsingReqDTO.class), eq(projectId));
        verifyNoInteractions(iaCGenerationService, projectHistoryCommandService);
    }

    @Test
    @DisplayName("IaC 생성 실패 — 이력 저장을 호출하지 않음")
    void generate_GenerationFailure_DoesNotSaveHistory() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        GenerateReqDTO.Request request = localRequest();
        ParsingResultDTO parsingResult = new ParsingResultDTO();
        IaCGenerationException generationException = new IaCGenerationException(
            IaCGenerationErrorCode.INVALID_COMPONENT_STATE
        );

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(null);
        when(parsingService.parsing(any(ParsingReqDTO.class), eq(projectId))).thenReturn(parsingResult);
        when(iaCGenerationService.generate(parsingResult, OutputFormat.DOCKER_COMPOSE))
            .thenThrow(generationException);

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> generationCommandService.generate(
                projectId,
                request,
                memberId
            )
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_COMPONENT_STATE, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verify(parsingService).parsing(any(ParsingReqDTO.class), eq(projectId));
        verify(iaCGenerationService).generate(parsingResult, OutputFormat.DOCKER_COMPOSE);
        verifyNoInteractions(projectHistoryCommandService);
    }

    private GenerateReqDTO.Request localRequest() {
        EdgeDTO edge = new EdgeDTO();
        edge.setSourceNodeId("mysql-1");
        edge.setTargetNodeId("app-1");
        return new GenerateReqDTO.Request(
            List.of(new NodeDTO("mysql-1", "MYSQL", 0f, 0f, Map.of())),
            List.of(edge),
            DeploymentOption.LOCAL,
            false,
            null
        );
    }

    private DeploymentTargetReqDTO.Target awsTarget() {
        return new DeploymentTargetReqDTO.AwsDeploymentTarget(
            "ap-northeast-2",
            "infragen-vpc",
            "infragen-subnet",
            "infragen-igw",
            "infragen-public-route",
            "infragen-sg",
            "infragen-app",
            "10.0.0.0/16",
            "10.0.1.0/24",
            "ami-xxxxxxxx",
            "t3.micro",
            "203.0.113.10/32",
            "0.0.0.0/0"
        );
    }
}
