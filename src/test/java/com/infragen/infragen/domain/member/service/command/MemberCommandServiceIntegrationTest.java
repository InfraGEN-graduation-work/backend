package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
class MemberCommandServiceIntegrationTest {
    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.0")
    ).withDatabaseName("infragen_test")
            .withUsername("infragen_test")
            .withPassword("infragen_test_password");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("spring.data.redis.password", () -> "");
        registry.add("jwt.secret", () -> "test-jwt-secret-test-jwt-secret-test-jwt-secret-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.client-secret", () -> "test-kakao-client-secret");
        registry.add("kakao.redirect-uri", () -> "http://localhost/test-callback");
        registry.add("kakao.authorization-uri", () -> "http://localhost/kakao/authorize");
        registry.add("kakao.token-uri", () -> "http://localhost/kakao/token");
        registry.add("kakao.user-info-uri", () -> "http://localhost/kakao/user-info");
    }

    @Autowired
    private MemberCommandService memberCommandService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void updateMember_PersistsToDatabase() {
        Member member = memberRepository.save(Member.builder()
                .email("member@test.com")
                .password("old-password")
                .nickname("oldNickname")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build());

        memberCommandService.updateMember(member.getId(),
                new MemberReqDTO.UpdateMember("newNickname", "newPassword123"));

        Member updated = memberRepository.findById(member.getId()).orElseThrow();
        assertEquals("newNickname", updated.getNickname());
        assertNotEquals("newPassword123", updated.getPassword());
    }

    @Test
    void updateMember_SocialMemberPasswordIncluded_DoesNotPersistChanges() {
        // given
        Member member = memberRepository.save(Member.builder()
                .email("social@test.com")
                .password("random-encoded-password")
                .nickname("oldNickname")
                .role(Role.ROLE_USER)
                .isActive(true)
                .socialProvider(SocialProvider.KAKAO)
                .socialId("social-member-id")
                .build());

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> memberCommandService.updateMember(member.getId(),
                        new MemberReqDTO.UpdateMember("newNickname", "newPassword123")));

        // then
        Member unchanged = memberRepository.findById(member.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(MemberErrorCode.CANNOT_CHANGE_SOCIAL_PASSWORD, exception.getCode()),
                () -> assertEquals("oldNickname", unchanged.getNickname()),
                () -> assertEquals("random-encoded-password", unchanged.getPassword())
        );
    }

    @Test
    void withdrawMember_PersistsSoftDeleteToDatabase() {
        Member member = memberRepository.save(Member.builder()
                .email("withdraw@test.com")
                .password("password")
                .nickname("nickname")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build());

        memberCommandService.withdrawMember(member.getId());

        Integer active = jdbcTemplate.queryForObject(
                "select is_active from member where id = ?", Integer.class, member.getId());
        assertEquals(0, active);
        assertTrue(memberRepository.findById(member.getId()).isEmpty());
    }
}
