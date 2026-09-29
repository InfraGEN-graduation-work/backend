package com.infragen.infragen.domain.member.enums;

/**
 * 회원 탈퇴 때 소유 프로젝트가 어떻게 처리되는지 사용자에게 알리는 결과 구분이다.
 * 탈퇴 전 안내 응답에서 삭제될 프로젝트를 구분하는 데 쓰며, 저장하지 않는다.
 */
public enum WithdrawalProjectOutcome {
    /** 활성 참여자가 있어 그 중 한 명이 새 owner가 되고 프로젝트는 유지된다. */
    SUCCESSION,

    /** 승계할 활성 참여자가 없어 프로젝트와 종속 데이터가 삭제된다. */
    DELETION
}
