package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorQueryService;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.CustomUserDetails;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectCollaboratorController.class)
@ContextConfiguration(classes = {
        ProjectCollaboratorControllerWebTest.ControllerWebTestApplication.class,
        ProjectCollaboratorController.class,
        GeneralExceptionAdvice.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProjectCollaboratorController Web 테스트")
class ProjectCollaboratorControllerWebTest {
    private static final String BASE_URL = "/api/v1/projects/{projectId}/collaborators";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCollaboratorQueryService collaboratorQueryService;

    @MockitoBean
    private ProjectCollaboratorCommandService collaboratorCommandService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProjectCollaboratorControllerWebTest.WebMvcTestConfig.class)
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
    @DisplayName("collaborator 목록 조회 요청은 목록과 성공 코드를 반환한다")
    void getCollaborators_ReturnsList() throws Exception {
        // given
        when(collaboratorQueryService.getAll(1L, 7L)).thenReturn(
                ProjectCollaboratorResDTO.ListResult.builder()
                        .collaborators(List.of(ProjectCollaboratorResDTO.Detail.builder()
                                .memberId(8L)
                                .nickname("editor")
                                .role(ProjectCollaboratorRole.EDITOR)
                                .build()))
                        .build()
        );

        // when & then
        mockMvc.perform(get(BASE_URL, 1L).with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_5"))
                .andExpect(jsonPath("$.result.collaborators[0].memberId").value(8))
                .andExpect(jsonPath("$.result.collaborators[0].role").value("EDITOR"));
    }

    @Test
    @DisplayName("collaborator 등록 요청은 201과 등록 결과를 반환한다")
    void addCollaborator_ReturnsCreated() throws Exception {
        // given
        when(collaboratorCommandService.add(eq(1L), eq(7L), any(ProjectCollaboratorReqDTO.Add.class)))
                .thenReturn(ProjectCollaboratorResDTO.Detail.builder()
                        .memberId(8L)
                        .nickname("editor")
                        .role(ProjectCollaboratorRole.EDITOR)
                        .build());

        // when & then
        mockMvc.perform(post(BASE_URL, 1L)
                        .with(authenticatedAs(7L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"memberId\":8,\"role\":\"EDITOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT201_2"))
                .andExpect(jsonPath("$.result.memberId").value(8));
    }

    @Test
    @DisplayName("collaborator 역할 변경과 삭제 요청은 각각 성공 응답을 반환한다")
    void changeRoleAndDelete_ReturnSuccess() throws Exception {
        // when & then
        mockMvc.perform(patch(BASE_URL + "/{memberId}", 1L, 8L)
                        .with(authenticatedAs(7L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_6"));

        mockMvc.perform(delete(BASE_URL + "/{memberId}", 1L, 8L)
                        .with(authenticatedAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT200_7"));

        verify(collaboratorCommandService).changeRole(
                eq(1L),
                eq(7L),
                eq(8L),
                any(ProjectCollaboratorReqDTO.ChangeRole.class)
        );
        verify(collaboratorCommandService).delete(1L, 7L, 8L);
    }

    @Test
    @DisplayName("collaborator 등록 요청에 memberId가 없으면 validation 오류를 반환한다")
    void addCollaborator_WithoutMemberId_ReturnsBadRequest() throws Exception {
        // when & then
        mockMvc.perform(post(BASE_URL, 1L)
                        .with(authenticatedAs(7L))
                        .contentType(APPLICATION_JSON)
                        .content("{\"role\":\"EDITOR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));

        verifyNoInteractions(collaboratorCommandService);
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
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            request.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    context
            );
            return request;
        };
    }
}
