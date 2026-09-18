package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationVersionService;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectEdgeReqDTO;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.collaboration.event.ProjectRoomResyncEvent;
import com.infragen.infragen.global.enums.ComponentType;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectCommandServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectNodeRepository projectNodeRepository;

    @Mock
    private ProjectEdgeRepository projectEdgeRepository;

    @Mock
    private ProjectHistoryRepository projectHistoryRepository;

    @Mock
    private GeneratedFileRepository generatedFileRepository;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ProjectCollaborationVersionService projectCollaborationVersionService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ProjectCollaboratorRepository projectCollaboratorRepository;

    @Mock
    private ProjectCollaboratorInvitationRepository projectCollaboratorInvitationRepository;

    @Mock
    private ProjectCollaborationStateRepository collaborationStateRepository;

    @Mock
    private ProjectCollaborationOperationRepository collaborationOperationRepository;

    @Mock
    private ProjectCollaborationSnapshotRepository collaborationSnapshotRepository;

    @Mock
    private ProjectCollaborationCheckpointFailureRepository checkpointFailureRepository;

    @InjectMocks
    private ProjectCommandService projectCommandService;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"New description"})
    @DisplayName("metadata만 바꾸고 기존 graph와 식별자를 resync에 보존한다")
    void updateMetadata_Owner_PreservesGraph(String description) {
        // given
        Project project = Project.builder().title("Old").description("Keep me")
                .status(ProjectStatus.DRAFT).build();
        ReflectionTestUtils.setField(project, "id", 100L);
        ProjectNode node = ProjectNode.builder().project(project).nodeId("node-1").nodeName("Database")
                .componentType(ComponentType.MYSQL).positionX(BigDecimal.ONE).positionY(BigDecimal.TEN)
                .properties(Map.of("port", 3306)).build();
        ReflectionTestUtils.setField(node, "id", 101L);
        ProjectNode target = ProjectNode.builder().project(project).nodeId("node-2").nodeName("App")
                .componentType(ComponentType.SPRING_BOOT).positionX(BigDecimal.TEN).positionY(BigDecimal.ONE)
                .properties(Map.of()).build();
        ReflectionTestUtils.setField(target, "id", 102L);
        ProjectEdge edge = ProjectEdge.builder().project(project).sourceNode(node).targetNode(target).build();
        ReflectionTestUtils.setField(edge, "id", 201L);
        var original = ProjectConverter.toProjectDetailResDTO(project, List.of(node, target), List.of(edge));
        when(projectRepository.existsByIdAndMemberId(100L, 7L)).thenReturn(true);
        when(projectCollaborationVersionService.issueNextVersionForFullReplace(100L, 12L)).thenReturn(13L);
        when(projectRepository.findByIdAndMemberId(100L, 7L)).thenReturn(Optional.of(project));
        when(projectNodeRepository.findAllByProjectId(100L)).thenReturn(List.of(node, target));
        when(projectEdgeRepository.findAllByProjectId(100L)).thenReturn(List.of(edge));

        // when
        var result = projectCommandService.updateMetadata(100L,
                new ProjectReqDTO.UpdateMetadata("New", description, 12L), 7L);

        // then
        var event = ArgumentCaptor.forClass(ProjectRoomResyncEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertAll(
                () -> assertEquals("New", result.title()),
                () -> assertEquals(description == null ? "Keep me" : description, result.description()),
                () -> assertEquals("OWNER", result.accessRole()),
                () -> assertEquals(100L, result.projectId()),
                () -> assertEquals(13L, event.getValue().snapshot().serverVersion()),
                () -> assertEquals(13L, event.getValue().snapshot().graphVersion()),
                () -> assertEquals(original.nodes(), event.getValue().snapshot().project().nodes()),
                () -> assertEquals(original.edges(), event.getValue().snapshot().project().edges()),
                () -> assertEquals(result.description(), event.getValue().snapshot().project().description())
        );
        InOrder order = inOrder(projectRepository, projectCollaborationVersionService, projectNodeRepository);
        order.verify(projectRepository).existsByIdAndMemberId(100L, 7L);
        order.verify(projectCollaborationVersionService).issueNextVersionForFullReplace(100L, 12L);
        order.verify(projectRepository).findByIdAndMemberId(100L, 7L);
        order.verify(projectNodeRepository).findAllByProjectId(100L);
        verify(projectNodeRepository, never()).deleteByProjectId(anyLong());
        verify(projectEdgeRepository, never()).deleteByProjectId(anyLong());
        verify(projectNodeRepository, never()).saveAll(anyList());
        verify(projectEdgeRepository, never()).saveAll(anyList());
        verifyNoInteractions(projectCollaboratorRepository, projectQueryService);
    }

    @Test
    @DisplayName("소유자가 아니거나 프로젝트가 없으면 metadata 변경을 거부한다")
    void updateMetadata_NotOwner_RejectsBeforeVersion() {
        // given
        var request = new ProjectReqDTO.UpdateMetadata("New", null, 12L);
        when(projectRepository.existsByIdAndMemberId(100L, 7L)).thenReturn(false);

        // when
        var exception = assertThrows(ProjectException.class,
                () -> projectCommandService.updateMetadata(100L, request, 7L));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verifyNoInteractions(projectCollaborationVersionService, projectNodeRepository,
                projectEdgeRepository, eventPublisher);
        verify(projectRepository, never()).findByIdAndMemberId(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(longs = {11L, 13L})
    @DisplayName("metadata 기준 버전 충돌 시 graph 조회와 event 발행을 하지 않는다")
    void updateMetadata_VersionConflict_LeavesGraphUntouched(long baseVersion) {
        // given
        var request = new ProjectReqDTO.UpdateMetadata("New", null, baseVersion);
        when(projectRepository.existsByIdAndMemberId(100L, 7L)).thenReturn(true);
        when(projectCollaborationVersionService.issueNextVersionForFullReplace(100L, baseVersion))
                .thenThrow(new CollaborationException(CollaborationErrorCode.VERSION_CONFLICT));

        // when
        var exception = assertThrows(CollaborationException.class,
                () -> projectCommandService.updateMetadata(100L, request, 7L));

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
        verifyNoInteractions(projectNodeRepository, projectEdgeRepository, eventPublisher);
        verify(projectRepository, never()).findByIdAndMemberId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("프로젝트 생성 - 성공 시 프로젝트 정보 반환")
    void createProject_Success() {
        // given
        Long memberId = 1L;
        ProjectReqDTO.CreateProjectReqDTO request = new ProjectReqDTO.CreateProjectReqDTO("Test Project", "Test Description");

        Member member = Member.builder()
                .email("test@test.com")
                .nickname("Tester")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();

        ReflectionTestUtils.setField(member, "id", memberId);

        Project savedProject = Project.builder()
                .title("Test Project")
                .description("Test Description")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
                
        ReflectionTestUtils.setField(savedProject, "id", 100L);
        ReflectionTestUtils.setField(savedProject, "createdAt", LocalDateTime.now());

        when(memberQueryService.findById(memberId)).thenReturn(member);
        when(projectRepository.save(any(Project.class))).thenReturn(savedProject);

        // when
        ProjectResDTO.CreateProjectResDTO result = projectCommandService.createProject(request, memberId);

        // then
        assertNotNull(result);
        assertEquals(100L, result.projectId());
        assertNotNull(result.createdAt());
        verify(memberQueryService).findById(memberId);
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    @DisplayName("프로젝트 생성 - 회원 조회가 안 될 경우 예외 발생")
    void createProject_MemberNotFound_ThrowsException() {
        // given
        Long memberId = 999L;
        ProjectReqDTO.CreateProjectReqDTO request = new ProjectReqDTO.CreateProjectReqDTO("Test Project", "Test Description");

        when(memberQueryService.findById(memberId))
                .thenThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        // when & then
        MemberException exception = assertThrows(MemberException.class,
                () -> projectCommandService.createProject(request, memberId));

        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(memberQueryService).findById(memberId);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    @DisplayName("프로젝트 수정 - 성공 시 상세 캔버스 정보 반환")
    void updateProject_Success() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        Member member = Member.builder().isActive(true).build();
        ReflectionTestUtils.setField(member, "id", memberId);

        Project project = Project.builder()
                .title("Old Title")
                .description("Old Desc")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectNodeReqDTO.NodeInfoReqDTO nodeReq = new ProjectNodeReqDTO.NodeInfoReqDTO(
                "node-1", "Web Server", "NGINX", BigDecimal.valueOf(100.0), BigDecimal.valueOf(200.0), Map.of("port", 80)
        );

        ProjectEdgeReqDTO.EdgeInfoReqDTO edgeReq = new ProjectEdgeReqDTO.EdgeInfoReqDTO(
                "node-1", "node-1"
        );

        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "New Title", "New Desc", List.of(nodeReq), List.of(edgeReq), 0L
        );

        when(projectQueryService.getWriteableProject(projectId, memberId)).thenReturn(project);
        when(projectCollaborationVersionService.issueNextVersionForFullReplace(projectId, 0L)).thenReturn(1L);
        when(projectNodeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectEdgeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ProjectResDTO.ProjectDetailResDTO result = projectCommandService.updateProject(projectId, updateRequest, memberId);

        // then
        assertNotNull(result);
        assertEquals("New Title", result.title());
        assertEquals("New Desc", result.description());
        assertEquals(1, result.nodes().size());
        assertEquals("node-1", result.nodes().get(0).nodeId());
        assertEquals(1, result.edges().size());
        assertEquals("node-1", result.edges().get(0).sourceNodeId());
        assertEquals("node-1", result.edges().get(0).targetNodeId());

        verify(projectQueryService).getWriteableProject(projectId, memberId);
        verify(projectCollaborationVersionService).issueNextVersionForFullReplace(projectId, 0L);
        verify(projectEdgeRepository).deleteByProjectId(projectId);
        verify(projectNodeRepository).deleteByProjectId(projectId);
        verify(projectNodeRepository).saveAll(anyList());
        verify(projectEdgeRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("프로젝트 수정 - nodeId가 중복되면 전용 예외 발생")
    void updateProject_DuplicateNodeId_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        Project project = Project.builder()
                .title("Project")
                .status(ProjectStatus.DRAFT)
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectNodeReqDTO.NodeInfoReqDTO firstNode = new ProjectNodeReqDTO.NodeInfoReqDTO(
                "node-1", "MySQL", "MYSQL", BigDecimal.valueOf(100.0), BigDecimal.valueOf(200.0), Map.of()
        );
        ProjectNodeReqDTO.NodeInfoReqDTO secondNode = new ProjectNodeReqDTO.NodeInfoReqDTO(
                "node-1", "Database", "MYSQL", BigDecimal.valueOf(300.0), BigDecimal.valueOf(400.0), Map.of()
        );
        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "Project", "Description", List.of(firstNode, secondNode), Collections.emptyList(), 0L
        );

        when(projectQueryService.getWriteableProject(projectId, memberId)).thenReturn(project);
        when(projectCollaborationVersionService.issueNextVersionForFullReplace(projectId, 0L)).thenReturn(1L);
        when(projectNodeRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectCommandService.updateProject(projectId, updateRequest, memberId));

        // then
        assertEquals(ProjectErrorCode.DUPLICATE_NODE_ID, exception.getCode());
        verify(projectNodeRepository).saveAll(anyList());
        verify(projectEdgeRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("프로젝트 수정 - 미래 version이면 전체 graph 저장을 시작하지 않음")
    void updateProject_FutureBaseVersion_ThrowsVersionConflict() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;
        Project project = Project.builder()
                .title("Project")
                .status(ProjectStatus.DRAFT)
                .build();
        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "Project",
                "Description",
                Collections.emptyList(),
                Collections.emptyList(),
                5L
        );
        CollaborationException versionConflict = new CollaborationException(
                CollaborationErrorCode.VERSION_CONFLICT
        );
        when(projectQueryService.getWriteableProject(projectId, memberId)).thenReturn(project);
        doThrow(versionConflict).when(projectCollaborationVersionService)
                .issueNextVersionForFullReplace(projectId, 5L);

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> projectCommandService.updateProject(projectId, updateRequest, memberId)
        );

        // then
        assertEquals(CollaborationErrorCode.VERSION_CONFLICT, exception.getCode());
        verify(projectEdgeRepository, never()).deleteByProjectId(projectId);
        verify(projectNodeRepository, never()).deleteByProjectId(projectId);
    }

    @Test
    @DisplayName("프로젝트 수정 - 본인 프로젝트가 아니거나 존재하지 않을 경우 예외 발생")
    void updateProject_ProjectNotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "New Title", "New Desc", Collections.emptyList(), Collections.emptyList(), 0L
        );

        when(projectQueryService.getWriteableProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when & then
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectCommandService.updateProject(projectId, updateRequest, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getWriteableProject(projectId, memberId);
        verify(projectEdgeRepository, never()).deleteByProjectId(anyLong());
    }

    @Test
    @DisplayName("owner 확인 후 협업·생성·graph 자식 데이터를 먼저 삭제하고 프로젝트를 삭제한다")
    void deleteProject_Success() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;
        Member owner = Member.builder().role(Role.ROLE_USER).isActive(true).build();
        Project project = Project.builder().member(owner).build();

        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);

        // when
        projectCommandService.deleteProject(projectId, memberId);

        // then
        InOrder deletion = inOrder(projectQueryService, checkpointFailureRepository, collaborationSnapshotRepository,
                collaborationOperationRepository, collaborationStateRepository,
                projectCollaboratorInvitationRepository, projectCollaboratorRepository,
                generatedFileRepository, projectHistoryRepository, projectEdgeRepository, projectNodeRepository,
                projectRepository);
        deletion.verify(projectQueryService).getOwnedProject(projectId, memberId);
        deletion.verify(checkpointFailureRepository).deleteByProjectId(projectId);
        deletion.verify(collaborationSnapshotRepository).deleteByProjectId(projectId);
        deletion.verify(collaborationOperationRepository).deleteByProjectId(projectId);
        deletion.verify(collaborationStateRepository).deleteByProjectId(projectId);
        deletion.verify(projectCollaboratorInvitationRepository).deleteByProjectId(projectId);
        deletion.verify(projectCollaboratorRepository).deleteByProjectId(projectId);
        deletion.verify(generatedFileRepository).deleteByProjectId(projectId);
        deletion.verify(projectHistoryRepository).deleteByProjectId(projectId);
        deletion.verify(projectEdgeRepository).deleteByProjectId(projectId);
        deletion.verify(projectNodeRepository).deleteByProjectId(projectId);
        deletion.verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("프로젝트 삭제 - 실패 (프로젝트 없음)")
    void deleteProject_ProjectNotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        when(projectQueryService.getOwnedProject(projectId, memberId))
            .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectCommandService.deleteProject(projectId, memberId));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectQueryService).getOwnedProject(projectId, memberId);
        verifyNoInteractions(checkpointFailureRepository, collaborationSnapshotRepository,
                collaborationOperationRepository, collaborationStateRepository,
                projectCollaboratorInvitationRepository, projectCollaboratorRepository);
        verify(generatedFileRepository, never()).deleteByProjectId(anyLong());
        verify(projectHistoryRepository, never()).deleteByProjectId(anyLong());
        verify(projectEdgeRepository, never()).deleteByProjectId(anyLong());
        verify(projectNodeRepository, never()).deleteByProjectId(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
    }

    @Test
    @DisplayName("협업 데이터 삭제가 실패하면 예외를 전파하고 이후 삭제를 진행하지 않는다")
    void deleteProject_CollaborationDeletionFails_StopsDeletion() {
        // given
        Long projectId = 100L;
        Long memberId = 1L;
        Member owner = Member.builder().role(Role.ROLE_USER).isActive(true).build();
        Project project = Project.builder().member(owner).build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("snapshot delete failed");
        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        doThrow(failure).when(collaborationSnapshotRepository).deleteByProjectId(projectId);

        // when
        DataAccessResourceFailureException thrown = assertThrows(DataAccessResourceFailureException.class,
                () -> projectCommandService.deleteProject(projectId, memberId));

        // then
        assertSame(failure, thrown);
        verify(checkpointFailureRepository).deleteByProjectId(projectId);
        verifyNoInteractions(collaborationOperationRepository, collaborationStateRepository,
                projectCollaboratorInvitationRepository, projectCollaboratorRepository,
                generatedFileRepository, projectHistoryRepository,
                projectEdgeRepository, projectNodeRepository, projectRepository);
    }

    @Test
    @DisplayName("초대 삭제가 실패하면 이후 종속 데이터와 프로젝트 삭제를 중단한다")
    void deleteProject_InvitationDeletionFails_StopsDeletion() {
        // given
        Long projectId = 100L;
        Long memberId = 1L;
        Project project = Project.builder()
                .member(Member.builder().role(Role.ROLE_USER).isActive(true).build())
                .build();
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException(
                "invitation delete failed"
        );
        when(projectQueryService.getOwnedProject(projectId, memberId)).thenReturn(project);
        doThrow(failure).when(projectCollaboratorInvitationRepository).deleteByProjectId(projectId);

        // when
        DataAccessResourceFailureException thrown = assertThrows(
                DataAccessResourceFailureException.class,
                () -> projectCommandService.deleteProject(projectId, memberId)
        );

        // then
        assertSame(failure, thrown);
        verify(checkpointFailureRepository).deleteByProjectId(projectId);
        verify(collaborationSnapshotRepository).deleteByProjectId(projectId);
        verify(collaborationOperationRepository).deleteByProjectId(projectId);
        verify(collaborationStateRepository).deleteByProjectId(projectId);
        verify(projectCollaboratorInvitationRepository).deleteByProjectId(projectId);
        verifyNoInteractions(
                projectCollaboratorRepository,
                generatedFileRepository,
                projectHistoryRepository,
                projectEdgeRepository,
                projectNodeRepository,
                projectRepository
        );
    }

    @Test
    @DisplayName("guest는 프로젝트를 삭제할 수 없다")
    void deleteProject_GuestOwner_ThrowsAccessDenied() {
        // given
        Long projectId = 100L;
        Long guestId = 99L;
        Member guest = Member.builder().role(Role.ROLE_GUEST).isActive(true).build();
        Project project = Project.builder().member(guest).build();
        when(projectQueryService.getOwnedProject(projectId, guestId)).thenReturn(project);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> projectCommandService.deleteProject(projectId, guestId)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(
                checkpointFailureRepository,
                collaborationSnapshotRepository,
                collaborationOperationRepository,
                collaborationStateRepository,
                projectCollaboratorInvitationRepository,
                projectCollaboratorRepository,
                generatedFileRepository,
                projectHistoryRepository,
                projectEdgeRepository,
                projectNodeRepository,
                projectRepository
        );
    }
}
