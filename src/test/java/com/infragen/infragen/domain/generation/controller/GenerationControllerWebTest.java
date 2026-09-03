package com.infragen.infragen.domain.generation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.infragen.infragen.domain.generation.dto.response.GenerateResDTO;
import com.infragen.infragen.domain.generation.dto.request.GenerateReqDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;
import com.infragen.infragen.domain.generation.service.command.GenerationCommandService;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.parsing.exception.ParsingException;
import com.infragen.infragen.domain.parsing.exception.code.error.ParsingErrorCode;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;

@WebMvcTest(GenerationController.class)
@ContextConfiguration(classes = {
    GenerationControllerWebTest.ControllerWebTestApplication.class,
    GenerationController.class,
    GeneralExceptionAdvice.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GenerationController Web 테스트")
class GenerationControllerWebTest {
    private static final String GENERATE_URL = "/api/v1/projects/{projectId}/generate";
    private static final String LOCAL_REQUEST_JSON = """
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
                  "userPassword": "userpass12",
                  "rootPassword": "rootpass12"
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
        """;
    private static final String AWS_REQUEST_JSON = """
        {
          "deploymentOption": "AWS",
          "includeLocalSpec": false,
          "deploymentTarget": {
            "region": "ap-northeast-2",
            "vpcName": "infragen-vpc",
            "subnetName": "infragen-subnet",
            "internetGatewayName": "infragen-igw",
            "routeTableName": "infragen-public-route",
            "securityGroupName": "infragen-sg",
            "instanceName": "infragen-app",
            "vpcCidr": "10.0.0.0/16",
            "subnetCidr": "10.0.1.0/24",
            "amiId": "ami-xxxxxxxx",
            "instanceType": "t3.micro",
            "adminCidr": "203.0.113.10/32",
            "appCidr": "0.0.0.0/0"
          },
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
                  "userPassword": "userpass12",
                  "rootPassword": "rootpass12"
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
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GenerationCommandService generationCommandService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GenerationControllerWebTest.WebMvcTestConfig.class)
    static class ControllerWebTestApplication {
    }

    @TestConfiguration
    static class WebMvcTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /generate — 성공 시 GENERATION200_1·files·historyId 반환")
    void generate_Success() throws Exception {
        // given
        GenerateResDTO.GenerateResultResDTO result = GenerateResDTO.GenerateResultResDTO.builder()
            .files(List.of(
                GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName("local/docker-compose.yml")
                    .content("services:\n  mysql:\n    image: mysql:8.0\n")
                    .build(),
                GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName("local/.env")
                    .content("SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb\n")
                    .build()
            ))
            .historyId(42L)
            .build();

        when(generationCommandService.generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L)))
            .thenReturn(result);

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(LOCAL_REQUEST_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("GENERATION200_1"))
            .andExpect(jsonPath("$.message").value("인프라 코드 생성에 성공했습니다."))
            .andExpect(jsonPath("$.result.historyId").value(42))
            .andExpect(jsonPath("$.result.files.length()").value(2))
            .andExpect(jsonPath("$.result.files[0].fileName").value("local/docker-compose.yml"))
            .andExpect(jsonPath("$.result.files[0].content")
                .value("services:\n  mysql:\n    image: mysql:8.0\n"))
            .andExpect(jsonPath("$.result.files[1].fileName").value("local/.env"))
            .andExpect(jsonPath("$.result.files[1].content")
                .value("SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb\n"));

        // then
        verify(generationCommandService).generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L));
    }

    @Test
    @DisplayName("POST /generate — AWS 배포 옵션으로 Terraform 파일 응답")
    void generate_AwsDeploymentOption_ReturnsTerraformFiles() throws Exception {
        // given
        GenerateResDTO.GenerateResultResDTO result = GenerateResDTO.GenerateResultResDTO.builder()
            .files(List.of(
                GenerateResDTO.GeneratedFileResDTO.builder()
                    .fileName("cloud/Dockerfile")
                    .content("FROM eclipse-temurin:17-jre\n")
                    .build()
            ))
            .historyId(43L)
            .build();

        when(generationCommandService.generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L))).thenReturn(result);

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(AWS_REQUEST_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.result.historyId").value(43))
            .andExpect(jsonPath("$.result.files.length()").value(1))
            .andExpect(jsonPath("$.result.files[0].fileName").value("cloud/Dockerfile"));

        // then
        verify(generationCommandService).generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L));
    }

    @Test
    @DisplayName("POST /generate — AWS target 필수값 누락 시 COMMON400_1·400 반환")
    void generate_AwsTargetMissingRequiredValue_ReturnsBadRequest() throws Exception {
        // given
        String invalidRequest = AWS_REQUEST_JSON.replace(
            "\"vpcName\": \"infragen-vpc\"",
            "\"vpcName\": \"\""
        );

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(invalidRequest))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("POST /generate — 잘못된 deploymentOption 역직렬화 시 COMMON400_1·400 반환")
    void generate_InvalidDeploymentOption_ReturnsBadRequest() throws Exception {
        // given
        String invalidRequest = LOCAL_REQUEST_JSON.replace(
            "\"deploymentOption\": \"LOCAL\"",
            "\"deploymentOption\": \"INVALID\""
        );

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(invalidRequest))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("POST /generate — deploymentTarget 누락 시 COMMON400_1·400 반환")
    void generate_MissingDeploymentTarget_ReturnsBadRequest() throws Exception {
        // given
        String invalidRequest = LOCAL_REQUEST_JSON.replace(
            "  \"deploymentTarget\": null,\n",
            ""
        );

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(invalidRequest))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("POST /generate — 파싱 실패 시 PARSING400_1·400 반환")
    void generate_ParsingFailure_ReturnsBadRequest() throws Exception {
        // given
        when(generationCommandService.generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L)))
            .thenThrow(new ParsingException(ParsingErrorCode.EMPTY_NODES));

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(LOCAL_REQUEST_JSON))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("PARSING400_1"))
            .andExpect(jsonPath("$.message").value("node가 없습니다."));

        verify(generationCommandService).generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L));
    }

    @Test
    @DisplayName("POST /generate — 생성 실패 시 GENERATION400_2·400 반환")
    void generate_GenerationFailure_ReturnsBadRequest() throws Exception {
        // given
        when(generationCommandService.generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L)))
            .thenThrow(new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE));

        // when
        mockMvc.perform(post(GENERATE_URL, 1L)
                .with(authenticatedAs(1L))
                .contentType(APPLICATION_JSON)
                .content(LOCAL_REQUEST_JSON))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("GENERATION400_2"))
            .andExpect(jsonPath("$.message")
                .value("인프라 코드 생성에 필요한 컴포넌트 상태가 올바르지 않습니다."));

        verify(generationCommandService).generate(
            eq(1L), any(GenerateReqDTO.Request.class), eq(1L));
    }

    private static RequestPostProcessor authenticatedAs(Long memberId) {
        return request -> {
            MemberResDTO.MemberResultDTO member = MemberResDTO.MemberResultDTO.builder()
                .id(memberId)
                .email("test@test.com")
                .nickname("tester")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
            CustomUserDetails userDetails = new CustomUserDetails(member);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return request;
        };
    }
}
