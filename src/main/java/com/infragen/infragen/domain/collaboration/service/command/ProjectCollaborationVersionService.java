package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationVersionService {
    private final ProjectRepository projectRepository;
    private final ProjectCollaborationStateRepository stateRepository;

    /**
     * client version을 확인한 뒤 project의 다음 collaboration serverVersion을 발급한다.
     *
     * @param projectId version을 발급할 project 식별자
     * @param baseVersion client가 알고 있는 project version
     * @return 발급된 serverVersion
     * @throws ProjectException project가 존재하지 않는 경우
     * @throws CollaborationException client version이 현재 serverVersion보다 앞서는 경우
     */
    @Transactional
    public Long issueNextVersion(Long projectId, Long baseVersion) {
        ProjectCollaborationState state = stateRepository.findByProjectIdForUpdate(projectId)
                .orElseGet(() -> createInitialState(projectId));

        if (baseVersion > state.getServerVersion()) {
            throw new CollaborationException(CollaborationErrorCode.VERSION_CONFLICT);
        }

        state.advanceServerVersion();
        return state.getServerVersion();
    }

    private ProjectCollaborationState createInitialState(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        return stateRepository.save(new ProjectCollaborationState(project));
    }
}
