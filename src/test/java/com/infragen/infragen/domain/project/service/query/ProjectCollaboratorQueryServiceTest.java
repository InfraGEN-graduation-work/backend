package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorQueryServiceTest {
    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectCollaboratorQueryService service;

    @Test
    @DisplayName("owner project의 collaborator 목록을 반환한다")
    void getAll_OwnerProject_ReturnsCollaboratorList() {
        // given
        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .member(Member.builder().role(Role.ROLE_USER).isActive(true).build())
                .build();
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(project)
                .member(Member.builder().nickname("editor").isActive(true).build())
                .role(ProjectCollaboratorRole.EDITOR)
                .build();
        when(collaboratorRepository.findAllByProjectId(1L)).thenReturn(List.of(collaborator));

        // when
        var result = service.getAll(1L, 10L);

        // then
        assertEquals(1, result.collaborators().size());
        assertEquals(ProjectCollaboratorRole.EDITOR, result.collaborators().get(0).role());
        verify(projectAccessService).requireReadAccess(1L, 10L);
    }

    @Test
    @DisplayName("guest project owner는 guest collaborator 목록을 조회한다")
    void getAll_GuestOwner_ReturnsGuestCollaborators() {
        // given
        Member guestOwner = Member.builder().role(Role.ROLE_GUEST).isActive(true).build();
        Project guestProject = Project.builder()
                .title("guest project")
                .status(ProjectStatus.DRAFT)
                .member(guestOwner)
                .build();
        Member invitedGuest = Member.builder()
                .nickname("invited guest")
                .role(Role.ROLE_GUEST)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(invitedGuest, "id", 20L);
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(guestProject)
                .member(invitedGuest)
                .role(ProjectCollaboratorRole.EDITOR)
                .build();
        when(collaboratorRepository.findAllByProjectId(1L)).thenReturn(List.of(collaborator));

        // when
        var result = service.getAll(1L, 99L);

        // then
        assertEquals(20L, result.collaborators().getFirst().memberId());
        assertEquals(ProjectCollaboratorRole.EDITOR, result.collaborators().getFirst().role());
        verify(projectAccessService).requireReadAccess(1L, 99L);
    }

    @Test
    @DisplayName("EDITOR collaborator는 실제 read access 확인을 통과해 목록을 조회한다")
    void getAll_EditorWithRealAccessService_ReturnsCollaboratorList() {
        // given
        Long projectId = 1L;
        Long editorId = 11L;
        ProjectCollaborator editor = collaborator(
                editorId,
                "editor",
                ProjectCollaboratorRole.EDITOR
        );
        when(projectRepository.findByIdAndMemberId(projectId, editorId))
                .thenReturn(Optional.empty());
        when(collaboratorRepository.existsByProjectIdAndMemberId(projectId, editorId))
                .thenReturn(true);
        when(collaboratorRepository.findAllByProjectId(projectId)).thenReturn(List.of(editor));

        // when
        var result = queryServiceWithRealAccessService().getAll(projectId, editorId);

        // then
        assertEquals(1, result.collaborators().size());
        assertEquals(editorId, result.collaborators().getFirst().memberId());
        assertEquals(ProjectCollaboratorRole.EDITOR, result.collaborators().getFirst().role());
    }

    @Test
    @DisplayName("VIEWER collaborator는 실제 read access 확인을 통과해 목록을 조회한다")
    void getAll_ViewerWithRealAccessService_ReturnsCollaboratorList() {
        // given
        Long projectId = 1L;
        Long viewerId = 12L;
        ProjectCollaborator viewer = collaborator(
                viewerId,
                "viewer",
                ProjectCollaboratorRole.VIEWER
        );
        when(projectRepository.findByIdAndMemberId(projectId, viewerId))
                .thenReturn(Optional.empty());
        when(collaboratorRepository.existsByProjectIdAndMemberId(projectId, viewerId))
                .thenReturn(true);
        when(collaboratorRepository.findAllByProjectId(projectId)).thenReturn(List.of(viewer));

        // when
        var result = queryServiceWithRealAccessService().getAll(projectId, viewerId);

        // then
        assertEquals(1, result.collaborators().size());
        assertEquals(viewerId, result.collaborators().getFirst().memberId());
        assertEquals(ProjectCollaboratorRole.VIEWER, result.collaborators().getFirst().role());
    }

    @Test
    @DisplayName("프로젝트 비참여자는 collaborator 목록을 조회할 수 없다")
    void getAll_NonCollaborator_RejectsBeforeLoadingList() {
        // given
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(1L, 30L);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.getAll(1L, 30L)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verify(collaboratorRepository, never()).findAllByProjectId(1L);
    }

    private ProjectCollaboratorQueryService queryServiceWithRealAccessService() {
        return new ProjectCollaboratorQueryService(
                new ProjectAccessService(projectRepository, collaboratorRepository),
                collaboratorRepository
        );
    }

    private ProjectCollaborator collaborator(
            Long memberId,
            String nickname,
            ProjectCollaboratorRole collaboratorRole
    ) {
        Member member = Member.builder()
                .nickname(nickname)
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);

        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .member(Member.builder().role(Role.ROLE_USER).isActive(true).build())
                .build();

        return ProjectCollaborator.builder()
                .project(project)
                .member(member)
                .role(collaboratorRole)
                .build();
    }
}
