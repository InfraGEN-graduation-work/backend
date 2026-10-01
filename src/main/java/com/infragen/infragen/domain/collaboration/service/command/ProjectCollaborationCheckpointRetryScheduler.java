package com.infragen.infragen.domain.collaboration.service.command;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationCheckpointFailure;
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
    private final ProjectCollaborationCheckpointFailureService failureService;

    /**
     * 저장에 실패한 checkpoint를 주기적으로 건별 재시도한다. 조회만 맡고 재조회·변경·삭제는 failure service transaction에 위임한다.
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
                failureService.retry(failure.getId());
            } catch (RuntimeException exception) {
                // 한 건의 갱신 실패가 나머지 failure 재시도를 막지 않게 격리한다.
                log.error("협업 snapshot checkpoint retry 처리 실패: failureId={}", failure.getId(), exception);
            }
        }
    }
}
