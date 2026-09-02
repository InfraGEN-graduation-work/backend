package com.infragen.infragen.domain.project.service.query;

import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessService {
    private final ProjectRepository projectRepository;
    private final ProjectCollaboratorRepository projectCollaboratorRepository;

    /**
     * member가 project의 graph를 읽을 수 있는지 확인한다.
     *
     * @param projectId 확인할 project 식별자
     * @param memberId 확인할 member 식별자
     * @throws ProjectException owner 또는 collaborator가 아닌 경우
     */
    public void requireReadAccess(Long projectId, Long memberId) {
        boolean owner = projectRepository.findByIdAndMemberId(projectId, memberId).isPresent();
        if (owner) {
            return;
        }

        if (!projectCollaboratorRepository.existsByProjectIdAndMemberId(projectId, memberId)) {
            throw new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED);
        }
    }

    /**
     * member가 project의 graph operation을 전송할 수 있는지 확인한다.
     *
     * @param projectId 확인할 project 식별자
     * @param memberId 확인할 member 식별자
     * @throws ProjectException EDITOR 이상 권한이 없는 경우
     */
    public void requireWriteAccess(Long projectId, Long memberId) {
        boolean owner = projectRepository.findByIdAndMemberId(projectId, memberId).isPresent();
        if (owner) {
            return;
        }

        if (!projectCollaboratorRepository.existsByProjectIdAndMemberIdAndRole(
                projectId,
                memberId,
                ProjectCollaboratorRole.EDITOR
        )) {
            throw new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED);
        }
    }
}
