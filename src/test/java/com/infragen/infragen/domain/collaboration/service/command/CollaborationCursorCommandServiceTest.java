package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.request.CollaborationCursorReqDTO;
import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import com.infragen.infragen.domain.collaboration.exception.CollaborationException;
import com.infragen.infragen.domain.collaboration.exception.code.error.CollaborationErrorCode;
import com.infragen.infragen.domain.collaboration.validator.CollaborationCursorValidator;
import com.infragen.infragen.domain.project.exception.ProjectException;
import com.infragen.infragen.domain.project.exception.code.error.ProjectErrorCode;
import com.infragen.infragen.domain.project.service.query.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CollaborationCursorCommandServiceTest {
    @Mock
    private ProjectAccessService projectAccessService;

    @Mock
    private CollaborationCursorValidator cursorValidator;

    @InjectMocks
    private CollaborationCursorCommandService service;

    @Test
    @DisplayName("읽기 권한 확인과 검증 뒤 인증된 회원 ID와 서버 식별자를 붙여 반환한다")
    void shareCursor_validCursor_returnsBroadcastWithServerIdentity() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 10.0, 20.0);

        // when
        CollaborationCursorResDTO.BroadcastCursorResDTO result = service.shareCursor(1L, 2L, "session-a", cursor);

        // then
        InOrder order = inOrder(projectAccessService, cursorValidator);
        order.verify(projectAccessService).requireReadAccess(1L, 2L);
        order.verify(cursorValidator).validate(cursor);
        assertAll(
                () -> assertEquals(2L, result.actorMemberId()),
                () -> assertEquals(true, result.visible()),
                () -> assertEquals(10.0, result.x()),
                () -> assertEquals(20.0, result.y())
        );
    }

    @Test
    @DisplayName("cursorId는 session ID를 그대로 노출하지 않고 같은 연결에는 같은 값, 다른 연결에는 다른 값을 준다")
    void shareCursor_cursorId_isStablePerSessionAndHidesSessionId() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(false, null, null);

        // when
        String first = service.shareCursor(1L, 2L, "session-a", cursor).cursorId();
        String again = service.shareCursor(1L, 2L, "session-a", cursor).cursorId();
        String otherTab = service.shareCursor(1L, 2L, "session-b", cursor).cursorId();

        // then
        assertAll(
                () -> assertEquals(first, again),
                () -> assertNotEquals(first, otherTab),
                () -> assertNotEquals("session-a", first)
        );
    }

    @Test
    @DisplayName("읽기 권한이 없으면 검증과 전달 없이 권한 예외를 던진다")
    void shareCursor_accessDenied_throwsBeforeValidation() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, 10.0, 20.0);
        doThrow(new ProjectException(ProjectErrorCode.PROJECT_ACCESS_DENIED))
                .when(projectAccessService).requireReadAccess(1L, 2L);

        // when
        ProjectException exception = assertThrows(
                ProjectException.class,
                () -> service.shareCursor(1L, 2L, "session-a", cursor)
        );

        // then
        assertEquals(ProjectErrorCode.PROJECT_ACCESS_DENIED, exception.getCode());
        verifyNoInteractions(cursorValidator);
    }

    @Test
    @DisplayName("검증에 실패하면 커서 오류를 그대로 던진다")
    void shareCursor_invalidCursor_propagatesValidationError() {
        // given
        CollaborationCursorReqDTO.Cursor cursor = new CollaborationCursorReqDTO.Cursor(true, null, null);
        doThrow(new CollaborationException(CollaborationErrorCode.INVALID_CURSOR))
                .when(cursorValidator).validate(cursor);

        // when
        CollaborationException exception = assertThrows(
                CollaborationException.class,
                () -> service.shareCursor(1L, 2L, "session-a", cursor)
        );

        // then
        assertEquals(CollaborationErrorCode.INVALID_CURSOR, exception.getCode());
        verify(projectAccessService).requireReadAccess(1L, 2L);
    }
}
