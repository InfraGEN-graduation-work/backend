package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.global.enums.ComponentType;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import com.infragen.infragen.domain.project.repository.projection.ProjectAccessPreview;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectNodeRepository projectNodeRepository;

    @Mock
    private ProjectEdgeRepository projectEdgeRepository;

    @Mock
    private ProjectAccessService projectAccessService;

    @InjectMocks
    private ProjectQueryService projectQueryService;

    @Test
    @DisplayName("프로젝트 목록 조회 - 최신순 조회 성공")
    void getProjects_Success() {
        // given
        Long memberId = 1L;
        Member member = Member.builder()
                .email("test@test.com")
                .nickname("Tester")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);

        // 최신 프로젝트 (id: 101, 10분 전 생성)
        Project recentProject = Project.builder()
                .title("Recent Project")
                .description("Recent Desc")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
        ReflectionTestUtils.setField(recentProject, "id", 101L);
        ReflectionTestUtils.setField(recentProject, "createdAt", LocalDateTime.of(2026, 9, 16, 12, 0));

        // 이전 프로젝트 (id: 100, 1일 전 생성)
        Project oldProject = Project.builder()
                .title("Old Project")
                .description("Old Desc")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
        ReflectionTestUtils.setField(oldProject, "id", 100L);
        ReflectionTestUtils.setField(oldProject, "createdAt", LocalDateTime.of(2026, 9, 15, 12, 0));

        // repository는 최신순(Recent -> Old)으로 정렬된 데이터를 리턴하도록
        when(projectRepository.findAllAccessibleByMemberId(memberId))
                .thenReturn(List.of(preview(recentProject, "OWNER"), preview(oldProject, "OWNER")));

        // when
        ProjectResDTO.ProjectPreviewListResDTO result = projectQueryService.getProjects(memberId);

        // then
        assertNotNull(result);
        assertEquals(2, result.projectList().size());

        // 첫 번째 원소가 최신 프로젝트여야 함
        assertEquals(101L, result.projectList().get(0).projectId());
        assertEquals("Recent Project", result.projectList().get(0).title());

        // 두 번째 원소가 이전 프로젝트여야 함
        assertEquals(100L, result.projectList().get(1).projectId());
        assertEquals("Old Project", result.projectList().get(1).title());

        assertEquals("OWNER", result.projectList().getFirst().accessRole());
        verify(projectRepository).findAllAccessibleByMemberId(memberId);
    }

    @Test
    @DisplayName("프로젝트 목록 조회 - 프로젝트가 없는 경우 빈 목록 반환")
    void getProjects_EmptyList_Success() {
        // given
        Long memberId = 1L;
        when(projectRepository.findAllAccessibleByMemberId(memberId))
                .thenReturn(Collections.emptyList());

        // when
        ProjectResDTO.ProjectPreviewListResDTO result = projectQueryService.getProjects(memberId);

        // then
        assertNotNull(result);
        assertTrue(result.projectList().isEmpty());
        verify(projectRepository).findAllAccessibleByMemberId(memberId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EDITOR", "VIEWER"})
    @DisplayName("참여 프로젝트도 접근 역할과 기존 preview 필드를 함께 반환한다")
    void getProjects_Collaborator_ReturnsAccessRole(String accessRole) {
        // given
        Project project = Project.builder().title("Shared").description("Team")
                .status(ProjectStatus.DRAFT).build();
        ReflectionTestUtils.setField(project, "id", 20L);
        ReflectionTestUtils.setField(project, "createdAt", LocalDateTime.of(2026, 9, 16, 12, 0));
        when(projectRepository.findAllAccessibleByMemberId(7L))
                .thenReturn(List.of(preview(project, accessRole)));

        // when
        var result = projectQueryService.getProjects(7L).projectList().getFirst();

        // then
        assertAll(
                () -> assertEquals(20L, result.projectId()),
                () -> assertEquals("Shared", result.title()),
                () -> assertEquals("Team", result.description()),
                () -> assertEquals("DRAFT", result.status()),
                () -> assertEquals(project.getCreatedAt(), result.createdAt()),
                () -> assertEquals(accessRole, result.accessRole())
        );
        verifyNoInteractions(projectNodeRepository, projectEdgeRepository);
    }

    private ProjectAccessPreview preview(Project project, String accessRole) {
        return new SpelAwareProxyProjectionFactory().createProjection(ProjectAccessPreview.class, Map.of(
                "projectId", project.getId(), "title", project.getTitle(),
                "description", project.getDescription(), "status", project.getStatus(),
                "createdAt", project.getCreatedAt(), "accessRole", accessRole));
    }

    @Test
    @DisplayName("프로젝트 상세 조회 - 성공")
    void getProjectDetail_Success() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        Member member = Member.builder()
                .email("test@test.com")
                .nickname("Tester")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);

        Project project = Project.builder()
                .title("Target Project")
                .description("Target Desc")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        ProjectNode node = ProjectNode.builder()
                .nodeName("Web Server")
                .componentType(ComponentType.NGINX)
                .positionX(BigDecimal.valueOf(100.0))
                .positionY(BigDecimal.valueOf(200.0))
                .properties(Map.of("port", 80))
                .project(project)
                .build();
        ReflectionTestUtils.setField(node, "id", 1L);

        ProjectEdge edge = ProjectEdge.builder()
                .project(project)
                .sourceNode(node)
                .targetNode(node)
                .build();
        ReflectionTestUtils.setField(edge, "id", 2L);

        doNothing().when(projectAccessService).requireReadAccess(projectId, memberId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectNodeRepository.findAllByProjectId(projectId)).thenReturn(List.of(node));
        when(projectEdgeRepository.findAllByProjectId(projectId)).thenReturn(List.of(edge));

        // when
        ProjectResDTO.ProjectDetailResDTO result = projectQueryService.getProjectDetail(projectId, memberId);

        // then
        assertNotNull(result);
        assertEquals(projectId, result.projectId());
        assertEquals("Target Project", result.title());
        assertEquals(1, result.nodes().size());
        assertEquals("Web Server", result.nodes().get(0).nodeName());
        assertEquals(1, result.edges().size());

        verify(projectAccessService).requireReadAccess(projectId, memberId);
        verify(projectRepository).findById(projectId);
        verify(projectNodeRepository).findAllByProjectId(projectId);
        verify(projectEdgeRepository).findAllByProjectId(projectId);
    }

    @Test
    @DisplayName("소유 프로젝트 조회 - 성공")
    void getOwnedProject_Success() {
        Long memberId = 1L;
        Long projectId = 100L;

        Project project = Project.builder()
            .title("Owned Project")
            .build();
        ReflectionTestUtils.setField(project, "id", projectId);

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.of(project));

        Project result = projectQueryService.getOwnedProject(projectId, memberId);

        assertEquals(projectId, result.getId());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
    }

    @Test
    @DisplayName("소유 프로젝트 조회 - 없거나 권한 불일치 시 예외")
    void getOwnedProject_NotFound_ThrowsException() {
        Long memberId = 1L;
        Long projectId = 100L;

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.empty());

        ProjectException exception = assertThrows(ProjectException.class,
            () -> projectQueryService.getOwnedProject(projectId, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
    }

    @Test
    @DisplayName("쓰기 권한이 있는 EDITOR도 저장용 프로젝트를 조회한다")
    void getWriteableProject_Editor_ReturnsProject() {
        // given
        Long projectId = 100L;
        Long editorId = 7L;
        Project project = Project.builder().title("Shared").build();
        ReflectionTestUtils.setField(project, "id", projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        // when
        Project result = projectQueryService.getWriteableProject(projectId, editorId);

        // then
        assertEquals(project, result);
        verify(projectAccessService).requireWriteAccess(projectId, editorId);
        verify(projectRepository).findById(projectId);
    }

    @Test
    @DisplayName("쓰기 권한이 없는 VIEWER는 프로젝트 graph 조회 전에 거부한다")
    void getWriteableProject_Viewer_RejectsBeforeProjectLookup() {
        // given
        Long projectId = 100L;
        Long viewerId = 8L;
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireWriteAccess(projectId, viewerId);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> projectQueryService.getWriteableProject(projectId, viewerId)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(projectAccessService).requireWriteAccess(projectId, viewerId);
        verify(projectRepository, never()).findById(projectId);
    }

    @Test
    @DisplayName("프로젝트 상세 조회 - 존재하지 않거나 타인 프로젝트 조회 시 예외 발생")
    void getProjectDetail_NotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        doNothing().when(projectAccessService).requireReadAccess(projectId, memberId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectQueryService.getProjectDetail(projectId, memberId));

        // then
        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectAccessService).requireReadAccess(projectId, memberId);
        verify(projectRepository).findById(projectId);
        verify(projectNodeRepository, never()).findAllByProjectId(anyLong());
    }

    @Test
    @DisplayName("협업자 읽기 권한이 있으면 상세 프로젝트를 조회한다")
    void getProjectDetail_CollaboratorReadAccess_ReturnsProject() {
        // given
        Long projectId = 100L;
        Long memberId = 7L;
        Project project = Project.builder()
                .title("Shared Project")
                .description("Shared Desc")
                .status(ProjectStatus.DRAFT)
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);
        doNothing().when(projectAccessService).requireReadAccess(projectId, memberId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(projectNodeRepository.findAllByProjectId(projectId)).thenReturn(List.of());
        when(projectEdgeRepository.findAllByProjectId(projectId)).thenReturn(List.of());

        // when
        ProjectResDTO.ProjectDetailResDTO result = projectQueryService.getProjectDetail(projectId, memberId);

        // then
        assertAll(
                () -> assertEquals(projectId, result.projectId()),
                () -> assertEquals("Shared Project", result.title()),
                () -> assertTrue(result.nodes().isEmpty()),
                () -> assertTrue(result.edges().isEmpty())
        );
        verify(projectAccessService).requireReadAccess(projectId, memberId);
        verify(projectRepository).findById(projectId);
    }

    @Test
    @DisplayName("읽기 권한이 없으면 상세 프로젝트를 조회하지 않는다")
    void getProjectDetail_WithoutReadAccess_ThrowsException() {
        // given
        Long projectId = 100L;
        Long memberId = 9L;
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(projectId, memberId);

        // when
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectQueryService.getProjectDetail(projectId, memberId));

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(projectAccessService).requireReadAccess(projectId, memberId);
        verifyNoInteractions(projectRepository, projectNodeRepository, projectEdgeRepository);
    }
}
