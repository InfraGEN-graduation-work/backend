package com.infragen.infragen.domain.collaboration.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationState;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CollaborationOperationCompactionService {
    private final ProjectCollaborationOperationRepository operationRepository;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;
    private final ProjectCollaborationStateRepository stateRepository;

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
     * 삭제한 상한 version은 state에 함께 기록해, 로그가 지워진 구간을 기준으로 한 재전송을 이후에 거부할 수 있게 한다.
     * 상한 기록은 bulk delete(영속성 컨텍스트를 비움)보다 먼저 해야 같은 transaction에서 반영된다.
     */
    @Transactional
    public int compact(Long projectId, long retentionVersions) {
        return snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId)
                .map(snapshot -> {
                    long compactedVersion = Math.max(
                            0L,
                            snapshot.getServerVersion() - Math.max(0L, retentionVersions)
                    );
                    // operation 발급과 같은 state lock으로 직렬화해 경계 갱신과 중복 확인이 엇갈리지 않게 한다.
                    ProjectCollaborationState state = stateRepository.findByProjectIdForUpdate(projectId)
                            .orElse(null);
                    if (state == null) {
                        return 0;
                    }
                    state.raiseCompactedVersion(compactedVersion);
                    return operationRepository.deleteAllByProjectIdAndServerVersionLessThanEqual(
                            projectId,
                            compactedVersion
                    );
                })
                .orElse(0);
    }
}
