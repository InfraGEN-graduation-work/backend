package com.infragen.infragen.domain.project.service.command;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.entity.Project;
import com.infragen.infragen.domain.project.entity.ProjectCollaborator;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorRepository;
import com.infragen.infragen.domain.project.repository.ProjectRepository;

import lombok.RequiredArgsConstructor;

/** 현재 owner와 대상 참여자를 project 잠금 아래 확인하고 단일 owner 전환을 조정한다. */
@Service
@RequiredArgsConstructor
public class ProjectOwnershipTransferCommandService {
    private final ProjectRepository projectRepository;
    private final ProjectCollaboratorRepository collaboratorRepository;
    private final MemberRepository memberRepository;

    /** project 다음 두 회원을 ID 순으로 잠그고, 같은 계정 유형의 활성 참여자에게 소유권을 즉시 이전한다. */
    @Transactional
    public void transfer(Long projectId, Long ownerId, Long targetMemberId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!project.getMember().getId().equals(ownerId)) {
            throw new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        if (ownerId.equals(targetMemberId)
                || !collaboratorRepository.existsByProjectIdAndMemberId(projectId, targetMemberId)) {
            throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
        }

        Member previousOwner;
        Member newOwner;
        
        // 두 회원을 참조하는 이전 요청끼리 역순으로 잠그지 않는다.
        if (ownerId < targetMemberId) {
            previousOwner = findActiveMemberForUpdate(ownerId, ProjectErrorCode.PROJECT_NOT_FOUND);
            newOwner = findActiveMemberForUpdate(targetMemberId, ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
        } else {
            newOwner = findActiveMemberForUpdate(targetMemberId, ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
            previousOwner = findActiveMemberForUpdate(ownerId, ProjectErrorCode.PROJECT_NOT_FOUND);
        }

        if (previousOwner.getRole() != newOwner.getRole()) {
            throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
        }

        // 대상 참여 기록이 없으면 owner를 바꾸지 않고 이번 이전 전체를 중단한다.
        if (collaboratorRepository.deleteByProjectIdAndMemberId(projectId, targetMemberId) != 1L) {
            throw new ProjectException(ProjectErrorCode.OWNERSHIP_TRANSFER_TARGET_UNAVAILABLE);
        }

        project.transferOwnershipTo(newOwner);

        collaboratorRepository.save(ProjectCollaborator.builder()
                .project(project)
                .member(previousOwner)
                .role(ProjectCollaboratorRole.EDITOR)
                .build());
    }

    private Member findActiveMemberForUpdate(Long memberId, ProjectErrorCode errorCode) {
        return memberRepository.findByIdForUpdate(memberId)
                .filter(member -> Boolean.TRUE.equals(member.getIsActive()))
                .orElseThrow(() -> new ProjectException(errorCode));
    }
}
