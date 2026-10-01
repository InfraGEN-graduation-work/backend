package com.infragen.infragen.domain.collaboration.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationCheckpointFailureConverter;
import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectCollaborationCheckpointFailureService {
    private final ProjectCollaborationCheckpointFailureRepository failureRepository;
    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaborationSnapshotWriter snapshotWriter;

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
     * 저장 실패한 checkpoint 한 건을 독립 transaction 안에서 재조회해 다시 저장한다.
     * 성공하면 failure row를 삭제하고, 실패하면 attemptCount와 lastError를 같은 transaction에서 갱신한다.
     * 이미 삭제된 failure id면 아무것도 하지 않는다.
     *
     * @param failureId 재시도할 checkpoint failure 식별자
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Long failureId) {
        ProjectCollaborationCheckpointFailure failure = failureRepository.findById(failureId).orElse(null);
        if (failure == null) {
            return;
        }

        try {
            snapshotWriter.write(new ProjectCollaborationCheckpointEvent(
                    failure.getProject().getId(),
                    failure.getMember().getId(),
                    failure.getServerVersion(),
                    failure.getGraphPayload()
            ));
            failureRepository.delete(failure);
        } catch (RuntimeException exception) {
            failure.recordFailure(errorMessage(exception));
            log.error(
                    "협업 snapshot checkpoint retry 실패: projectId={}, serverVersion={}, attempts={}",
                    failure.getProject().getId(),
                    failure.getServerVersion(),
                    failure.getAttemptCount(),
                    exception
            );
        }
    }

    private String errorMessage(Throwable exception) {
        String message = exception.getMessage();
        return message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 1000));
    }
}
