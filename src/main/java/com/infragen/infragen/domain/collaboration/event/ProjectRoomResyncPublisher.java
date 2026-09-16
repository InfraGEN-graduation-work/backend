package com.infragen.infragen.domain.collaboration.event;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PUT 또는 metadata PATCH가 commit된 뒤 같은 project room에 최신 graph를 broadcast한다.
 */
@Component
@RequiredArgsConstructor
public class ProjectRoomResyncPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ProjectRoomResyncEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/projects/" + event.projectId() + "/resync",
                event.snapshot()
        );
    }
}
