package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.dto.response.ProjectCollaboratorInvitationResDTO;
import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import com.infragen.infragen.domain.project.service.command.ProjectCollaboratorInvitationCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectCollaboratorInvitationQueryService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectCollaboratorInvitationController.class)
@ContextConfiguration(classes = {
        ProjectCollaboratorInvitationControllerWebTest.ControllerWebTestApplication.class,
        ProjectCollaboratorInvitationController.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProjectCollaboratorInvitationController Web 테스트")
class ProjectCollaboratorInvitationControllerWebTest {
    private static final String BASE_URL = "/api/v1/project-collaborator-invitations";
    private static final String RECEIVED_URL = BASE_URL + "/received";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectCollaboratorInvitationQueryService invitationQueryService;

    @MockitoBean
    private ProjectCollaboratorInvitationCommandService invitationCommandService;

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(ProjectCollaboratorInvitationControllerWebTest.WebMvcTestConfig.class)
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
    @DisplayName("받은 초대 목록 조회는 로그인 회원과 요청 상태로 필터링한다")
    void getReceivedInvitations_WithStatus_ReturnsList() throws Exception {
        // given
        ProjectCollaboratorInvitationResDTO.ReceivedList invitations =
                ProjectCollaboratorInvitationResDTO.ReceivedList.builder()
                        .invitations(List.of(ProjectCollaboratorInvitationResDTO.ReceivedItem.builder()
                                .invitationId(31L)
                                .projectTitle("InfraGEN")
                                .inviterNickname("project-owner")
                                .role(ProjectCollaboratorRole.VIEWER)
                                .status(ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING)
                                .build()))
                        .build();
        when(invitationQueryService.getReceivedInvitations(
                42L,
                ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING
        )).thenReturn(invitations);

        // when
        var response = mockMvc.perform(get(RECEIVED_URL)
                .queryParam("status", "PENDING")
                .with(authenticatedAs(42L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_10"))
                .andExpect(jsonPath("$.result.invitations[0].invitationId").value(31))
                .andExpect(jsonPath("$.result.invitations[0].projectTitle").value("InfraGEN"))
                .andExpect(jsonPath("$.result.invitations[0].inviterNickname").value("project-owner"))
                .andExpect(jsonPath("$.result.invitations[0].status").value("PENDING"));
        verify(invitationQueryService).getReceivedInvitations(
                42L,
                ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING
        );
    }

    @Test
    @DisplayName("status를 생략하면 받은 초대 전체 조회로 전달한다")
    void getReceivedInvitations_WithoutStatus_PassesNullFilter() throws Exception {
        // given
        ProjectCollaboratorInvitationResDTO.ReceivedList invitations =
                ProjectCollaboratorInvitationResDTO.ReceivedList.builder()
                        .invitations(List.of(
                                receivedInvitation(31L, ProjectCollaboratorInvitationResDTO.InvitationStatus.PENDING),
                                receivedInvitation(32L, ProjectCollaboratorInvitationResDTO.InvitationStatus.ACCEPTED)
                        ))
                        .build();
        when(invitationQueryService.getReceivedInvitations(42L, null)).thenReturn(invitations);

        // when
        var response = mockMvc.perform(get(RECEIVED_URL).with(authenticatedAs(42L)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.result.invitations.length()").value(2));
        verify(invitationQueryService).getReceivedInvitations(42L, null);
    }

    @Test
    @DisplayName("초대 수락 요청은 로그인 회원 ID를 전달하고 성공 코드를 반환한다")
    void acceptInvitation_AuthenticatedInvitee_ReturnsSuccess() throws Exception {
        // given
        Long invitationId = 31L;
        Long memberId = 42L;

        // when
        var response = mockMvc.perform(post(BASE_URL + "/{invitationId}/accept", invitationId)
                .with(authenticatedAs(memberId)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_11"));
        verify(invitationCommandService).accept(invitationId, memberId);
    }

    @Test
    @DisplayName("초대 거절 요청은 로그인 회원 ID를 전달하고 성공 코드를 반환한다")
    void declineInvitation_AuthenticatedInvitee_ReturnsSuccess() throws Exception {
        // given
        Long invitationId = 31L;
        Long memberId = 42L;

        // when
        var response = mockMvc.perform(post(BASE_URL + "/{invitationId}/decline", invitationId)
                .with(authenticatedAs(memberId)));

        // then
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PROJECT200_12"));
        verify(invitationCommandService).decline(invitationId, memberId);
    }

    private static ProjectCollaboratorInvitationResDTO.ReceivedItem receivedInvitation(
            Long invitationId,
            ProjectCollaboratorInvitationResDTO.InvitationStatus status
    ) {
        return ProjectCollaboratorInvitationResDTO.ReceivedItem.builder()
                .invitationId(invitationId)
                .projectTitle("InfraGEN")
                .inviterNickname("project-owner")
                .role(ProjectCollaboratorRole.VIEWER)
                .status(status)
                .build();
    }

    private static RequestPostProcessor authenticatedAs(Long memberId) {
        return request -> {
            MemberResDTO.MemberResultDTO member = MemberResDTO.MemberResultDTO.builder()
                    .id(memberId)
                    .email("test@test.com")
                    .nickname("invitee")
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
