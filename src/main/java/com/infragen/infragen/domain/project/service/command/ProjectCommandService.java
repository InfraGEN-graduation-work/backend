package com.infragen.infragen.domain.project.service.command;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.collaboration.event.ProjectRoomResyncEvent;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationCheckpointFailureRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationStateRepository;
import com.infragen.infragen.domain.collaboration.service.command.ProjectCollaborationVersionService;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.converter.ProjectEdgeConverter;
import com.infragen.infragen.domain.project.converter.ProjectNodeConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectCommandService {
    private final ProjectRepository projectRepository;
    private final ProjectNodeRepository projectNodeRepository;
    private final ProjectEdgeRepository projectEdgeRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final GeneratedFileRepository generatedFileRepository;
    private final MemberRepository memberRepository;
    private final ProjectQueryService projectQueryService;
    private final ProjectCollaborationVersionService projectCollaborationVersionService;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectCollaboratorRepository projectCollaboratorRepository;
    private final ProjectCollaboratorInvitationRepository projectCollaboratorInvitationRepository;
    private final ProjectCollaborationStateRepository collaborationStateRepository;
    private final ProjectCollaborationOperationRepository collaborationOperationRepository;
    private final ProjectCollaborationSnapshotRepository collaborationSnapshotRepository;
    private final ProjectCollaborationCheckpointFailureRepository checkpointFailureRepository;

    /** 활성 회원을 잠근 뒤 새 프로젝트를 생성한다. 회원 잠금 이후 기존 프로젝트를 잠그지 않는다. */
    @Transactional
    public ProjectResDTO.CreateProjectResDTO createProject(
        ProjectReqDTO.CreateProjectReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 생성 요청: title={}, memberId={}", request.title(), memberId);

        Member member = memberRepository.findByIdForUpdate(memberId)
                .filter(lockedMember -> Boolean.TRUE.equals(lockedMember.getIsActive()))
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        Project savedProject = projectRepository.save(ProjectConverter.toEntity(request, member));
        
        log.info("프로젝트 생성 성공: id={}", savedProject.getId());

        return ProjectConverter.toCreateProjectResDTO(savedProject);
    }

    /**
     * 프로젝트 graph 전체를 교체한다. OWNER와 EDITOR가 호출할 수 있다.
     * 이름·설명은 OWNER의 요청만 반영하고, EDITOR의 요청은 기존 값을 유지한다.
     */
    @Transactional
    public ProjectResDTO.ProjectDetailResDTO updateProject(
        Long projectId,
        ProjectReqDTO.UpdateProjectReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 수정 요청: id={}, memberId={}", projectId, memberId);

        // version state보다 project를 먼저 잠가 metadata·삭제와 잠금 순서를 맞춘다.
        Project lockedProject = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        Project project = projectQueryService.getWriteableProject(projectId, memberId);

        Long serverVersion = projectCollaborationVersionService.issueNextVersionForFullReplace(
                projectId,
                request.baseVersion()
        );

        // 이름·설명은 OWNER 전용이다(updateMetadata와 동일). EDITOR가 보낸 값은 무시하고 기존 값을 보존한다.
        // 잠근 엔티티로 판정해 소유권 이전과 직렬화한다.
        if (memberId.equals(lockedProject.getMember().getId())) {
            project.updateInfo(request.title(), request.description());
        }

        // 외래키 제약조건 고려하여 기존 자식 데이터 일괄 삭제 (Edge 선삭제 -> Node 후삭제)
        projectEdgeRepository.deleteByProjectId(projectId);
        projectNodeRepository.deleteByProjectId(projectId);

        // 신규 Node 리스트 일괄 생성 및 저장
        List<ProjectNode> newNodes = ProjectNodeConverter.toEntityList(request.nodes(), project);
        List<ProjectNode> savedNodes = projectNodeRepository.saveAll(newNodes);

        // Edge 매핑을 위한 Node ID Map 구성 (중복 키 발생 시 예외 처리)
        Map<String, ProjectNode> nodeMap = savedNodes.stream()
            .collect(Collectors.toMap(
                ProjectNode::getNodeId,
                node -> node,
                (existing, replacement) -> {
                    throw new ProjectException(ProjectErrorCode.DUPLICATE_NODE_ID);
                }
            ));

        // 신규 Edge 리스트 일괄 생성 및 저장
        List<ProjectEdge> newEdges = ProjectEdgeConverter.toEntityList(request.edges(), project, nodeMap);
        List<ProjectEdge> savedEdges = projectEdgeRepository.saveAll(newEdges);

        ProjectResDTO.ProjectDetailResDTO result = ProjectConverter.toProjectDetailResDTO(
                project,
                savedNodes,
                savedEdges
        );
        eventPublisher.publishEvent(new ProjectRoomResyncEvent(projectId, serverVersion, result));

        log.info("프로젝트 수정 완료: id={}, serverVersion={}", projectId, serverVersion);
        return result;
    }

    /**
     * owner가 프로젝트 이름·설명만 변경하고 graph는 보존한다.
     * project 잠금 뒤 현재 owner를 확인하고 version state를 잠근다.
     * version·snapshot을 같은 transaction에 저장하고 commit 후 room을 재동기화한다.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectResDTO.ProjectPreviewResDTO updateMetadata(
        Long projectId,
        ProjectReqDTO.UpdateMetadata request,
        Long memberId
    ) {
        Project project = findOwnedProjectForUpdate(projectId, memberId);

        Long serverVersion = projectCollaborationVersionService.issueNextVersionForFullReplace(
                projectId, request.baseVersion());

        String description = request.description() == null ? project.getDescription() : request.description();

        project.updateInfo(request.title(), description);

        List<ProjectNode> nodes = projectNodeRepository.findAllByProjectId(projectId);
        List<ProjectEdge> edges = projectEdgeRepository.findAllByProjectId(projectId);
        
        eventPublisher.publishEvent(new ProjectRoomResyncEvent(projectId, serverVersion,
                ProjectConverter.toProjectDetailResDTO(project, nodes, edges)));

        return ProjectConverter.toProjectPreviewResDTO(project, "OWNER");
    }

    /**
     * owner의 프로젝트와 모든 종속 데이터를 하나의 transaction으로 삭제한다.
     * project 잠금 뒤 현재 owner를 확인해 소유권 이전과 삭제를 직렬화한다.
     * 자식 데이터를 먼저 정리하며 어느 단계에서든 실패하면 전체 삭제를 롤백한다.
     */
    @Transactional
    public void deleteProject(Long projectId, Long memberId) {
        log.info("프로젝트 삭제: id={}, memberId={}", projectId, memberId);

        Project project = findOwnedProjectForUpdate(projectId, memberId);

        // project를 참조하는 협업 기록을 부모 삭제 전에 정리한다.
        checkpointFailureRepository.deleteByProjectId(projectId);
        collaborationSnapshotRepository.deleteByProjectId(projectId);
        collaborationOperationRepository.deleteByProjectId(projectId);
        collaborationStateRepository.deleteByProjectId(projectId);
        projectCollaboratorInvitationRepository.deleteByProjectId(projectId);
        projectCollaboratorRepository.deleteByProjectId(projectId);

        // generated file은 history를, edge는 node를 참조하므로 자식부터 삭제한다.
        generatedFileRepository.deleteByProjectId(projectId);
        projectHistoryRepository.deleteByProjectId(projectId);
        projectEdgeRepository.deleteByProjectId(projectId);
        projectNodeRepository.deleteByProjectId(projectId);

        projectRepository.delete(project);

        log.info("프로젝트 삭제 완료: id={}", projectId);
    }

    private Project findOwnedProjectForUpdate(Long projectId, Long memberId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));
        if (!memberId.equals(project.getMember().getId())) {
            throw new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }
}
