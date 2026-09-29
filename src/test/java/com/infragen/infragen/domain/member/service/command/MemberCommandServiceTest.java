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
import com.infragen.infragen.domain.project.repository.ProjectCollaboratorInvitationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    private ProjectCollaboratorInvitationRepository invitationRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private Member member;

    @InjectMocks
    private MemberCommandService memberCommandService;

    @Test
    @DisplayName("활성 회원을 잠근 뒤 닉네임과 암호화한 비밀번호를 수정한다")
    void updateMember_Success() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");
        Member lockedMember = Member.builder().email("member@test.com").nickname("oldNickname")
                .password("oldEncodedPassword").role(Role.ROLE_USER).isActive(true).build();
        ReflectionTestUtils.setField(lockedMember, "id", 1L);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedMember));
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");

        // when
        var result = memberCommandService.updateMember(1L, request);

        // then
        var order = inOrder(memberRepository, passwordEncoder);
        order.verify(memberRepository).findByIdForUpdate(1L);
        order.verify(passwordEncoder).encode("newPassword123");
        assertAll(
                () -> assertEquals("newNickname", lockedMember.getNickname()),
                () -> assertEquals("encodedPassword", lockedMember.getPassword()),
                () -> assertEquals(1L, result.id()),
                () -> assertEquals("newNickname", result.nickname()),
                () -> assertTrue(result.isActive())
        );
        verify(memberRepository, never()).findById(anyLong());
        verifyNoInteractions(invitationRepository, tokenService);
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
                () -> assertTrue(savedMembers.get(1).getIsActive()),
                () -> assertTrue(savedMembers.get(0).getInvitationCode().matches("[A-Z0-9]{8}")),
                () -> assertTrue(savedMembers.get(1).getInvitationCode().matches("[A-Z0-9]{8}")),
                () -> assertNotEquals(
                        savedMembers.get(0).getInvitationCode(),
                        savedMembers.get(1).getInvitationCode()
                )
        );
        verify(passwordEncoder, times(2)).encode(anyString());
    }

    @Test
    void ensureInvitationCode_LegacyGuest_IssuesCodeOnce() {
        // given
        Member legacyGuest = guestMember();
        ReflectionTestUtils.setField(legacyGuest, "invitationCode", "0123456789abcdef0123456789abcdef");

        // when
        String issuedCode = legacyGuest.ensureInvitationCode();
        String repeatedCode = legacyGuest.ensureInvitationCode();

        // then
        assertAll(
                () -> assertTrue(issuedCode.matches("[A-Z0-9]{8}")),
                () -> assertNotEquals("0123456789abcdef0123456789abcdef", issuedCode),
                () -> assertEquals(issuedCode, repeatedCode)
        );
    }

    @Test
    void ensureInvitationCode_ExistingMember_ReturnsStoredCode() {
        // given
        Member existingMember = Member.builder().role(Role.ROLE_USER).build();
        String expectedCode = existingMember.getInvitationCode();
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(existingMember));

        // when
        MemberResDTO.InvitationCode result = memberCommandService.ensureInvitationCode(1L);

        // then
        assertEquals(expectedCode, result.inviteCode());
        verify(memberRepository).findByIdForUpdate(1L);
        verify(memberRepository, never()).countRowsByInvitationCode(anyString());
    }

    @Test
    void ensureInvitationCode_LegacyMember_ReplacesOldCodeAndPersistsCandidate() {
        // given
        Member legacyMember = Member.builder().role(Role.ROLE_USER).build();
        ReflectionTestUtils.setField(legacyMember, "id", 2L);
        ReflectionTestUtils.setField(legacyMember, "invitationCode", "0123456789abcdef0123456789abcdef");
        when(memberRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(legacyMember));
        when(memberRepository.countRowsByInvitationCodeExcludingMember(anyString(), eq(2L))).thenReturn(0L);

        // when
        MemberResDTO.InvitationCode result = memberCommandService.ensureInvitationCode(2L);

        // then
        assertAll(
                () -> assertTrue(result.inviteCode().matches("[A-Z0-9]{8}")),
                () -> assertEquals(result.inviteCode(), legacyMember.getInvitationCode()),
                () -> assertNotEquals("0123456789abcdef0123456789abcdef", result.inviteCode())
        );
        verify(memberRepository).findByIdForUpdate(2L);
        verify(memberRepository).countRowsByInvitationCodeExcludingMember(result.inviteCode(), 2L);
    }

    @Test
    void createGuestMember_CodeCollision_RegeneratesBeforeSave() {
        // given
        when(passwordEncoder.encode(anyString())).thenReturn("encodedGuestPassword");
        when(memberRepository.countRowsByInvitationCode(anyString())).thenReturn(1L, 0L);
        when(memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        memberCommandService.createGuestMember();

        // then
        verify(memberRepository, times(2)).countRowsByInvitationCode(anyString());
        verify(memberRepository).save(argThat(member -> member.getInvitationCode().matches("[A-Z0-9]{8}")));
    }

    @Test
    void createRegularMember_AssignsInvitationCode() {
        // given
        Member regularMember = Member.builder()
                .role(Role.ROLE_USER)
                .build();

        // when
        String invitationCode = regularMember.getInvitationCode();

        // then
        assertTrue(invitationCode.matches("[A-Z0-9]{8}"));
    }

    @Test
    void regenerateInvitationCode_PersistedMember_KeepsOriginalCode() {
        // given
        Member persistedMember = Member.builder()
                .role(Role.ROLE_USER)
                .build();
        ReflectionTestUtils.setField(persistedMember, "id", 7L);
        String originalCode = persistedMember.getInvitationCode();

        // when
        MemberException exception = assertThrows(
                MemberException.class,
                persistedMember::regenerateInvitationCode
        );

        // then
        assertEquals(MemberErrorCode.INVITATION_CODE_IMMUTABLE, exception.getCode());
        assertEquals(originalCode, persistedMember.getInvitationCode());
    }

    @Test
    void updateMember_NicknameOnly() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", null);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(member.getPassword()).thenReturn("oldEncodedPassword");

        // when
        memberCommandService.updateMember(1L, request);

        // then
        verify(member).updateProfile("newNickname", "oldEncodedPassword");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMember_PasswordOnly() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember(null, "newPassword123");
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
        when(member.getNickname()).thenReturn("oldNickname");
        when(passwordEncoder.encode(request.password())).thenReturn("encodedPassword");

        // when
        memberCommandService.updateMember(1L, request);

        // then
        verify(member).updateProfile("oldNickname", "encodedPassword");
    }

    @Test
    void updateMember_SocialMemberNicknameOnly_Success() {
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", null);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
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
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(member.getIsActive()).thenReturn(true);
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
        // given
        MemberReqDTO.UpdateMember request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> memberCommandService.updateMember(1L, request));

        // then
        assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode());
        verify(member, never()).updateProfile(any(), any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void updateMember_GuestMember_ThrowsForbidden() {
        // given
        when(memberRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(guestMember()));

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

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    @DisplayName("잠금 조회한 회원이 활성이 아니면 프로필을 수정하지 않는다")
    void updateMember_InactiveLockedMember_ThrowsMemberNotFound(Boolean active) {
        // given
        Member lockedMember = Member.builder().email("member@test.com").nickname("oldNickname")
                .password("oldEncodedPassword").role(Role.ROLE_USER).isActive(active).build();
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedMember));
        var request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");

        // when
        MemberException exception = assertThrows(MemberException.class,
                () -> memberCommandService.updateMember(1L, request));

        // then
        assertAll(
                () -> assertEquals(MemberErrorCode.MEMBER_NOT_FOUND, exception.getCode()),
                () -> assertEquals("oldNickname", lockedMember.getNickname()),
                () -> assertEquals("oldEncodedPassword", lockedMember.getPassword()),
                () -> assertEquals(active, lockedMember.getIsActive())
        );
        verifyNoInteractions(passwordEncoder, invitationRepository, tokenService);
    }

    @Test
    @DisplayName("회원 잠금 실패를 전파하며 비밀번호 암호화나 프로필 수정을 하지 않는다")
    void updateMember_MemberLockFailure_PropagatesWithoutMutation() {
        // given
        var failure = new PessimisticLockingFailureException("member lock failed");
        when(memberRepository.findByIdForUpdate(1L)).thenThrow(failure);
        var request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");

        // when
        var exception = assertThrows(PessimisticLockingFailureException.class,
                () -> memberCommandService.updateMember(1L, request));

        // then
        assertSame(failure, exception);
        verifyNoInteractions(member, passwordEncoder, invitationRepository, tokenService);
    }

    @Test
    @DisplayName("수정 필드가 없으면 잠금 조회한 현재 프로필을 유지한다")
    void updateMember_NoFields_PreservesLockedProfile() {
        // given
        Member lockedMember = Member.builder().email("member@test.com").nickname("currentNickname")
                .password("currentEncodedPassword").role(Role.ROLE_USER).isActive(true).build();
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedMember));
        var request = new MemberReqDTO.UpdateMember(null, null);

        // when
        var result = memberCommandService.updateMember(1L, request);

        // then
        assertAll(
                () -> assertEquals("currentNickname", result.nickname()),
                () -> assertEquals("currentEncodedPassword", lockedMember.getPassword()),
                () -> assertTrue(lockedMember.getIsActive())
        );
        verify(memberRepository).findByIdForUpdate(1L);
        verifyNoInteractions(passwordEncoder, invitationRepository, tokenService);
    }

    @Test
    @DisplayName("비밀번호 암호화 실패 때 닉네임과 비밀번호를 변경하지 않는다")
    void updateMember_PasswordEncodingFailure_PropagatesWithoutMutation() {
        // given
        Member lockedMember = Member.builder().email("member@test.com").nickname("oldNickname")
                .password("oldEncodedPassword").role(Role.ROLE_USER).isActive(true).build();
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lockedMember));
        var failure = new IllegalStateException("password encoding failed");
        when(passwordEncoder.encode("newPassword123")).thenThrow(failure);
        var request = new MemberReqDTO.UpdateMember("newNickname", "newPassword123");

        // when
        var exception = assertThrows(IllegalStateException.class,
                () -> memberCommandService.updateMember(1L, request));

        // then
        assertAll(
                () -> assertSame(failure, exception),
                () -> assertEquals("oldNickname", lockedMember.getNickname()),
                () -> assertEquals("oldEncodedPassword", lockedMember.getPassword())
        );
        verifyNoInteractions(invitationRepository, tokenService);
    }

    @Test
    void withdrawMember_Success() {
        // given
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        // when
        memberCommandService.withdrawMember(1L);

        // then
        var inOrder = inOrder(invitationRepository, member, tokenService);
        inOrder.verify(invitationRepository).deleteAllByMemberId(1L);
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
        verify(invitationRepository, never()).deleteAllByMemberId(anyLong());
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
        verify(invitationRepository, never()).deleteAllByMemberId(anyLong());
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
