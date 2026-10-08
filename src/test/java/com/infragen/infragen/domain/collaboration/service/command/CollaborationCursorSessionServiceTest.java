package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.dto.response.CollaborationCursorResDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationCursorSessionServiceTest {
    private final CollaborationCursorSessionService service = new CollaborationCursorSessionService();

    @Test
    @DisplayName("커서를 보여 주던 연결이 끝나면 회원·커서 식별자를 담은 숨김 메시지를 만든다")
    void release_visibleCursor_returnsHiddenForProject() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release("session-a");

        // then
        CollaborationCursorResDTO.BroadcastCursorResDTO hidden = result.get(10L);
        assertAll(
                () -> assertEquals(Set.of(10L), result.keySet()),
                () -> assertEquals(2L, hidden.actorMemberId()),
                () -> assertEquals("cursor-a", hidden.cursorId()),
                () -> assertEquals(false, hidden.visible()),
                () -> assertNull(hidden.x()),
                () -> assertNull(hidden.y())
        );
    }

    @Test
    @DisplayName("한 연결이 여러 project에서 보여 주던 커서를 모두 숨긴다")
    void release_multipleProjects_returnsHiddenForEachProject() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.track("session-a", 2L, "cursor-a", 20L, true);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release("session-a");

        // then
        assertEquals(Set.of(10L, 20L), result.keySet());
    }

    @Test
    @DisplayName("같은 project에 표시를 반복해도 숨김 메시지는 하나다")
    void release_repeatedVisibleInSameProject_returnsSingleHidden() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.track("session-a", 2L, "cursor-a", 10L, true);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release("session-a");

        // then
        assertEquals(Set.of(10L), result.keySet());
    }

    @Test
    @DisplayName("이미 숨긴 project는 연결이 끝나도 다시 숨기지 않고 다른 project만 숨긴다")
    void release_projectAlreadyHidden_excludesThatProject() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.track("session-a", 2L, "cursor-a", 20L, true);
        service.track("session-a", 2L, "cursor-a", 10L, false);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release("session-a");

        // then
        assertEquals(Set.of(20L), result.keySet());
    }

    @Test
    @DisplayName("모든 커서를 숨겼으면 연결이 끝나도 보낼 것이 없다")
    void release_allProjectsHidden_returnsEmpty() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.track("session-a", 2L, "cursor-a", 10L, false);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release("session-a");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("종료 이벤트가 중복돼도 두 번째 정리는 빈 결과다")
    void release_calledTwice_secondIsEmpty() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.release("session-a");

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> second = service.release("session-a");

        // then
        assertTrue(second.isEmpty());
    }

    @Test
    @DisplayName("커서를 보낸 적 없는 연결의 정리는 빈 결과다")
    void release_unknownSession_returnsEmpty() {
        // given
        String sessionId = "never-shared";

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> result = service.release(sessionId);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("같은 회원의 다른 연결은 한 연결이 끝나도 유지된다")
    void release_oneSession_preservesOtherSessionOfSameMember() {
        // given
        service.track("session-a", 2L, "cursor-a", 10L, true);
        service.track("session-b", 2L, "cursor-b", 10L, true);

        // when
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> released = service.release("session-a");
        Map<Long, CollaborationCursorResDTO.BroadcastCursorResDTO> remaining = service.release("session-b");

        // then
        assertAll(
                () -> assertEquals("cursor-a", released.get(10L).cursorId()),
                () -> assertEquals("cursor-b", remaining.get(10L).cursorId())
        );
    }
}
