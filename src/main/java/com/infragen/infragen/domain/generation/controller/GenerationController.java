package com.infragen.infragen.domain.generation.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.enums.OutputFormat;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.exception.code.success.GenerationSuccessCode;
import com.infragen.infragen.domain.generation.service.command.GenerationCommandService;
import com.infragen.infragen.domain.generation.controller.docs.GenerationControllerDocs;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class GenerationController implements GenerationControllerDocs {
    private final GenerationCommandService generationCommandService;

    /**
     * 캔버스 그래프를 파싱한 뒤 선택한 출력 형식의 IaC 파일을 생성한다.
     * 출력 형식을 생략하면 LOCAL_DEV용 Docker Compose를 생성한다.
     */
    @Override
    @PostMapping("/generate")
    public ApiResponse<GenerateResDTO.GenerateResultResDTO> generateInfrastructure(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @PathVariable Long projectId,
        @RequestParam(required = false) String outputFormat,
        @RequestBody ParsingReqDTO requestDTO
    ) {
        OutputFormat resolvedOutputFormat = resolveOutputFormat(outputFormat);
        GenerateResDTO.GenerateResultResDTO result = generationCommandService.generate(
            projectId,
            requestDTO,
            userDetails.getMemberId(),
            resolvedOutputFormat
        );

        return ApiResponse.onSuccess(GenerationSuccessCode.GENERATE_SUCCESS, result);
    }

    private OutputFormat resolveOutputFormat(String outputFormat) {
        if (outputFormat == null || outputFormat.isBlank()) {
            return OutputFormat.DOCKER_COMPOSE;
        }

        try {
            return OutputFormat.valueOf(outputFormat.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IaCGenerationException(IaCGenerationErrorCode.UNSUPPORTED_OUTPUT_FORMAT);
        }
    }
}
