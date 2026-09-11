package com.infragen.infragen.domain.collaboration.service.command;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.infragen.infragen.domain.collaboration.converter.ProjectCollaborationSnapshotConverter;
import com.infragen.infragen.domain.collaboration.event.ProjectCollaborationCheckpointEvent;
import com.infragen.infragen.domain.collaboration.event.ProjectRoomResyncEvent;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationSnapshotCommandService {
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;
    private final ProjectCollaborationSnapshotWriter snapshotWriter;
    private final ProjectCollaborationCheckpointFailureService failureService;
    private final ObjectMapper objectMapper;

    /**
     * materialized graph를 지정한 serverVersion의 snapshot으로 저장한다.
     *
     * @param projectId snapshot 대상 project 식별자
     * @param memberId snapshot을 생성한 member 식별자
     * @param serverVersion graph의 기준 serverVersion
     * @param graphPayload 저장할 materialized graph payload
     * @throws CollaborationException version 또는 graph payload가 올바르지 않은 경우
     * @throws ProjectException project가 존재하지 않는 경우
     */
    @Transactional
    public void saveSnapshot(
            Long projectId,
            Long memberId,
            Long serverVersion,
            Map<String, Object> graphPayload
    ) {
        projectAccessService.requireWriteAccess(projectId, memberId);
        if (serverVersion == null || serverVersion < 0 || graphPayload == null) {
            throw new CollaborationException(CollaborationErrorCode.INVALID_OPERATION);
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        Member member = memberQueryService.findById(memberId);

        persistSnapshot(project, member, serverVersion, graphPayload);
    }

    /**
     * owner-only PUT의 graph와 version을 원래 transaction의 snapshot으로 저장한다.
     * 저장 실패는 PUT을 롤백시키며 commit 이후 room resync도 발행되지 않는다.
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProjectReplacing(ProjectRoomResyncEvent event) {
        Project project = projectRepository.findById(event.projectId())
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        persistSnapshot(
                project,
                project.getMember(),
                event.snapshot().graphVersion(),
                ProjectCollaborationSnapshotConverter.toGraphPayload(event.snapshot().project(), objectMapper)
        );
    }

    /**
     * commit된 operation의 checkpoint event를 별도 transaction에서 snapshot으로 저장한다.
     *
     * @param event commit된 operation의 version과 materialized graph payload
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOperationCommitted(ProjectCollaborationCheckpointEvent event) {
        try {
            snapshotWriter.write(event);
        } catch (RuntimeException exception) {
            failureService.record(event, exception);
        }
    }

    private void persistSnapshot(
            Project project,
            Member member,
            Long serverVersion,
            Map<String, Object> graphPayload
    ) {
        snapshotRepository.save(ProjectCollaborationSnapshotConverter.toEntity(
                project,
                member,
                serverVersion,
                graphPayload
        ));
    }

}
