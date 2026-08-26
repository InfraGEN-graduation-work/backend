package com.infragen.infragen.domain.generation.controller.docs;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.parsing.dto.request.ParsingReqDTO;
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
            outputFormat을 생략하면 LOCAL_DEV(DOCKER_COMPOSE)를 생성하며,
            TERRAFORM을 명시하면 CLOUD_DEPLOY plan-only scaffold를 생성합니다.
            출력 형식 선택은 generation 유스케이스의 입력이며, 그래프 parsing 입력과 분리됩니다.
            """
    )
    ApiResponse<GenerateResDTO.GenerateResultResDTO> generateInfrastructure(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Parameter(description = "프로젝트 고유 식별자", required = true, example = "1")
        @PathVariable Long projectId,
        @Parameter(
            description = "생성할 IaC 출력 형식. 생략하면 DOCKER_COMPOSE입니다.",
            required = false,
            example = "DOCKER_COMPOSE"
        )
        @RequestParam(required = false) String outputFormat,
        @RequestBody(
            description = "캔버스 노드·엣지 그래프 (nested properties 형식)",
            required = true,
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ParsingReqDTO.class),
                examples = @ExampleObject(
                    name = "MySQL + Spring Boot (LOCAL_DEV)",
                    value = """
                        {
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
        ParsingReqDTO requestDTO
    );
}
