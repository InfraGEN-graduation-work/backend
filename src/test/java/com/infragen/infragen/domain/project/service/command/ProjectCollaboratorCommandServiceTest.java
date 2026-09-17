package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorCommandServiceTest {
    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private MemberQueryService memberQueryService;

    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;

    @InjectMocks
    private ProjectCollaboratorCommandService service;

    @Test
    @DisplayName("active member를 collaborator로 등록한다")
    void add_ActiveMember_SavesCollaborator() {
        // given
        Project project = project(1L, 10L);
        Member member = member(20L, "editor");
        ProjectCollaboratorReqDTO.Add request = new ProjectCollaboratorReqDTO.Add(
                20L,
                ProjectCollaboratorRole.EDITOR
        );
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project);
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L)).thenReturn(Optional.empty());
        when(memberQueryService.findById(20L)).thenReturn(member);
        when(collaboratorRepository.save(any(ProjectCollaborator.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = service.add(1L, 10L, request);

        // then
        assertEquals(20L, result.memberId());
        assertEquals(ProjectCollaboratorRole.EDITOR, result.role());
        verify(collaboratorRepository).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("이미 등록된 collaborator는 중복 오류를 발생시킨다")
    void add_ExistingCollaborator_ThrowsConflict() {
        // given
        Project project = project(1L, 10L);
        ProjectCollaboratorReqDTO.Add request = new ProjectCollaboratorReqDTO.Add(
                20L,
                ProjectCollaboratorRole.VIEWER
        );
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project);
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L))
                .thenReturn(Optional.of(ProjectCollaborator.builder().build()));

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.add(1L, 10L, request)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_ALREADY_EXISTS, exception.getCode());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("project owner를 collaborator로 등록할 수 없다")
    void add_Owner_ThrowsOwnerConflict() {
        // given
        Project project = project(1L, 10L);
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.add(
                        1L,
                        10L,
                        new ProjectCollaboratorReqDTO.Add(10L, ProjectCollaboratorRole.EDITOR)
                )
        );

        // then
        assertEquals(ProjectErrorCode.OWNER_CANNOT_BE_COLLABORATOR, exception.getCode());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
    }

    @Test
    @DisplayName("기존 collaborator의 역할을 변경한다")
    void changeRole_ExistingCollaborator_ChangesRole() {
        // given
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(project(1L, 10L))
                .member(member(20L, "viewer"))
                .role(ProjectCollaboratorRole.VIEWER)
                .build();
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(collaborator.getProject());
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L))
                .thenReturn(Optional.of(collaborator));

        // when
        service.changeRole(
                1L,
                10L,
                20L,
                new ProjectCollaboratorReqDTO.ChangeRole(ProjectCollaboratorRole.EDITOR)
        );

        // then
        assertEquals(ProjectCollaboratorRole.EDITOR, collaborator.getRole());
    }

    @Test
    @DisplayName("없는 collaborator를 삭제하면 조회 오류를 발생시킨다")
    void delete_MissingCollaborator_ThrowsNotFound() {
        // given
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project(1L, 10L));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(1L, 20L)).thenReturn(0L);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.delete(1L, 10L, 20L)
        );

        // then
        assertEquals(ProjectErrorCode.COLLABORATOR_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("guest project owner는 다른 guest를 초대하고 관리할 수 있다")
    void guestOwner_CanManageGuestCollaborators() {
        // given
        Project guestProject = project(1L, 99L, Role.ROLE_GUEST);
        Member guestCollaborator = member(20L, "guest collaborator", Role.ROLE_GUEST);
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(guestProject)
                .member(guestCollaborator)
                .role(ProjectCollaboratorRole.VIEWER)
                .build();
        when(projectQueryService.getOwnedProject(1L, 99L)).thenReturn(guestProject);
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L))
                .thenReturn(Optional.empty(), Optional.of(collaborator));
        when(memberQueryService.findById(20L)).thenReturn(guestCollaborator);
        when(collaboratorRepository.save(any(ProjectCollaborator.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(1L, 20L)).thenReturn(1L);

        // when
        var added = service.add(
                1L,
                99L,
                new ProjectCollaboratorReqDTO.Add(20L, ProjectCollaboratorRole.EDITOR)
        );
        service.changeRole(
                1L,
                99L,
                20L,
                new ProjectCollaboratorReqDTO.ChangeRole(ProjectCollaboratorRole.EDITOR)
        );
        service.delete(1L, 99L, 20L);

        // then
        assertEquals(20L, added.memberId());
        assertEquals(ProjectCollaboratorRole.EDITOR, added.role());
        assertEquals(ProjectCollaboratorRole.EDITOR, collaborator.getRole());
        verify(collaboratorRepository).deleteByProjectIdAndMemberId(1L, 20L);
    }

    @Test
    @DisplayName("guest project owner는 일반 회원을 collaborator로 초대하거나 관리할 수 없다")
    void guestOwner_CannotManageRegularMember() {
        // given
        Project guestProject = project(1L, 99L, Role.ROLE_GUEST);
        Member regularMember = member(20L, "regular member", Role.ROLE_USER);
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(guestProject)
                .member(regularMember)
                .role(ProjectCollaboratorRole.VIEWER)
                .build();
        when(projectQueryService.getOwnedProject(1L, 99L)).thenReturn(guestProject);
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L))
                .thenReturn(Optional.empty(), Optional.of(collaborator));
        when(memberQueryService.findById(20L)).thenReturn(regularMember);

        // when
        ProjectException addException = assertThrows(
                ProjectException.class,
                () -> service.add(
                        1L,
                        99L,
                        new ProjectCollaboratorReqDTO.Add(20L, ProjectCollaboratorRole.EDITOR)
                )
        );
        ProjectException changeException = assertThrows(
                ProjectException.class,
                () -> service.changeRole(
                        1L,
                        99L,
                        20L,
                        new ProjectCollaboratorReqDTO.ChangeRole(ProjectCollaboratorRole.EDITOR)
                )
        );
        ProjectException deleteException = assertThrows(
                ProjectException.class,
                () -> service.delete(1L, 99L, 20L)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, addException.getCode());
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, changeException.getCode());
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, deleteException.getCode());
        verify(collaboratorRepository, never()).save(any(ProjectCollaborator.class));
        verify(collaboratorRepository, never()).deleteByProjectIdAndMemberId(1L, 20L);
    }

    private Project project(Long projectId, Long ownerId) {
        return project(projectId, ownerId, Role.ROLE_USER);
    }

    private Project project(Long projectId, Long ownerId, Role role) {
        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .member(member(ownerId, "owner", role))
                .build();
        ReflectionTestUtils.setField(project, "id", projectId);
        return project;
    }

    private Member member(Long memberId, String nickname) {
        return member(memberId, nickname, Role.ROLE_USER);
    }

    private Member member(Long memberId, String nickname, Role role) {
        Member member = Member.builder()
                .nickname(nickname)
                .isActive(true)
                .role(role)
                .build();
        ReflectionTestUtils.setField(member, "id", memberId);
        return member;
    }
}
