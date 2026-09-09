package com.infragen.infragen.domain.collaboration.service.command;

import com.infragen.infragen.domain.collaboration.entity.ProjectCollaborationSnapshot;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationOperationRepository;
import com.infragen.infragen.domain.collaboration.repository.ProjectCollaborationSnapshotRepository;
import com.infragen.infragen.domain.member.entity.Member;
import com.infragen.infragen.domain.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborationOperationCompactionServiceTest {
    @Mock
    private ProjectCollaborationOperationRepository operationRepository;

    @Mock
    private ProjectCollaborationSnapshotRepository snapshotRepository;

    @InjectMocks
    private CollaborationOperationCompactionService compactionService;

    @Test
    @DisplayName("snapshot이 없으면 operation log를 삭제하지 않는다")
    void compact_withoutSnapshot_doesNotDeleteOperationLog() {
        // given
        Long projectId = 1L;
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.empty());

        // when
        int deletedCount = compactionService.compact(projectId);

        // then
        assertEquals(0, deletedCount);
        verify(operationRepository, never())
                .deleteAllByProjectIdAndServerVersionLessThanEqual(eq(projectId), eq(0L));
    }

    @Test
    @DisplayName("snapshot version 이하의 operation log를 삭제한다")
    void compact_withSnapshot_deletesLogsThroughSnapshotVersion() {
        // given
        Long projectId = 1L;
        ProjectCollaborationSnapshot snapshot = ProjectCollaborationSnapshot.builder()
                .project(Project.builder().title("project").build())
                .updatedBy(Member.builder().isActive(true).build())
                .serverVersion(10L)
                .graphPayload(Map.of("projectId", projectId))
                .build();
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.of(snapshot));
        when(operationRepository.deleteAllByProjectIdAndServerVersionLessThanEqual(projectId, 10L))
                .thenReturn(10);

        // when
        int deletedCount = compactionService.compact(projectId);

        // then
        assertEquals(10, deletedCount);
        verify(operationRepository)
                .deleteAllByProjectIdAndServerVersionLessThanEqual(projectId, 10L);
    }

    @Test
    @DisplayName("retention version만큼 최근 operation log를 보존한다")
    void compact_withRetentionVersions_preservesRecentOperationLogs() {
        // given
        Long projectId = 1L;
        ProjectCollaborationSnapshot snapshot = ProjectCollaborationSnapshot.builder()
                .serverVersion(10L)
                .graphPayload(Map.of("projectId", projectId))
                .build();
        when(snapshotRepository.findTopByProjectIdOrderByServerVersionDesc(projectId))
                .thenReturn(Optional.of(snapshot));
        when(operationRepository.deleteAllByProjectIdAndServerVersionLessThanEqual(projectId, 7L))
                .thenReturn(7);

        // when
        int deletedCount = compactionService.compact(projectId, 3L);

        // then
        assertEquals(7, deletedCount);
        verify(operationRepository)
                .deleteAllByProjectIdAndServerVersionLessThanEqual(projectId, 7L);
    }
}
