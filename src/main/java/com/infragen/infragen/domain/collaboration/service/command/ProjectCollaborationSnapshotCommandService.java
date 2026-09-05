package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectCollaborationSnapshotCommandService {
    private final ProjectAccessService projectAccessService;
    private final ProjectRepository projectRepository;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaborationSnapshotRepository snapshotRepository;

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

        snapshotRepository.save(ProjectCollaborationSnapshot.builder()
                .project(project)
                .updatedBy(member)
                .serverVersion(serverVersion)
                .graphPayload(graphPayload)
                .build());
    }
}
