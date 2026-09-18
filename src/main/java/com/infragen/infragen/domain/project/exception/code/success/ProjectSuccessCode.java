package com.infragen.infragen.domain.project.exception.code.success;

import com.infragen.infragen.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
@Getter
@AllArgsConstructor
public enum ProjectSuccessCode implements BaseSuccessCode {
    PROJECT_CREATE_SUCCESS(
        HttpStatus.CREATED,
        "프로젝트 생성에 성공했습니다.",
        "PROJECT201_1"
    ),
    PROJECT_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 목록 조회에 성공했습니다.",
        "PROJECT200_1"
    ),
    PROJECT_CANVAS_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 캔버스 조회에 성공했습니다.",
        "PROJECT200_2"
    ),
    PROJECT_CANVAS_SAVE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 캔버스 저장에 성공했습니다.",
        "PROJECT200_3"
    ),
    PROJECT_DELETE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 삭제에 성공했습니다.",
        "PROJECT200_4"
    ),
    PROJECT_COLLABORATOR_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 collaborator 조회에 성공했습니다.",
        "PROJECT200_5"
    ),
    PROJECT_COLLABORATOR_ROLE_UPDATE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 collaborator 역할 변경에 성공했습니다.",
        "PROJECT200_6"
    ),
    PROJECT_COLLABORATOR_DELETE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 collaborator 삭제에 성공했습니다.",
        "PROJECT200_7"
    ),
    PROJECT_METADATA_UPDATE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 이름과 설명 수정에 성공했습니다.",
        "PROJECT200_8"
    ),
    PROJECT_COLLABORATOR_INVITATION_SENT_LIST_GET_SUCCESS(
        HttpStatus.OK,
        "프로젝트 발신 초대 목록 조회에 성공했습니다.",
        "PROJECT200_9"
    ),
    PROJECT_COLLABORATOR_INVITATION_RECEIVED_LIST_GET_SUCCESS(
        HttpStatus.OK,
        "받은 프로젝트 초대 목록 조회에 성공했습니다.",
        "PROJECT200_10"
    ),
    PROJECT_COLLABORATOR_INVITATION_SEND_SUCCESS(
        HttpStatus.CREATED,
        "프로젝트 초대 발신에 성공했습니다.",
        "PROJECT201_3"
    ),
    PROJECT_COLLABORATOR_INVITATION_ACCEPT_SUCCESS(
        HttpStatus.OK,
        "프로젝트 초대 수락에 성공했습니다.",
        "PROJECT200_11"
    ),
    PROJECT_COLLABORATOR_INVITATION_DECLINE_SUCCESS(
        HttpStatus.OK,
        "프로젝트 초대 거절에 성공했습니다.",
        "PROJECT200_12"
    ),
    ;
    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
