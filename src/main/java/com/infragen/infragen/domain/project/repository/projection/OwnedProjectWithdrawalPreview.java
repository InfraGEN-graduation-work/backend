package com.infragen.infragen.domain.project.repository.projection;

/**
 * 회원 탈퇴 전 안내에서 소유 프로젝트 한 개가 승계될지 삭제될지 보여주기 위한 값이다.
 * 탈퇴 확정 화면이 삭제될 프로젝트를 사용자에게 알리는 데만 쓰며, API 응답으로 직접 노출하지 않는다.
 */
public interface OwnedProjectWithdrawalPreview {
    Long getProjectId();
    String getTitle();

    /** 활성 참여자가 있어 탈퇴 시 승계 대상이 있으면 true, 없어서 프로젝트가 삭제되면 false다. */
    Boolean getHasSuccessor();
}
