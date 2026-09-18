package com.infragen.infragen.domain.member.service.query;

import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberQueryServiceTest {
    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberQueryService memberQueryService;

    @Test
    @DisplayName("초대코드로 활성 회원을 조회한다")
    void findByInvitationCode_ActiveMember_ReturnsMember() {
        // given
        String invitationCode = "A1B2C3D4";
        Member member = Member.builder()
                .email("member@test.com")
                .password("encodedPassword")
                .nickname("member")
                .role(Role.ROLE_USER)
                .isActive(true)
                .build();
        when(memberRepository.findByInvitationCode(invitationCode))
                .thenReturn(Optional.of(member));

        // when
        Member result = memberQueryService.findByInvitationCode(invitationCode);

        // then
        assertSame(member, result);
        verify(memberRepository).findByInvitationCode(invitationCode);
    }

    @Test
    @DisplayName("활성 회원이 없는 초대코드는 회원 없음 예외를 반환한다")
    void findByInvitationCode_NoActiveMember_ThrowsMemberNotFound() {
        // given
        String invitationCode = "A1B2C3D4";
        when(memberRepository.findByInvitationCode(invitationCode))
                .thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                () -> memberQueryService.findByInvitationCode(invitationCode)
        );

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(memberRepository).findByInvitationCode(invitationCode);
    }
}
