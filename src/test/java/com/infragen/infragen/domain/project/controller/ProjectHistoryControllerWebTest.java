package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.request.ProjectHistoryReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectHistoryResDTO;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.command.ProjectHistoryCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectHistoryQueryService;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectHistoryController.class)
@ContextConfiguration(classes = {
        ProjectHistoryControllerWebTest.Config.class,
        ProjectHistoryController.class,
        GeneralExceptionAdvice.class
})
@AutoConfigureMockMvc(addFilters = false)
class ProjectHistoryControllerWebTest {
    private static final String HISTORIES_URL = "/api/v1/projects/{projectId}/histories";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WebMvcTestConfig.class)
    static class Config {
    }

    @TestConfiguration
    static class WebMvcTestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthenticationPrincipalArgumentResolver());
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectHistoryCommandService commandService;

    @MockitoBean
    private ProjectHistoryQueryService queryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("이력 목록은 인증 회원의 프로젝트 읽기 결과를 반환한다")
    void getHistories_AccessibleMember_ReturnsList() throws Exception {
        // given
        var history = ProjectHistoryResDTO.HistoryPreviewResDTO.builder()
                .historyId(3L).versionName("v1").description("저장")
                .actorMemberId(8L).build();
        when(queryService.getHistories(1L, 7L)).thenReturn(
                ProjectHistoryResDTO.HistoryPreviewListResDTO.builder()
                        .historyList(List.of(history)).build());

        // when
        var response = mockMvc.perform(get(HISTORIES_URL, 1L).with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT_HISTORY200_1"))
                .andExpect(jsonPath("$.result.historyList[0].historyId").value(3))
                .andExpect(jsonPath("$.result.historyList[0].actorMemberId").value(8));
        verify(queryService).getHistories(1L, 7L);
    }

    @Test
    @DisplayName("이력 상세는 인증 회원의 조회 결과를 반환한다")
    void getHistoryDetail_AccessibleMember_ReturnsDetail() throws Exception {
        // given
        when(queryService.getHistoryDetail(1L, 3L, 7L)).thenReturn(
                ProjectHistoryResDTO.HistoryDetailResDTO.builder()
                        .historyId(3L).versionName("v1").generatedFileList(List.of())
                        .actorMemberId(8L).build());

        // when
        var response = mockMvc.perform(get(HISTORIES_URL + "/{historyId}", 1L, 3L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROJECT_HISTORY200_2"))
                .andExpect(jsonPath("$.result.historyId").value(3))
                .andExpect(jsonPath("$.result.actorMemberId").value(8));
        verify(queryService).getHistoryDetail(1L, 3L, 7L);
    }

    @Test
    @DisplayName("기존 이력은 미상 생성자를 null로 반환한다")
    void getHistories_LegacyHistory_ReturnsNullActor() throws Exception {
        // given
        var history = ProjectHistoryResDTO.HistoryPreviewResDTO.builder()
                .historyId(3L).versionName("v1").build();
        when(queryService.getHistories(1L, 7L)).thenReturn(
                ProjectHistoryResDTO.HistoryPreviewListResDTO.builder()
                        .historyList(List.of(history)).build());

        // when
        var response = mockMvc.perform(get(HISTORIES_URL, 1L).with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.historyList[0].actorMemberId").value(nullValue()));
        verify(queryService).getHistories(1L, 7L);
    }

    @Test
    @DisplayName("기존 이력 상세도 미상 생성자를 null로 반환한다")
    void getHistoryDetail_LegacyHistory_ReturnsNullActor() throws Exception {
        // given
        when(queryService.getHistoryDetail(1L, 3L, 7L)).thenReturn(
                ProjectHistoryResDTO.HistoryDetailResDTO.builder()
                        .historyId(3L).versionName("v1").generatedFileList(List.of()).build());

        // when
        var response = mockMvc.perform(get(HISTORIES_URL + "/{historyId}", 1L, 3L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.actorMemberId").value(nullValue()));
        verify(queryService).getHistoryDetail(1L, 3L, 7L);
    }

    @Test
    @DisplayName("프로젝트 비참여자의 이력 조회는 403을 반환한다")
    void getHistories_UnrelatedMember_ReturnsForbidden() throws Exception {
        // given
        when(queryService.getHistories(1L, 7L))
                .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED));

        // when
        var response = mockMvc.perform(get(HISTORIES_URL, 1L).with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        verify(queryService).getHistories(1L, 7L);
    }

    @Test
    @DisplayName("이력 생성은 인증 회원의 설명을 전달하고 201을 반환한다")
    void createHistory_WritableMember_ReturnsCreated() throws Exception {
        // given
        var request = new ProjectHistoryReqDTO.CreateHistoryReqDTO("저장 설명");
        when(commandService.createHistory(1L, request, 7L)).thenReturn(
                ProjectHistoryResDTO.HistoryPreviewResDTO.builder()
                        .historyId(3L).versionName("v1").description("저장 설명")
                        .actorMemberId(7L).build());

        // when
        var response = mockMvc.perform(post(HISTORIES_URL, 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content("{\"description\":\"저장 설명\"}"));

        // then
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("PROJECT_HISTORY201_1"))
                .andExpect(jsonPath("$.result.description").value("저장 설명"))
                .andExpect(jsonPath("$.result.actorMemberId").value(7));
        verify(commandService).createHistory(1L, request, 7L);
    }

    @Test
    @DisplayName("쓰기 권한이 없는 회원의 이력 생성은 403을 반환한다")
    void createHistory_ReadOnlyMember_ReturnsForbidden() throws Exception {
        // given
        var request = new ProjectHistoryReqDTO.CreateHistoryReqDTO("저장 설명");
        when(commandService.createHistory(1L, request, 7L))
                .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED));

        // when
        var response = mockMvc.perform(post(HISTORIES_URL, 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content("{\"description\":\"저장 설명\"}"));

        // then
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        verify(commandService).createHistory(1L, request, 7L);
    }

    private static RequestPostProcessor authenticatedAs(Long memberId) {
        return request -> {
            var member = MemberResDTO.MemberResultDTO.builder()
                    .id(memberId).email("test@example.com").nickname("tester")
                    .role(Role.ROLE_USER).isActive(true).build();
            var userDetails = new CustomUserDetails(member);
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
