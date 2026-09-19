package com.infragen.infragen.domain.project.dto.request;

import com.infragen.infragen.domain.project.enums.ProjectCollaboratorRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class ProjectCollaboratorInvitationReqDTO {
    private ProjectCollaboratorInvitationReqDTO() {
    }

    public record Create(
            @NotBlank(message = "초대 코드는 필수 입력값입니다.")
            @Pattern(regexp = "^[A-Z0-9]{8}$", message = "초대 코드는 영문 대문자와 숫자 8자리여야 합니다.")
            String inviteeCode,
            @NotNull(message = "초대 역할은 필수 입력값입니다.")
            ProjectCollaboratorRole role
    ) {
    }
}
