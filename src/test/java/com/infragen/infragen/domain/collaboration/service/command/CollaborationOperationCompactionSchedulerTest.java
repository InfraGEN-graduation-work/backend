package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.global.properties.CollaborationCompactionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationCompactionSchedulerTest {
    @Mock
    private ProjectCollaborationSnapshotRepository snapshotRepository;

    @Mock
    private CollaborationOperationCompactionService compactionService;

    @Test
    @DisplayName("compact가 비활성화되면 project를 조회하거나 삭제하지 않는다")
    void compactOperationLogs_Disabled_DoesNothing() {
        // given
        CollaborationCompactionProperties properties = properties(false);
        CollaborationOperationCompactionScheduler scheduler = new CollaborationOperationCompactionScheduler(
                properties,
                snapshotRepository,
                compactionService
        );

        // when
        scheduler.compactOperationLogs();

        // then
        verify(snapshotRepository, never()).findDistinctProjectIds();
        verify(compactionService, never()).compact(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("compact가 활성화되면 snapshot이 있는 project마다 retention 정책으로 compact한다")
    void compactOperationLogs_Enabled_CompactsEachProject() {
        // given
        CollaborationCompactionProperties properties = properties(true);
        properties.setRetentionVersions(100L);
        when(snapshotRepository.findDistinctProjectIds()).thenReturn(List.of(1L, 2L));
        CollaborationOperationCompactionScheduler scheduler = new CollaborationOperationCompactionScheduler(
                properties,
                snapshotRepository,
                compactionService
        );

        // when
        scheduler.compactOperationLogs();

        // then
        verify(compactionService).compact(1L, 100L);
        verify(compactionService).compact(2L, 100L);
    }

    private CollaborationCompactionProperties properties(boolean enabled) {
        CollaborationCompactionProperties properties = new CollaborationCompactionProperties();
        properties.setEnabled(enabled);
        return properties;
    }
}
