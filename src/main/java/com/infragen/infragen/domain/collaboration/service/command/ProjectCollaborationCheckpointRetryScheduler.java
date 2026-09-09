package com.infragen.infragen.domain.collaboration.service.command;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.global.properties.CollaborationCompactionProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCollaborationCheckpointRetryScheduler {
    private final CollaborationCompactionProperties properties;
    private final ProjectCollaborationCheckpointFailureRepository failureRepository;
    private final ProjectCollaborationSnapshotWriter snapshotWriter;
    private final ProjectCollaborationCheckpointFailureService failureService;

    /**
     * 저장에 실패한 checkpoint를 주기적으로 재시도한다.
     */
    @Scheduled(
            fixedDelayString = "${collaboration.compaction.fixed-delay-ms:3600000}",
            initialDelayString = "${collaboration.compaction.initial-delay-ms:60000}"
    )
    public void retryFailedCheckpoints() {
        if (!properties.isEnabled()) {
            return;
        }

        for (ProjectCollaborationCheckpointFailure failure : failureRepository.findTop100ByOrderByCreatedAtAsc()) {
            try {
                snapshotWriter.write(new ProjectCollaborationCheckpointEvent(
                        failure.getProject().getId(),
                        failure.getMember().getId(),
                        failure.getServerVersion(),
                        failure.getGraphPayload()
                ));
                failureRepository.delete(failure);
            } catch (RuntimeException exception) {
                failureService.recordRetryFailure(failure, exception);
                log.error(
                        "협업 snapshot checkpoint retry 실패: projectId={}, serverVersion={}, attempts={}",
                        failure.getProject().getId(),
                        failure.getServerVersion(),
                        failure.getAttemptCount(),
                        exception
                );
            }
        }
    }
}
