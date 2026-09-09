package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationCheckpointFailureConverter;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationCheckpointFailureService {
    private final ProjectCollaborationCheckpointFailureRepository failureRepository;
    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;

    /**
     * checkpoint 실패 payload를 독립 transaction으로 저장해 서버 재시작 뒤에도 재시도할 수 있게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(ProjectCollaborationCheckpointEvent event, Throwable exception) {
        ProjectCollaborationCheckpointFailure failure = failureRepository
                .findByProjectIdAndServerVersion(event.projectId(), event.serverVersion())
                .orElseGet(() -> failureRepository.save(
                        ProjectCollaborationCheckpointFailureConverter.toEntity(
                                event,
                                projectRepository.findById(event.projectId())
                                        .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND)),
                                memberQueryService.findById(event.memberId())
                        )
                ));
        failure.recordFailure(errorMessage(exception));
    }

    /**
     * retry 실패 횟수와 마지막 원인을 갱신한다.
     */
    @Transactional
    public void recordRetryFailure(ProjectCollaborationCheckpointFailure failure, Throwable exception) {
        failure.recordFailure(errorMessage(exception));
    }

    private String errorMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }
}
