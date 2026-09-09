package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberCommandServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private Member member;

    @InjectMocks
    private MemberCommandService memberCommandService;

    @Test
    void updateMember_Success() {
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");
        when(member.getId()).thenReturn(1L);
        when(member.getEmail()).thenReturn("member@test.com");
        when(member.getNickname()).thenReturn("newNickname");

        var result = memberCommandService.updateMember(1L, request);

        verify(passwordEncoder).encode("newPassword123");
        verify(member).updateProfile("newNickname", "encodedPassword");
        assertEquals(1L, result.id());
        assertEquals("newNickname", result.nickname());
    }

    @Test
    void updateMember_NicknameOnly() {
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(member.getPassword()).thenReturn("oldEncodedPassword");

        memberCommandService.updateMember(1L, request);

        verify(member).updateProfile("newNickname", "oldEncodedPassword");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMember_PasswordOnly() {
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember(null, "newPassword123");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(member.getNickname()).thenReturn("oldNickname");
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");

        memberCommandService.updateMember(1L, request);

        verify(member).updateProfile("oldNickname", "encodedPassword");
    }

    @Test
    void updateMember_SocialMemberNicknameOnly_Success() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", null);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(member.getSocialProvider()).thenReturn(SocialProvider.KAKAO);
        when(member.getPassword()).thenReturn("randomEncodedPassword");

        // when
        memberCommandService.updateMember(1L, request);

        // then
        verify(member).updateProfile("newNickname", "randomEncodedPassword");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMember_SocialMemberPasswordIncluded_ThrowsException() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(member.getSocialProvider()).thenReturn(SocialProvider.KAKAO);

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> memberCommandService.updateMember(1L, request));

        // then
        assertEquals(MemberErrorCode.CANNOT_CHANGE_SOCIAL_PASSWORD, exception.getCode());
        verifyNoInteractions(passwordEncoder);
        verify(member, never()).updateProfile(any(), any());
    }

    @Test
    void updateMember_MemberNotFound() {
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(MemberException.class, () -> memberCommandService.updateMember(1L, request));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void withdrawMember_Success() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        // when
        memberCommandService.withdrawMember(1L);

        // then
        var inOrder = inOrder(member, tokenService);
        inOrder.verify(member).withdraw();
        inOrder.verify(tokenService).deleteRefreshToken(1L);
    }

    @Test
    void withdrawMember_MemberNotFound() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> memberCommandService.withdrawMember(1L));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(member, never()).withdraw();
        verifyNoInteractions(tokenService);
    }
}
