package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.project.converter.ProjectCollaboratorConverter;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorQueryService {
    private final ProjectQueryService projectQueryService;
    private final ProjectCollaboratorRepository collaboratorRepository;

    /**
     * owner가 관리하는 project의 collaborator 목록을 조회한다.
     */
    @Transactional(readOnly = true)
    public ProjectCollaboratorResDTO.ListResult getAll(Long projectId, Long ownerId) {
        projectQueryService.getOwnedProject(projectId, ownerId);
        return ProjectCollaboratorConverter.toListResult(
                collaboratorRepository.findAllByProjectId(projectId)
        );
    }
}
