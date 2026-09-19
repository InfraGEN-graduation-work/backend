package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.project.controller.docs.ProjectCollaboratorInvitationControllerDocs;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.exception.code.success.ProjectSuccessCode;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorInvitationQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/project-collaborator-invitations")
public class ProjectCollaboratorInvitationController implements ProjectCollaboratorInvitationControllerDocs {
    private final ProjectCollaboratorInvitationQueryService invitationQueryService;
    private final ProjectCollaboratorInvitationCommandService invitationCommandService;

    /** 로그인한 회원 본인에게 도착한 초대를 상태 조건으로 조회한다. */
    @Override
    @GetMapping("/received")
    public ApiResponse<ProjectCollaboratorInvitationResDTO.ReceivedList> getReceivedInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(name = "status", required = false)
            ProjectCollaboratorInvitationResDTO.InvitationStatus statusFilter
    ) {
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_INVITATION_RECEIVED_LIST_GET_SUCCESS,
                invitationQueryService.getReceivedInvitations(userDetails.getMemberId(), statusFilter)
        );
    }

    /** 로그인한 초대 대상이 초대를 수락하고 collaborator membership을 만든다. */
    @Override
    @PostMapping("/{invitationId}/accept")
    public ApiResponse<Void> acceptInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long invitationId
    ) {
        invitationCommandService.accept(invitationId, userDetails.getMemberId());
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_INVITATION_ACCEPT_SUCCESS,
                null
        );
    }

    /** 로그인한 초대 대상이 초대를 거절하고 invitation 상태만 변경한다. */
    @Override
    @PostMapping("/{invitationId}/decline")
    public ApiResponse<Void> declineInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long invitationId
    ) {
        invitationCommandService.decline(invitationId, userDetails.getMemberId());
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_INVITATION_DECLINE_SUCCESS,
                null
        );
    }
}
