package com.infragen.infragen.domain.parsing.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.exception.code.success.GenerationSuccessCode;
import com.infragen.infragen.domain.generation.service.command.GenerationCommandService;
import com.infragen.infragen.domain.parsing.controller.docs.ParsingControllerDocs;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class ParsingController implements ParsingControllerDocs {
    private final GenerationCommandService generationCommandService;

    @Override
    @PostMapping("/generate")
    public ApiResponse<GenerateResDTO.GenerateResultResDTO> generateInfrastructure(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId,
        @RequestBody ParsingReqDTO requestDTO
    ) {
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            requestDTO,
            userDetails.getMemberId()
        );

        return ApiResponse.onSuccess(GenerationSuccessCode.GENERATE_SUCCESS, result);
    }
}
