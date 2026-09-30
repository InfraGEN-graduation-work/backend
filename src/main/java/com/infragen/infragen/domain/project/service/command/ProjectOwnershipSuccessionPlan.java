package com.infragen.infragen.domain.project.service.command;

import java.util.Objects;

import com.infragen.infragen.domain.member.enums.Role;

/**
 * 탈퇴하는 owner의 프로젝트 한 건을 어떻게 처리할지 정한 실행 계획이다.
 * 승계 대상 선정(prepare)과 실제 변경(execute) 사이에 전달되며, 호출자는 승계 대상 회원 ID를 모아
 * 실행 전에 회원 잠금 목록을 만든다.
 * bulk 삭제가 영속성 context를 비워도 값이 유지되도록 Entity 대신 식별자와 기대 계정 유형만 보관한다.
 * 같은 transaction 안에서만 쓰는 일시 값이며 저장하거나 API로 노출하지 않는다.
 */
public sealed interface ProjectOwnershipSuccessionPlan {
    Long projectId();

    Long departingMemberId();

    /**
     * 선정한 참여자를 새 owner로 승격하는 계획이다.
     * 실행 시 successor의 계정 유형이 계획 당시와 다르면 재선정하지 않고 중단한다.
     */
    record Transfer(Long projectId, Long departingMemberId, Long successorMemberId, Role successorRole)
            implements ProjectOwnershipSuccessionPlan {
        public Transfer {
            Objects.requireNonNull(projectId);
            Objects.requireNonNull(departingMemberId);
            Objects.requireNonNull(successorMemberId);
            Objects.requireNonNull(successorRole);
        }
    }

    /** 활성 승계 후보가 없어 프로젝트와 종속 데이터를 hard delete하는 계획이다. */
    record Deletion(Long projectId, Long departingMemberId) implements ProjectOwnershipSuccessionPlan {
        public Deletion {
            Objects.requireNonNull(projectId);
            Objects.requireNonNull(departingMemberId);
        }
    }
}
