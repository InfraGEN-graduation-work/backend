package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationOperationCompactionService {
    private final ProjectCollaborationOperationRepository operationRepository;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;

    /**
     * 최신 snapshot으로 복원할 수 있는 operation log를 삭제한다.
     *
     * @param projectId compact 대상 project 식별자
     * @return 삭제된 operation 수
     */
    @Transactional
    public int compact(Long projectId) {
        return compact(projectId, 0L);
    }

    /**
     * 최신 snapshot에서 retention version만큼 operation log를 남기고 오래된 log를 삭제한다.
     */
    @Transactional
    public int compact(Long projectId, long retentionVersions) {
        return snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId)
                .map(snapshot -> operationRepository
                        .deleteAllByProjectIdAndServerVersionLessThanEqual(
                                projectId,
                                Math.max(0L, snapshot.getServerVersion() - Math.max(0L, retentionVersions))
                        ))
                .orElse(0);
    }
}
