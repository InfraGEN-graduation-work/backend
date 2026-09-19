package com.infragen.infragen.domain.project.enums;

/**
 * DB에 저장하는 초대 상태다. 만료 여부는 초대 만료 시각으로 판단한다.
 */
public enum ProjectCollaboratorInvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
