package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.service.query.MemberQueryService;
import com.infragen.infragen.domain.project.converter.ProjectCollaboratorConverter;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectCollaboratorCommandService {
    private final ProjectQueryService projectQueryService;
    private final MemberQueryService memberQueryService;
    private final ProjectCollaboratorRepository collaboratorRepository;

    /**
     * owner가 지정한 active member를 project collaborator로 등록한다.
     */
    @Transactional
    public ProjectCollaboratorResDTO.Detail add(
            Long projectId,
            Long ownerId,
            ProjectCollaboratorReqDTO.Add request
    ) {
        Project project = projectQueryService.getOwnedProject(projectId, ownerId);
        if (project.getMember().getId().equals(request.memberId())) {
            throw new ProjectException(ProjectErrorCode.OWNER_CANNOT_BE_COLLABORATOR);
        }
        if (collaboratorRepository.findByProjectIdAndMemberId(projectId, request.memberId()).isPresent()) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_ALREADY_EXISTS);
        }

        Member member = memberQueryService.findById(request.memberId());
        ensureGuestOwnerOnlyManagesGuests(project, member);
        return ProjectCollaboratorConverter.toDetail(collaboratorRepository.save(
                ProjectCollaborator.builder()
                        .project(project)
                        .member(member)
                        .role(request.role())
                        .build()
        ));
    }

    /**
     * owner가 collaborator의 역할을 EDITOR 또는 VIEWER로 변경한다.
     */
    @Transactional
    public void changeRole(
            Long projectId,
            Long ownerId,
            Long memberId,
            ProjectCollaboratorReqDTO.ChangeRole request
    ) {
        Project project = projectQueryService.getOwnedProject(projectId, ownerId);
        ProjectCollaborator collaborator = findCollaborator(projectId, memberId);
        ensureGuestOwnerOnlyManagesGuests(project, collaborator.getMember());
        collaborator.changeRole(request.role());
    }

    /**
     * owner가 project에서 collaborator membership을 삭제한다.
     */
    @Transactional
    public void delete(Long projectId, Long ownerId, Long memberId) {
        Project project = projectQueryService.getOwnedProject(projectId, ownerId);
        if (project.getMember().getRole() == Role.ROLE_GUEST) {
            ProjectCollaborator collaborator = findCollaborator(projectId, memberId);
            ensureGuestOwnerOnlyManagesGuests(project, collaborator.getMember());
        }
        if (collaboratorRepository.deleteByProjectIdAndMemberId(projectId, memberId) == 0) {
            throw new ProjectException(ProjectErrorCode.COLLABORATOR_NOT_FOUND);
        }
    }

    private ProjectCollaborator findCollaborator(Long projectId, Long memberId) {
        return collaboratorRepository.findByProjectIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.COLLABORATOR_NOT_FOUND));
    }

    private void ensureGuestOwnerOnlyManagesGuests(Project project, Member collaboratorMember) {
        if (project.getMember().getRole() == Role.ROLE_GUEST
                && collaboratorMember.getRole() != Role.ROLE_GUEST) {
            throw new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED);
        }
    }
}
