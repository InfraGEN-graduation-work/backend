package com.infragen.infragen.domain.member.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class MemberReqDTO {
    private MemberReqDTO() {
    }

    public record UpdateMember(
            @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
            String nickname,
            @Pattern(regexp = ".*\\S.*", message = "비밀번호는 공백만 입력할 수 없습니다.")
            @Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
            String password
    ) {
        @AssertTrue(message = "수정할 회원 정보가 하나 이상 필요합니다.")
        public boolean hasUpdateValue() {
            return nickname != null || password != null;
        }
    }
}
