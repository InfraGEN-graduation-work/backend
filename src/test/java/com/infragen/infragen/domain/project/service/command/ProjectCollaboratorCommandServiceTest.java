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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorCommandServiceTest {
    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;

    @InjectMocks
    private ProjectCollaboratorCommandService service;

    @Test
    @DisplayName("숫자 memberId 직접 등록 경로는 일반 회원 owner에게도 거부한다")
    void rejectMemberIdAddition_OwnedProject_ThrowsAccessDenied() {
        // given
        Project project = project(1L, 10L);
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.rejectMemberIdAddition(1L, 10L)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(collaboratorRepository);
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
    @DisplayName("guest project owner는 기존 guest collaborator를 계속 관리할 수 있다")
    void guestOwner_CanManageExistingGuestCollaborators() {
        // given
        Project guestProject = project(1L, 99L, Role.ROLE_GUEST);
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(guestProject)
                .member(member(20L, "guest collaborator", Role.ROLE_GUEST))
                .role(ProjectCollaboratorRole.VIEWER)
                .build();
        when(projectQueryService.getOwnedProject(1L, 99L)).thenReturn(guestProject);
        when(collaboratorRepository.findByProjectIdAndMemberId(1L, 20L))
                .thenReturn(Optional.of(collaborator));
        when(collaboratorRepository.deleteByProjectIdAndMemberId(1L, 20L)).thenReturn(1L);

        // when
        service.changeRole(
                1L,
                99L,
                20L,
                new ProjectCollaboratorReqDTO.ChangeRole(ProjectCollaboratorRole.EDITOR)
        );
        service.delete(1L, 99L, 20L);

        // then
        assertEquals(ProjectCollaboratorRole.EDITOR, collaborator.getRole());
        verify(collaboratorRepository).deleteByProjectIdAndMemberId(1L, 20L);
    }

    @Test
    @DisplayName("guest project owner는 일반 회원 collaborator를 관리할 수 없다")
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
                .thenReturn(Optional.of(collaborator));

        // when
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
