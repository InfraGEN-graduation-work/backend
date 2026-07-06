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
        ReflectionTestUtils.setField(recentProject, "createdAt", LocalDateTime.now().minusMinutes(10));

        // 이전 프로젝트 (id: 100, 1일 전 생성)
        Project oldProject = Project.builder()
                .title("Old Project")
                .description("Old Desc")
                .status(ProjectStatus.DRAFT)
                .member(member)
                .build();
        ReflectionTestUtils.setField(oldProject, "id", 100L);
        ReflectionTestUtils.setField(oldProject, "createdAt", LocalDateTime.now().minusDays(1));

        // repository는 최신순(Recent -> Old)으로 정렬된 데이터를 리턴하도록
        when(projectRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId))
                .thenReturn(List.of(recentProject, oldProject));

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

        verify(projectRepository).findAllByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Test
    @DisplayName("프로젝트 목록 조회 - 프로젝트가 없는 경우 빈 목록 반환")
    void getProjects_EmptyList_Success() {
        // given
        Long memberId = 1L;
        when(projectRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId))
                .thenReturn(Collections.emptyList());

        // when
        ProjectResDTO.ProjectPreviewListResDTO result = projectQueryService.getProjects(memberId);

        // then
        assertNotNull(result);
        assertTrue(result.projectList().isEmpty());
        verify(projectRepository).findAllByMemberIdOrderByCreatedAtDesc(memberId);
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

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.of(project));
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

        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
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
    @DisplayName("프로젝트 상세 조회 - 존재하지 않거나 타인 프로젝트 조회 시 예외 발생")
    void getProjectDetail_NotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.empty());

        // when & then
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectQueryService.getProjectDetail(projectId, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectNodeRepository, never()).findAllByProjectId(anyLong());
    }
}
