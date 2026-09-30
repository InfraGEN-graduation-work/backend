package com.infragen.infragen.domain.member.converter;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.member.dto.response.MemberResDTO;
import com.infragen.infragen.domain.member.enums.WithdrawalProjectOutcome;
import com.infragen.infragen.domain.project.repository.projection.OwnedProjectWithdrawalPreview;

class MemberConverterTest {

    @Test
    @DisplayName("toWithdrawalPreview_후보 유무별_입력 순서대로 승계와 삭제로 표시한다")
    void toWithdrawalPreview_SuccessorAvailability_MapsOutcomeInOrder() {
        // given
        List<OwnedProjectWithdrawalPreview> previews = List.of(
                preview(1L, "승계 프로젝트", true),
                preview(2L, "삭제 프로젝트", false));

        // when
        MemberResDTO.WithdrawalPreview response = MemberConverter.toWithdrawalPreview(previews);

        // then
        assertAll(
                () -> assertEquals(List.of(1L, 2L),
                        response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::projectId).toList()),
                () -> assertEquals(List.of("승계 프로젝트", "삭제 프로젝트"),
                        response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::title).toList()),
                () -> assertEquals(List.of(WithdrawalProjectOutcome.SUCCESSION, WithdrawalProjectOutcome.DELETION),
                        response.ownedProjects().stream().map(MemberResDTO.WithdrawalOwnedProject::outcome).toList()));
    }

    @Test
    @DisplayName("toWithdrawalPreview_후보 유무 값이 없으면_삭제로 표시한다")
    void toWithdrawalPreview_NullSuccessorFlag_MapsToDeletion() {
        // given
        List<OwnedProjectWithdrawalPreview> previews = List.of(preview(3L, "값 없음", null));

        // when
        MemberResDTO.WithdrawalPreview response = MemberConverter.toWithdrawalPreview(previews);

        // then
        assertEquals(WithdrawalProjectOutcome.DELETION, response.ownedProjects().get(0).outcome());
    }

    @Test
    @DisplayName("toWithdrawalPreview_소유 프로젝트가 없으면_빈 목록을 담는다")
    void toWithdrawalPreview_NoOwnedProject_ReturnsEmptyList() {
        // given
        List<OwnedProjectWithdrawalPreview> previews = List.of();

        // when
        MemberResDTO.WithdrawalPreview response = MemberConverter.toWithdrawalPreview(previews);

        // then
        assertTrue(response.ownedProjects().isEmpty());
    }

    private OwnedProjectWithdrawalPreview preview(Long projectId, String title, Boolean hasSuccessor) {
        OwnedProjectWithdrawalPreview preview = mock(OwnedProjectWithdrawalPreview.class);
        when(preview.getProjectId()).thenReturn(projectId);
        when(preview.getTitle()).thenReturn(title);
        when(preview.getHasSuccessor()).thenReturn(hasSuccessor);
        return preview;
    }
}
