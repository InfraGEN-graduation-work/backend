package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.MemberErrorCode;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.dto.request.ProjectNodeReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectEdgeReqDTO;
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
    private MemberQueryService memberQueryService;

    @InjectMocks
    private ProjectCommandService projectCommandService;

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
                "Web Server", "NGINX", BigDecimal.valueOf(100.0), BigDecimal.valueOf(200.0), Map.of("port", 80)
        );

        ProjectEdgeReqDTO.EdgeInfoReqDTO edgeReq = new ProjectEdgeReqDTO.EdgeInfoReqDTO(
                "Web Server", "Web Server"
        );

        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "New Title", "New Desc", List.of(nodeReq), List.of(edgeReq)
        );

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.of(project));

        // when
        ProjectResDTO.ProjectDetailResDTO result = projectCommandService.updateProject(projectId, updateRequest, memberId);

        // then
        assertNotNull(result);
        assertEquals("New Title", result.title());
        assertEquals("New Desc", result.description());
        assertEquals(1, result.nodes().size());
        assertEquals(1, result.edges().size());

        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectEdgeRepository).deleteByProjectId(projectId);
        verify(projectNodeRepository).deleteByProjectId(projectId);
        verify(projectNodeRepository).saveAll(anyList());
        verify(projectEdgeRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("프로젝트 수정 - 본인 프로젝트가 아니거나 존재하지 않을 경우 예외 발생")
    void updateProject_ProjectNotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        ProjectReqDTO.UpdateProjectReqDTO updateRequest = new ProjectReqDTO.UpdateProjectReqDTO(
                "New Title", "New Desc", Collections.emptyList(), Collections.emptyList()
        );

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.empty());

        // when & then
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectCommandService.updateProject(projectId, updateRequest, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectEdgeRepository, never()).deleteByProjectId(anyLong());
    }

    @Test
    @DisplayName("프로젝트 삭제 - 성공")
    void deleteProject_Success() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;
        Project project = Project.builder().build();

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.of(project));

        // when
        projectCommandService.deleteProject(projectId, memberId);

        // then
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectEdgeRepository).deleteByProjectId(projectId);
        verify(projectNodeRepository).deleteByProjectId(projectId);
        verify(projectRepository).delete(project);
    }

    @Test
    @DisplayName("프로젝트 삭제 - 실패 (프로젝트 없음)")
    void deleteProject_ProjectNotFound_ThrowsException() {
        // given
        Long memberId = 1L;
        Long projectId = 100L;

        when(projectRepository.findByIdAndMemberId(projectId, memberId)).thenReturn(Optional.empty());

        // when & then
        ProjectException exception = assertThrows(ProjectException.class,
                () -> projectCommandService.deleteProject(projectId, memberId));

        assertEquals(ProjectErrorCode.PROJECT_NOT_FOUND, exception.getCode());
        verify(projectRepository).findByIdAndMemberId(projectId, memberId);
        verify(projectEdgeRepository, never()).deleteByProjectId(anyLong());
        verify(projectNodeRepository, never()).deleteByProjectId(anyLong());
        verify(projectRepository, never()).delete(any(Project.class));
    }
}
