package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAccessServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectCollaboratorRepository projectCollaboratorRepository;

    @InjectMocks
    private ProjectAccessService projectAccessService;

    @Test
    void owner_canReadAndWriteProject() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findByIdAndMemberId(projectId, memberId))
                .thenReturn(Optional.of(project()));

        // when & then
        assertDoesNotThrow(() -> projectAccessService.requireReadAccess(projectId, memberId));
        assertDoesNotThrow(() -> projectAccessService.requireWriteAccess(projectId, memberId));
        verify(projectRepository, times(2)).findByIdAndMemberId(projectId, memberId);
    }

    @Test
    void editor_canReadAndWriteProject() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findByIdAndMemberId(projectId, memberId))
                .thenReturn(Optional.empty());
        when(projectCollaboratorRepository.existsByProjectIdAndMemberId(projectId, memberId))
                .thenReturn(true);
        when(projectCollaboratorRepository.existsByProjectIdAndMemberIdAndRole(
                projectId, memberId, ProjectCollaboratorRole.EDITOR))
                .thenReturn(true);

        // when & then
        assertDoesNotThrow(() -> projectAccessService.requireReadAccess(projectId, memberId));
        assertDoesNotThrow(() -> projectAccessService.requireWriteAccess(projectId, memberId));
    }

    @Test
    void viewer_canReadButCannotWriteProject() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findByIdAndMemberId(projectId, memberId))
                .thenReturn(Optional.empty());
        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> projectAccessService.requireWriteAccess(projectId, memberId)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
    }

    @Test
    void unrelatedMember_cannotReadProject() {
        // given
        Long projectId = 1L;
        Long memberId = 2L;
        when(projectRepository.findByIdAndMemberId(projectId, memberId))
                .thenReturn(Optional.empty());
        when(projectCollaboratorRepository.existsByProjectIdAndMemberId(projectId, memberId))
                .thenReturn(false);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> projectAccessService.requireReadAccess(projectId, memberId)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
    }

    private Project project() {
        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .build();
        ReflectionTestUtils.setField(project, "id", 1L);
        return project;
    }
}
