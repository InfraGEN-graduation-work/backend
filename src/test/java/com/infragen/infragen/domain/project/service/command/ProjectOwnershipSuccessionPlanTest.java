package com.infragen.infragen.domain.project.service.command;

import com.infragen.infragen.domain.member.enums.Role;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Deletion;
import com.infragen.infragen.domain.project.service.command.ProjectOwnershipSuccessionPlan.Transfer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectOwnershipSuccessionPlanTest {
    @Test
    @DisplayName("승계 계획은 식별자와 기대 계정 유형이 모두 있어야 만들어진다")
    void transfer_MissingValue_ThrowsNullPointerException() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Transfer(null, 1L, 2L, Role.ROLE_USER)),
                () -> assertThrows(NullPointerException.class, () -> new Transfer(10L, null, 2L, Role.ROLE_USER)),
                () -> assertThrows(NullPointerException.class, () -> new Transfer(10L, 1L, null, Role.ROLE_USER)),
                () -> assertThrows(NullPointerException.class, () -> new Transfer(10L, 1L, 2L, null))
        );
    }

    @Test
    @DisplayName("삭제 계획은 project와 떠나는 회원 식별자가 있어야 만들어진다")
    void deletion_MissingValue_ThrowsNullPointerException() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new Deletion(null, 1L)),
                () -> assertThrows(NullPointerException.class, () -> new Deletion(10L, null))
        );
    }
}
