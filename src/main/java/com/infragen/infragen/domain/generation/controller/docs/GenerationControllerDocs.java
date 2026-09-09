package com.infragen.infragen.domain.generation.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import com.infragen.infragen.global.auth.CustomUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Generate API", description = "캔버스 그래프 검증·파싱 후 IaC 파일 생성 API")
public interface GenerationControllerDocs {

    @Operation(
        summary = "인프라 코드 생성 API",
        description = """
            프로젝트 캔버스(노드·엣지)를 검증·파싱한 뒤 IaC 파일을 생성합니다.
            LOCAL_DEV 기준 산출물은 의존 인프라용 docker-compose.yml과 호스트 실행용 .env입니다.
            생성 이력은 project_history·generated_file에 저장되며, 응답 historyId로 조회할 수 있습니다.
            엣지 방향: sourceNodeId(먼저 기동) → targetNodeId(나중 기동). 예) MySQL → Spring Boot.
            프로젝트 식별자는 path의 projectId만 사용합니다.
            deploymentOption이 LOCAL이면 LOCAL_DEV를 생성합니다.
            AWS 또는 OCI이면 선택한 provider의 CLOUD_DEPLOY plan-only scaffold를 생성합니다.
            includeLocalSpec=true이면 Cloud 산출물과 Local 산출물을 함께 생성합니다.
            deploymentTarget은 deploymentOption이 AWS 또는 OCI일 때 선택 provider의 필드를 직접 포함합니다.
            Local 요청은 deploymentTarget을 null로 명시해야 합니다.
            응답 files는 local/·cloud/ scope prefix를 사용하며 모든 결과는 하나의 historyId로 저장됩니다.
            """
    )
    ApiResponse<GenerateResDTO.GenerateResultResDTO> generateInfrastructure(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Parameter(description = "프로젝트 고유 식별자", required = true, example = "1")
        @PathVariable Long projectId,
        @RequestBody(
            description = "캔버스 노드·엣지 그래프 (nested properties 형식)",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GenerateReqDTO.Request.class),
                examples = @ExampleObject(
                    name = "MySQL + Spring Boot (LOCAL_DEV)",
                    value = """
                        {
                          "deploymentOption": "LOCAL",
                          "includeLocalSpec": false,
                          "deploymentTarget": null,
                          "nodes": [
                            {
                              "nodeId": "node-1",
                              "componentType": "MYSQL",
                              "positionX": 100,
                              "positionY": 200,
                              "properties": {
                                "imageVersion": "mysql:8.0",
                                "containerName": "mysql",
                                "volumeName": "mysql_data",
                                "port": 3306,
                                "env": {
                                  "databaseName": "appdb",
                                  "username": "user",
                                "userPassword": "<user-password>",
                                "rootPassword": "<root-password>"
                                }
                              }
                            },
                            {
                              "nodeId": "node-2",
                              "componentType": "SPRING_BOOT",
                              "positionX": 400,
                              "positionY": 200,
                              "properties": {
                                "name": "app",
                                "port": 8080,
                                "javaVersion": "17",
                                "containerName": "spring-app"
                              }
                            }
                          ],
                          "edges": [
                            { "sourceNodeId": "node-1", "targetNodeId": "node-2" }
                          ]
                        }
                        """
                )
            )
        )
        @Valid GenerateReqDTO.Request requestDTO
    );
}
