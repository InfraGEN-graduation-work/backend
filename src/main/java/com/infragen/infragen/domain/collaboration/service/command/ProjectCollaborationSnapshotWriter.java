package com.infragen.infragen.domain.collaboration.service.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationSnapshotWriter {
    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;

    /**
     * checkpoint version의 graph payload를 독립 transaction으로 저장한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(ProjectCollaborationCheckpointEvent event) {
        // 이미 snapshot이 존재하는 경우 중복 저장을 방지한다.
        if (snapshotRepository.findByProjectIdAndServerVersion(
                event.projectId(),
                event.serverVersion()
        ).isPresent()) {
            return;
        }

        snapshotRepository.save(ProjectCollaborationSnapshot.builder()
                .project(projectRepository.findById(event.projectId())
                        .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND)))
                .updatedBy(memberQueryService.findById(event.memberId()))
                .serverVersion(event.serverVersion())
                .graphPayload(normalizeGraphPayload(event.graphPayload()))
                .build());
    }

    // graph payload에서 null value를 제거하고 LinkedHashMap으로 변환한다.
    private Map<String, Object> normalizeGraphPayload(Map<String, Object> graphPayload) {
        Map<String, Object> normalizedPayload = new LinkedHashMap<>(graphPayload);
        normalizedPayload.values().removeIf(Objects::isNull);
        return normalizedPayload;
    }
}
