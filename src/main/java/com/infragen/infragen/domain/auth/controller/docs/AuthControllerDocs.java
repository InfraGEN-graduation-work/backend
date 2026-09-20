package com.infragen.infragen.domain.auth.controller.docs;

import com.infragen.infragen.domain.auth.dto.request.AuthReqDTO;
import com.infragen.infragen.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;

public interface AuthControllerDocs {
    @Operation(summary = "회원가입 이메일 인증번호 발송", description = "신규 이메일은 인증번호 발송 후 200(AUTH200_4)을 반환합니다. 이미 가입된 이메일은 발송하지 않고 409(MEMBER409_1)를 반환합니다. 6자리 번호는 5분간 유효합니다. 재발송 간격은 30초이며 이메일당 시간당 최대 10회 발송합니다.")
    ApiResponse<Void> sendEmailCode(AuthReqDTO.SendEmailCode request);

    @Operation(summary = "일반 회원가입", description = "이메일로 받은 verificationCode 6자리가 필요합니다. 인증번호는 한 번만 사용할 수 있습니다.")
    ApiResponse<Void> signup(AuthReqDTO.SignupDTO request);
}
