package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.enums.ProjectStatus;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCollaboratorQueryServiceTest {
    @Mock
    private ProjectQueryService projectQueryService;

    @Mock
    private ProjectCollaboratorRepository collaboratorRepository;

    @InjectMocks
    private ProjectCollaboratorQueryService service;

    @Test
    @DisplayName("owner project의 collaborator 목록을 반환한다")
    void getAll_OwnerProject_ReturnsCollaboratorList() {
        // given
        Project project = Project.builder()
                .title("project")
                .status(ProjectStatus.DRAFT)
                .build();
        ProjectCollaborator collaborator = ProjectCollaborator.builder()
                .project(project)
                .member(Member.builder().nickname("editor").isActive(true).build())
                .role(ProjectCollaboratorRole.EDITOR)
                .build();
        when(projectQueryService.getOwnedProject(1L, 10L)).thenReturn(project);
        when(collaboratorRepository.findAllByProjectId(1L)).thenReturn(List.of(collaborator));

        // when
        var result = service.getAll(1L, 10L);

        // then
        assertEquals(1, result.collaborators().size());
        assertEquals(ProjectCollaboratorRole.EDITOR, result.collaborators().get(0).role());
    }
}
