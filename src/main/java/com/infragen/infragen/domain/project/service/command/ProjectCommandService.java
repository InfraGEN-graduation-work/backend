package com.infragen.infragen.domain.project.service.command;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.converter.ProjectConverter;
import com.infragen.infragen.domain.project.converter.ProjectNodeConverter;
import com.infragen.infragen.domain.project.converter.ProjectEdgeConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectNode;
import com.infragen.infragen.domain.project.entity.ProjectEdge;
import com.infragen.infragen.domain.project.repository.ProjectRepository;
import com.infragen.infragen.domain.project.repository.ProjectNodeRepository;
import com.infragen.infragen.domain.project.repository.ProjectEdgeRepository;
import com.infragen.infragen.domain.project.repository.ProjectHistoryRepository;
import com.infragen.infragen.domain.project.repository.GeneratedFileRepository;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
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
    private final MemberQueryService memberQueryService;
    private final ProjectQueryService projectQueryService;

    @Transactional
    public ProjectResDTO.CreateProjectResDTO createProject(
        ProjectReqDTO.CreateProjectReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 생성 요청: title={}, memberId={}", request.title(), memberId);

        Member member = memberQueryService.findById(memberId);
        Project savedProject = projectRepository.save(ProjectConverter.toEntity(request, member));
        log.info("프로젝트 생성 성공: id={}", savedProject.getId());

        return ProjectConverter.toCreateProjectResDTO(savedProject);
    }

    @Transactional
    public ProjectResDTO.ProjectDetailResDTO updateProject(
        Long projectId,
        ProjectReqDTO.UpdateProjectReqDTO request,
        Long memberId
    ) {
        log.info("프로젝트 수정 요청: id={}, memberId={}", projectId, memberId);

        Project project = projectQueryService.getOwnedProject(projectId, memberId);

        // 프로젝트 메타정보 수정
        project.updateInfo(request.title(), request.description());

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

        log.info("프로젝트 수정 완료: id={}", projectId);
        return ProjectConverter.toProjectDetailResDTO(project, savedNodes, savedEdges);
    }

    @Transactional
    public void deleteProject(Long projectId, Long memberId) {
        log.info("프로젝트 삭제: id={}, memberId={}", projectId, memberId);

        Project project = projectQueryService.getOwnedProject(projectId, memberId);

        // 외래키 무결성을 위해 자식 데이터 물리 선삭제 (File -> History -> Edge -> Node)
        generatedFileRepository.deleteByProjectId(projectId);
        projectHistoryRepository.deleteByProjectId(projectId);
        projectEdgeRepository.deleteByProjectId(projectId);
        projectNodeRepository.deleteByProjectId(projectId);

        projectRepository.delete(project);

        log.info("프로젝트 삭제 완료: id={}", projectId);
    }
}
