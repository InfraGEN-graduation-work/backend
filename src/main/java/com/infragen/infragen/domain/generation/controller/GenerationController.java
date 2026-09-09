package com.infragen.infragen.domain.generation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.exception.code.success.GenerationSuccessCode;
import com.infragen.infragen.domain.generation.service.command.GenerationCommandService;
import com.infragen.infragen.domain.generation.controller.docs.GenerationControllerDocs;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class GenerationController implements GenerationControllerDocs {
    private final GenerationCommandService generationCommandService;

    /**
     * 캔버스 그래프와 배포 옵션으로 IaC 파일을 생성한다.
     */
    @Override
    @PostMapping("/generate")
    public ApiResponse<GenerateResDTO.GenerateResultResDTO> generateInfrastructure(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId,
        @Valid @RequestBody GenerateReqDTO.Request requestDTO
    ) {
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            requestDTO,
            userDetails.getMemberId()
        );

        return ApiResponse.onSuccess(GenerationSuccessCode.GENERATE_SUCCESS, result);
    }
}
