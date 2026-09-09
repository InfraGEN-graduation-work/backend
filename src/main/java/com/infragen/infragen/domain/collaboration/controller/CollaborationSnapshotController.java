package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.exception.code.success.CollaborationSuccessCode;
import com.infragen.infragen.domain.collaboration.service.query.CollaborationSnapshotQueryService;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * collaboration client의 초기 graph와 operation replay 조회를 처리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/collaboration")
public class CollaborationSnapshotController {
    private final CollaborationSnapshotQueryService snapshotQueryService;

    /**
     * project snapshot과 지정 version 이후 operation을 반환한다.
     *
     * @param userDetails 인증된 member
     * @param projectId 조회할 project 식별자
     * @param afterVersion replay 기준 version
     * @return graph snapshot과 operation replay 결과
     */
    @GetMapping
    public ApiResponse<CollaborationSnapshotResDTO.SnapshotResDTO> getSnapshot(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") Long afterVersion
    ) {
        CollaborationSnapshotResDTO.SnapshotResDTO result = snapshotQueryService.getSnapshot(
                projectId,
                userDetails.getMemberId(),
                afterVersion
        );
        return ApiResponse.onSuccess(CollaborationSuccessCode.SNAPSHOT_GET_SUCCESS, result);
    }
}
