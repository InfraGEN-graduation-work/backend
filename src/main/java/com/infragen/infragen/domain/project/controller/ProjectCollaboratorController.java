package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.project.controller.docs.ProjectCollaboratorControllerDocs;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorInvitationReqDTO;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.exception.code.success.ProjectSuccessCode;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorInvitationQueryService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/collaborators")
public class ProjectCollaboratorController implements ProjectCollaboratorControllerDocs {
    private final ProjectCollaboratorQueryService collaboratorQueryService;
    private final ProjectCollaboratorCommandService collaboratorCommandService;
    private final ProjectCollaboratorInvitationCommandService invitationCommandService;
    private final ProjectCollaboratorInvitationQueryService invitationQueryService;

    @Override
    @GetMapping
    public ApiResponse<ProjectCollaboratorResDTO.ListResult> getCollaborators(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    ) {
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_GET_SUCCESS,
                collaboratorQueryService.getAll(projectId, userDetails.getMemberId())
        );
    }

    /** owner만 프로젝트에서 발신한 초대 목록을 확인한다. */
    @Override
    @GetMapping("/invitations")
    public ApiResponse<ProjectCollaboratorInvitationResDTO.SentList> getSentInvitations(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    ) {
        return ApiResponse.onSuccess(
                ProjectSuccessCode.PROJECT_COLLABORATOR_INVITATION_SENT_LIST_GET_SUCCESS,
                invitationQueryService.getSentInvitations(projectId, userDetails.getMemberId())
        );
    }

    /** 인증된 owner가 초대 대상을 지정하고 PENDING 초대를 생성한다. */
    @Override
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> inviteCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectCollaboratorInvitationReqDTO.Create request
    ) {
        invitationCommandService.invite(
                projectId,
                userDetails.getMemberId(),
                request.inviteeCode(),
                request.role()
        );
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_INVITATION_SEND_SUCCESS, null);
    }

    /** 숫자 memberId 직접 등록 요청을 거부하고 초대코드 발신 경로를 사용하게 한다. */
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void rejectMemberIdAddition(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    ) {
        collaboratorCommandService.rejectMemberIdAddition(projectId, userDetails.getMemberId());
    }

    @Override
    @PatchMapping("/{memberId}")
    public ApiResponse<Void> changeCollaboratorRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @Valid @RequestBody ProjectCollaboratorReqDTO.ChangeRole request
    ) {
        collaboratorCommandService.changeRole(
                projectId,
                userDetails.getMemberId(),
                memberId,
                request
        );
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_ROLE_UPDATE_SUCCESS, null);
    }

    @Override
    @DeleteMapping("/{memberId}")
    public ApiResponse<Void> deleteCollaborator(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @PathVariable Long memberId
    ) {
        collaboratorCommandService.delete(projectId, userDetails.getMemberId(), memberId);
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_DELETE_SUCCESS, null);
    }

    /** 인증된 collaborator가 자신의 membership만 삭제하고 프로젝트에서 나간다. */
    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> leaveProject(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId
    ) {
        collaboratorCommandService.leave(projectId, userDetails.getMemberId());
        return ApiResponse.onSuccess(ProjectSuccessCode.PROJECT_COLLABORATOR_DELETE_SUCCESS, null);
    }
}
