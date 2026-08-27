package com.infragen.infragen.domain.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectEdgeResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectNodeResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.CustomUserDetails;

@WebMvcTest(ProjectController.class)
@ContextConfiguration(classes = {
    ProjectControllerWebTest.ControllerWebTestApplication.class,
    ProjectController.class,
    GeneralExceptionAdvice.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProjectController Web 테스트")
class ProjectControllerWebTest {
    private static final String PROJECT_URL = "/api/v1/projects/{projectId}";
    private static final String REQUEST_JSON = """
        {
          "title": "my-infra",
          "description": "개발용 인프라",
          "nodes": [
            {
              "nodeId": "mysql-node-1",
              "nodeName": "mysql",
              "componentType": "MYSQL",
              "positionX": 100,
              "positionY": 200,
              "properties": {}
            },
            {
              "nodeId": "spring-node-1",
              "nodeName": "app",
              "componentType": "SPRING_BOOT",
              "positionX": 400,
              "positionY": 200,
              "properties": {}
            }
          ],
          "edges": [
            {
              "sourceNodeId": "mysql-node-1",
              "targetNodeId": "spring-node-1"
            }
          ]
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCommandService projectCommandService;

    @MockitoBean
    private ProjectQueryService projectQueryService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProjectControllerWebTest.WebMvcTestConfig.class)
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
    @DisplayName("PUT /projects/{projectId} — nodeId 기반 graph 응답")
    void updateProject_NodeIdContract_ReturnsGraphIdentity() throws Exception {
        // given
        ProjectResDTO.ProjectDetailResDTO result = ProjectResDTO.ProjectDetailResDTO.builder()
            .projectId(1L)
            .title("my-infra")
            .description("개발용 인프라")
            .status("DRAFT")
            .nodes(List.of(
                ProjectNodeResDTO.NodeInfoResDTO.builder()
                    .nodeId("mysql-node-1")
                    .nodeName("mysql")
                    .componentType("MYSQL")
                    .positionX(BigDecimal.valueOf(100))
                    .positionY(BigDecimal.valueOf(200))
                    .properties(Map.of())
                    .id(101L)
                    .build(),
                ProjectNodeResDTO.NodeInfoResDTO.builder()
                    .nodeId("spring-node-1")
                    .nodeName("app")
                    .componentType("SPRING_BOOT")
                    .positionX(BigDecimal.valueOf(400))
                    .positionY(BigDecimal.valueOf(200))
                    .properties(Map.of())
                    .id(102L)
                    .build()
            ))
            .edges(List.of(
                ProjectEdgeResDTO.EdgeInfoResDTO.builder()
                    .id(201L)
                    .sourceNodeId("mysql-node-1")
                    .targetNodeId("spring-node-1")
                    .build()
            ))
            .build();

        when(projectCommandService.updateProject(eq(1L), any(), eq(7L))).thenReturn(result);

        // when
        mockMvc.perform(put(PROJECT_URL, 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content(REQUEST_JSON))
            // then
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("PROJECT200_3"))
            .andExpect(jsonPath("$.result.nodes[0].nodeId").value("mysql-node-1"))
            .andExpect(jsonPath("$.result.nodes[0].nodeName").value("mysql"))
            .andExpect(jsonPath("$.result.nodes[0].id").value(101))
            .andExpect(jsonPath("$.result.edges[0].id").value(201))
            .andExpect(jsonPath("$.result.edges[0].sourceNodeId").value("mysql-node-1"))
            .andExpect(jsonPath("$.result.edges[0].targetNodeId").value("spring-node-1"));

        verify(projectCommandService).updateProject(eq(1L), any(), eq(7L));
    }

    @Test
    @DisplayName("PUT /projects/{projectId} — nodeId 누락 시 validation 오류")
    void updateProject_MissingNodeId_ReturnsBadRequest() throws Exception {
        // given
        String requestJson = REQUEST_JSON.replace("\"nodeId\": \"mysql-node-1\",", "");

        // when
        mockMvc.perform(put(PROJECT_URL, 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content(requestJson))
            // then
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false))
            .andExpect(jsonPath("$.code").value("COMMON400_1"));

        verifyNoInteractions(projectCommandService);
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
