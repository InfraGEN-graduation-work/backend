package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationOperation;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationVersionService {
    private final ProjectRepository projectRepository;
    private final ProjectCollaborationStateRepository stateRepository;
    private final ProjectCollaborationOperationRepository operationRepository;

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
        return issueNextVersion(projectId, baseVersion, null, false).serverVersion();
    }

    /**
     * 전체 graph 교체 전에 client가 알고 있는 version과 현재 version이 같은지 확인하고 다음 version을 발급한다.
     *
     * @param projectId version을 발급할 project 식별자
     * @param baseVersion 전체 교체 요청이 기준으로 삼은 version
     * @return 발급된 serverVersion
     * @throws CollaborationException 현재 version과 일치하지 않는 경우
     */
    @Transactional
    public Long issueNextVersionForFullReplace(Long projectId, Long baseVersion) {
        return issueNextVersion(projectId, baseVersion, null, true).serverVersion();
    }

    /**
     * operationId를 state lock 안에서 재확인한 뒤 serverVersion을 발급한다.
     *
     * @param projectId version을 발급할 project 식별자
     * @param baseVersion client가 알고 있는 project version
     * @param operationId 중복 여부를 확인할 operation 식별자
     * @return 신규 version 또는 기존 operation 정보
     * @throws ProjectException project가 존재하지 않는 경우
     * @throws CollaborationException client version이 현재 serverVersion보다 앞서는 경우
     */
    @Transactional
    public VersionIssuance issueNextVersion(
            Long projectId,
            Long baseVersion,
            String operationId
    ) {
        return issueNextVersion(projectId, baseVersion, operationId, false);
    }

    private VersionIssuance issueNextVersion(
            Long projectId,
            Long baseVersion,
            String operationId,
            boolean requireExactBaseVersion
    ) {
        Optional<ProjectCollaborationState> state = stateRepository.findByProjectId(projectId);

        if (state.isPresent()) {
            state = stateRepository.findByProjectIdForUpdate(projectId);
        } else {
            Project project = projectRepository.findByIdForUpdate(projectId)
                    .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
            state = stateRepository.findByProjectIdForUpdate(projectId)
                    .or(() -> Optional.of(createInitialState(project)));
        }

        ProjectCollaborationState lockedState = state
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (operationId != null) {
            ProjectCollaborationOperation existing = operationRepository
                    .findByProjectIdAndOperationId(projectId, operationId)
                    .orElse(null);
            if (existing != null) {
                return new VersionIssuance(null, existing);
            }
        }

        boolean versionConflict = requireExactBaseVersion
                ? baseVersion == null || !baseVersion.equals(lockedState.getServerVersion())
                : baseVersion == null || baseVersion > lockedState.getServerVersion();
        if (versionConflict) {
            throw new CollaborationException(CollaborationErrorCode.VERSION_CONFLICT);
        }

        lockedState.advanceServerVersion();
        return new VersionIssuance(lockedState.getServerVersion(), null);
    }

    private ProjectCollaborationState createInitialState(Project project) {
        return stateRepository.save(new ProjectCollaborationState(project));
    }

    /**
     * version 발급 결과 또는 lock 안에서 재확인된 기존 operation을 표현한다.
     *
     * @param serverVersion 신규 operation에 발급된 version
     * @param existingOperation 이미 저장된 operation
     */
    public record VersionIssuance(
            Long serverVersion,
            ProjectCollaborationOperation existingOperation
    ) {
    }
}
