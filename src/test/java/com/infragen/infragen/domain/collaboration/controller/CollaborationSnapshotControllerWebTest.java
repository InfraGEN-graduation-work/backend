package com.infragen.infragen.domain.collaboration.controller;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationSnapshotResDTO;
import com.infragen.infragen.domain.collaboration.service.query.CollaborationSnapshotQueryService;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.CustomUserDetails;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import org.junit.jupiter.api.AfterEach;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CollaborationSnapshotController.class)
@ContextConfiguration(classes = {
        CollaborationSnapshotControllerWebTest.ControllerWebTestApplication.class,
        CollaborationSnapshotController.class,
        GeneralExceptionAdvice.class
})
@AutoConfigureMockMvc(addFilters = false)
class CollaborationSnapshotControllerWebTest {
    private static final String SNAPSHOT_URL = "/api/v1/projects/{projectId}/collaboration";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CollaborationSnapshotQueryService snapshotQueryService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WebMvcTestConfig.class)
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
    void getSnapshot_withAfterVersion_returnsSnapshotResponse() throws Exception {
        // given
        CollaborationSnapshotResDTO.SnapshotResDTO snapshot =
                CollaborationSnapshotResDTO.SnapshotResDTO.builder()
                        .project(ProjectResDTO.ProjectDetailResDTO.builder()
                                .projectId(1L)
                                .title("project")
                                .status("DRAFT")
                                .nodes(List.of())
                                .edges(List.of())
                                .build())
                        .serverVersion(3L)
                        .operations(List.of())
                        .build();
        when(snapshotQueryService.getSnapshot(1L, 7L, 2L)).thenReturn(snapshot);

        // when
        mockMvc.perform(get(SNAPSHOT_URL, 1L)
                        .param("afterVersion", "2")
                        .with(authenticatedAs(7L)))
                // then
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COLLAB200_1"))
                .andExpect(jsonPath("$.result.project.projectId").value(1))
                .andExpect(jsonPath("$.result.serverVersion").value(3));

        verify(snapshotQueryService).getSnapshot(eq(1L), eq(7L), eq(2L));
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
            request.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
            return request;
        };
    }
}
