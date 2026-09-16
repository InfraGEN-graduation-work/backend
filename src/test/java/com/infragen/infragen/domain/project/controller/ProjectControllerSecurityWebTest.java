package com.infragen.infragen.domain.project.controller;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import com.infragen.infragen.domain.project.dto.request.ProjectReqDTO;
import com.infragen.infragen.domain.project.dto.response.ProjectResDTO;
import com.infragen.infragen.domain.project.service.command.ProjectCommandService;
import com.infragen.infragen.domain.project.service.query.ProjectQueryService;
import com.infragen.infragen.global.apiPayload.handler.GeneralExceptionAdvice;
import com.infragen.infragen.global.auth.AuthenticationEntryPointImpl;
import com.infragen.infragen.global.auth.CustomUserDetailsService;
import com.infragen.infragen.global.auth.filter.JwtAuthFilter;
import com.infragen.infragen.global.auth.filter.JwtExceptionFilter;
import com.infragen.infragen.global.config.SecurityConfig;
import com.infragen.infragen.global.properties.JwtProperties;
import com.infragen.infragen.global.util.JwtUtil;
import com.infragen.infragen.global.util.RedisUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/** 실제 JWT filter chain에서 인증 사용자 식별자를 목록과 PATCH로 전달하는지 확인한다. */
@WebMvcTest(controllers = ProjectController.class, properties = {
        "cors.allowed-origins=http://localhost",
        "jwt.secret=issue55-test-secret-issue55-test-secret-issue55-test-secret",
        "jwt.access-token.expiration-time=60000",
        "jwt.refresh-token.expiration-time=120000",
        "jwt.dev-token.expiration-time=60000"
})
@ContextConfiguration(classes = ProjectControllerSecurityWebTest.Config.class)
class ProjectControllerSecurityWebTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    @Import({ProjectController.class, GeneralExceptionAdvice.class, SecurityConfig.class,
            JwtUtil.class, JwtAuthFilter.class, JwtExceptionFilter.class,
            AuthenticationEntryPointImpl.class, CustomUserDetailsService.class})
    static class Config {
    }

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean RedisUtil redisUtil;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean ProjectCommandService commandService;
    @MockitoBean ProjectQueryService queryService;

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    @DisplayName("목록과 metadata PATCH는 인증이 없으면 401로 거부한다")
    void projectEndpoints_Anonymous_Unauthorized(String method) throws Exception {
        // given
        var request = request(method);

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isUnauthorized());
        verifyNoInteractions(commandService, queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PATCH"})
    @DisplayName("목록과 metadata PATCH는 잘못된 JWT를 거부한다")
    void projectEndpoints_InvalidJwt_Unauthorized(String method) throws Exception {
        // given
        var request = request(method).header("Authorization", "Bearer invalid");

        // when
        var response = mockMvc.perform(request);

        // then
        response.andExpect(status().isUnauthorized());
        verifyNoInteractions(commandService, queryService);
    }

    @Test
    @DisplayName("목록과 PATCH는 요청 memberId 대신 검증된 JWT subject를 사용한다")
    void projectEndpoints_ValidJwt_UseAuthenticatedMember() throws Exception {
        // given
        Member member = Member.builder().email("test@example.com").nickname("tester")
                .password("test-only").isActive(true).role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(member, "id", 7L);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(queryService.getProjects(7L)).thenReturn(
                ProjectResDTO.ProjectPreviewListResDTO.builder().projectList(List.of()).build());
        var metadata = new ProjectReqDTO.UpdateMetadata("New", null, 0L);
        when(commandService.updateMetadata(1L, metadata, 7L)).thenReturn(
                ProjectResDTO.ProjectPreviewResDTO.builder().projectId(1L).title("New").accessRole("OWNER").build());
        String token = jwtUtil.createAccessToken(7L, Role.ROLE_USER);

        // when
        var listResponse = mockMvc.perform(request("GET").param("memberId", "999")
                .header("Authorization", "Bearer " + token));
        var patchResponse = mockMvc.perform(request("PATCH").param("memberId", "999")
                .header("Authorization", "Bearer " + token));

        // then
        listResponse.andExpect(status().isOk());
        patchResponse.andExpect(status().isOk());
        verify(queryService).getProjects(7L);
        verify(commandService).updateMetadata(1L, metadata, 7L);
    }

    private MockHttpServletRequestBuilder request(String method) {
        return method.equals("GET") ? get("/api/v1/projects")
                : patch("/api/v1/projects/1/metadata").contentType(APPLICATION_JSON)
                        .content("{\"title\":\"New\",\"baseVersion\":0}");
    }
}
