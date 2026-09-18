package com.infragen.infragen.domain.member.service.command;

import com.infragen.infragen.domain.auth.service.TokenService;
import com.infragen.infragen.domain.auth.service.EmailVerificationService;
import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.domain.auth.exception.AuthException;
import com.infragen.infragen.domain.auth.exception.code.error.AuthErrorCode;
import com.infragen.infragen.domain.member.dto.request.MemberReqDTO;
import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.member.enums.SocialProvider;
import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberCommandServiceTest {
    @Mock
    private EmailVerificationService emailVerificationService;

    @Test
    void createMember_InvalidEmailCode_DoesNotCreateMember() {
        // given
        var request = AuthReqDTO.SignupDTO.builder().email("user@example.com")
                .password("password123").nickname("user").verificationCode("123456").build();
        doThrow(new AuthException(AuthErrorCode.EMAIL_CODE_INVALID))
                .when(emailVerificationService).verifyAndConsume("user@example.com", "123456");

        // when
        AuthException error = assertThrows(AuthException.class, () -> memberCommandService.createMember(request));

        // then
        assertEquals(AuthErrorCode.EMAIL_CODE_INVALID, error.getCode());
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createMember_ValidEmailCode_VerifiesBeforeSaving() {
        // given
        var request = AuthReqDTO.SignupDTO.builder().email("user@example.com")
                .password("password123").nickname("user").verificationCode("012345").build();
        when(passwordEncoder.encode("password123")).thenReturn("encoded");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var result = memberCommandService.createMember(request);

        // then
        var order = inOrder(emailVerificationService, memberRepository);
        order.verify(emailVerificationService).verifyAndConsume("user@example.com", "012345");
        order.verify(memberRepository).save(any(Member.class));
        assertEquals("user@example.com", result.email());
    }

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
    void createGuestMember_CreatesUniqueGuestMembers() {
        // given
        when(passwordEncoder.encode(anyString())).thenReturn("encodedGuestPassword");
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        MemberResDTO.MemberResultDTO first = memberCommandService.createGuestMember();
        MemberResDTO.MemberResultDTO second = memberCommandService.createGuestMember();

        // then
        ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository, times(2)).save(memberCaptor.capture());
        List<Member> savedMembers = memberCaptor.getAllValues();

        assertAll(
                () -> assertEquals(Role.ROLE_GUEST, first.role()),
                () -> assertEquals(Role.ROLE_GUEST, second.role()),
                () -> assertNotEquals(first.email(), second.email()),
                () -> assertTrue(savedMembers.get(0).getEmail().startsWith("guest-")),
                () -> assertTrue(savedMembers.get(1).getEmail().startsWith("guest-")),
                () -> assertEquals(Role.ROLE_GUEST, savedMembers.get(0).getRole()),
                () -> assertEquals(Role.ROLE_GUEST, savedMembers.get(1).getRole()),
                () -> assertTrue(savedMembers.get(0).getIsActive()),
                () -> assertTrue(savedMembers.get(1).getIsActive())
        );
        verify(passwordEncoder, times(2)).encode(anyString());
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
    void updateMember_GuestMember_ThrowsForbidden() {
        // given
        when(memberRepository.findById(99L)).thenReturn(Optional.of(guestMember()));

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                () -> memberCommandService.updateMember(
                        99L,
                        new MemberReqDTO.UpdateMember("new-name", null)
                )
        );

        // then
        assertEquals(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED, exception.getCode());
        verifyNoInteractions(passwordEncoder);
        verify(tokenService, never()).deleteRefreshToken(99L);
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

    @Test
    void withdrawMember_GuestMember_ThrowsForbidden() {
        // given
        when(memberRepository.findById(99L)).thenReturn(Optional.of(guestMember()));

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                () -> memberCommandService.withdrawMember(99L)
        );

        // then
        assertEquals(MemberErrorCode.GUEST_ACTION_NOT_ALLOWED, exception.getCode());
        verify(member, never()).withdraw();
        verify(tokenService, never()).deleteRefreshToken(99L);
    }

    private Member guestMember() {
        return Member.builder()
                .email("guest-99@guest.infragen.local")
                .password("encoded")
                .nickname("Guest 99")
                .role(Role.ROLE_GUEST)
                .isActive(true)
                .build();
    }
}
