package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.global.properties.CollaborationCompactionProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationOperationCompactionScheduler {
    private final CollaborationCompactionProperties properties;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;
    private final CollaborationOperationCompactionService compactionService;

    /**
     * snapshot이 존재하는 project의 오래된 operation log를 주기적으로 정리한다.
     */
    @Scheduled(
            fixedDelayString = "${collaboration.compaction.fixed-delay-ms:3600000}",
            initialDelayString = "${collaboration.compaction.initial-delay-ms:60000}"
    )
    public void compactOperationLogs() {
        if (!properties.isEnabled()) {
            return;
        }

        for (Long projectId : snapshotRepository.findDistinctProjectIds()) {
            try {
                int deletedCount = compactionService.compact(
                        projectId,
                        properties.getRetentionVersions()
                );
                if (deletedCount > 0) {
                    log.info("협업 operation log compact 완료: projectId={}, deletedCount={}", projectId, deletedCount);
                }
            } catch (RuntimeException exception) {
                log.error("협업 operation log compact 실패: projectId={}", projectId, exception);
            }
        }
    }
}
