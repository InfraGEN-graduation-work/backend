package com.infragen.infragen.domain.collaboration.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * commit된 operation version과 해당 시점의 graph payload를 checkpoint 저장 단계로 전달한다.
 */
public record ProjectCollaborationCheckpointEvent(
        Long projectId,
        Long memberId,
        Long serverVersion,
        Map<String, Object> graphPayload
) {
    public ProjectCollaborationCheckpointEvent {
        graphPayload = Collections.unmodifiableMap(new LinkedHashMap<>(graphPayload));
    }
}
