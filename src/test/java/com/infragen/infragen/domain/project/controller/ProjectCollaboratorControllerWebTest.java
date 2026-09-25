package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.request.ProjectCollaboratorReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorCommandService;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorInvitationQueryService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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

    @MockitoBean
    private ProjectCollaboratorInvitationQueryService invitationQueryService;

    @MockitoBean
    private ProjectCollaboratorInvitationCommandService invitationCommandService;

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
        verify(collaboratorQueryService).getAll(1L, 7L);
    }

    @Test
    @DisplayName("프로젝트 비참여자의 collaborator 목록 요청은 403으로 거부한다")
    void getCollaborators_NonCollaborator_ReturnsForbidden() throws Exception {
        // given
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(collaboratorQueryService).getAll(1L, 9L);

        // when
        var response = mockMvc.perform(get(BASE_URL, 1L).with(authenticatedAs(9L)));

        // then
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        verify(collaboratorQueryService).getAll(1L, 9L);
    }

    @Test
    @DisplayName("owner의 발신 초대 목록 조회는 항목과 성공 코드를 반환한다")
    void getSentInvitations_Owner_ReturnsList() throws Exception {
        // given
        when(invitationQueryService.getSentInvitations(1L, 7L)).thenReturn(
                ProjectCollaboratorInvitationResDTO.SentList.builder()
                        .invitations(List.of(ProjectCollaboratorInvitationResDTO.SentItem.builder()
                                .invitationId(21L)
                                .inviteeNickname("guest-editor")
                                .role(ProjectCollaboratorRole.EDITOR)
                                .status(ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING)
                                .build()))
                        .build()
        );

        // when
        var response = mockMvc.perform(get(BASE_URL + "/invitations", 1L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_9"))
                .andExpect(jsonPath("$.result.invitations[0].invitationId").value(21))
                .andExpect(jsonPath("$.result.invitations[0].inviteeNickname").value("guest-editor"))
                .andExpect(jsonPath("$.result.invitations[0].status").value("PENDING"));
        verify(invitationQueryService).getSentInvitations(1L, 7L);
    }

    @Test
    @DisplayName("소유하지 않은 프로젝트의 발신 초대 목록은 조회할 수 없다")
    void getSentInvitations_NotOwned_ReturnsNotFound() throws Exception {
        // given
        when(invitationQueryService.getSentInvitations(1L, 9L))
                .thenThrow(new ProjectException(ProjectErrorCode.PROJECT_NOT_FOUND));

        // when
        var response = mockMvc.perform(get(BASE_URL + "/invitations", 1L)
                .with(authenticatedAs(9L)));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT404_1"));
        verify(invitationQueryService).getSentInvitations(1L, 9L);
    }

    @Test
    @DisplayName("초대 발신 요청은 초대를 만들고 201 성공 응답을 반환한다")
    void inviteCollaborator_ValidRequest_ReturnsCreated() throws Exception {
        // given
        String requestBody = "{\"inviteeCode\":\"A1B2C3D4\",\"role\":\"EDITOR\"}";

        // when
        var response = mockMvc.perform(post(BASE_URL + "/invitations", 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content(requestBody));

        // then
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT201_3"));
        verify(invitationCommandService).invite(
                1L,
                7L,
                "A1B2C3D4",
                ProjectCollaboratorRole.EDITOR
        );
    }

    @Test
    @DisplayName("형식이 잘못된 초대코드는 초대 발신에 사용할 수 없다")
    void inviteCollaborator_InvalidInviteeCode_ReturnsBadRequest() throws Exception {
        // given
        String requestBody = "{\"inviteeCode\":\"bad\",\"role\":\"EDITOR\"}";

        // when
        var response = mockMvc.perform(post(BASE_URL + "/invitations", 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content(requestBody));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(invitationCommandService);
    }

    @Test
    @DisplayName("초대 역할이 없으면 초대 발신 요청을 거부한다")
    void inviteCollaborator_WithoutRole_ReturnsBadRequest() throws Exception {
        // given
        String requestBody = "{\"inviteeCode\":\"A1B2C3D4\"}";

        // when
        var response = mockMvc.perform(post(BASE_URL + "/invitations", 1L)
                .with(authenticatedAs(7L))
                .contentType(APPLICATION_JSON)
                .content(requestBody));

        // then
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
        verifyNoInteractions(invitationCommandService);
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
    @DisplayName("본인 탈퇴 요청은 인증된 회원의 ID만 사용하고 성공 응답을 반환한다")
    void leaveProject_AuthenticatedCollaborator_ReturnsSuccess() throws Exception {
        // when
        var response = mockMvc.perform(delete(BASE_URL + "/me", 1L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_7"));
        verify(collaboratorCommandService).leave(1L, 7L);
        verify(collaboratorCommandService, never()).delete(1L, 7L, 7L);
    }

    @Test
    @DisplayName("owner의 본인 탈퇴는 접근 거부 응답을 반환한다")
    void leaveProject_Owner_ReturnsForbidden() throws Exception {
        // given
        doThrow(new ProjectException(ProjectErrorCode.OWNER_CANNOT_LEAVE_PROJECT))
                .when(collaboratorCommandService).leave(1L, 7L);

        // when
        var response = mockMvc.perform(delete(BASE_URL + "/me", 1L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT403_2"))
                .andExpect(jsonPath("$.message").value("프로젝트 소유자는 협업자 나가기 기능을 사용할 수 없습니다."));
    }

    @Test
    @DisplayName("참여하지 않거나 이미 나간 회원은 collaborator 조회 오류를 받는다")
    void leaveProject_NonCollaborator_ReturnsNotFound() throws Exception {
        // given
        doThrow(new ProjectException(ProjectErrorCode.COLLABORATOR_NOT_FOUND))
                .when(collaboratorCommandService).leave(1L, 7L);

        // when
        var response = mockMvc.perform(delete(BASE_URL + "/me", 1L)
                .with(authenticatedAs(7L)));

        // then
        response.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT404_2"));
    }

    @Test
    @DisplayName("memberId 직접 등록 요청은 초대코드 사용 오류를 반환한다")
    void postMemberIdAdd_Disabled_ReturnsForbidden() throws Exception {
        // given
        String requestBody = "{\"memberId\":8,\"role\":\"EDITOR\"}";
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(collaboratorCommandService)
                .rejectMemberIdAddition(1L, 7L);

        // when
        var response = mockMvc.perform(post(BASE_URL, 1L)
                        .with(authenticatedAs(7L))
                        .contentType(APPLICATION_JSON)
                        .content(requestBody));

        // then
        response.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PROJECT403_1"));
        verify(collaboratorCommandService).rejectMemberIdAddition(1L, 7L);
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
