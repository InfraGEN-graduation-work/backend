package com.infragen.infragen.global.apiPayload.handler;

import com.infragen.infragen.domain.member.exception.MemberException;
import com.infragen.infragen.domain.member.exception.code.error.MemberErrorCode;
import com.infragen.infragen.global.apiPayload.code.GeneralErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class GeneralExceptionAdviceTest {

    private final GeneralExceptionAdvice advice = new GeneralExceptionAdvice();

    @Test
    @DisplayName("GeneralException은 도메인 에러 코드를 그대로 반환한다")
    void handleGeneralException_returnsDomainErrorCode() {
        var response = advice.handleGeneralException(new MemberException(MemberErrorCode.DUPLICATE_EMAIL));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(MemberErrorCode.DUPLICATE_EMAIL.getCode(), response.getBody().getCode());
        assertEquals(MemberErrorCode.DUPLICATE_EMAIL.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("실제 MySQL 이메일 중복 로그는 COMMON409_1로 매핑된다")
    void handleDataIntegrityViolation_duplicateEmailFromActualLog_returnsConflict() {
        SQLException sqlException = new SQLException(
                "Duplicate entry 'test@test.com' for key 'member.UKmbmcqelty0fbrvxp1q58dn57t'",
                "23000",
                1062
        );
        var exception = new DataIntegrityViolationException(
                "could not execute statement [insert into member (created_at,email,is_active,nickname,password,role,social_id,social_provider,updated_at) values (?,?,?,?,?,?,?,?,?)]",
                sqlException
        );

        var response = advice.handleDataIntegrityViolation(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GeneralErrorCode.CONFLICT.getCode(), response.getBody().getCode());
        assertEquals(GeneralErrorCode.CONFLICT.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("데드락은 COMMON409_2로 매핑된다")
    void handlePessimisticLockingFailure_deadlock_returnsConcurrentModification() {
        var exception = new CannotAcquireLockException(
                "could not execute batch [Deadlock found when trying to get lock; try restarting transaction]"
        );

        var response = advice.handlePessimisticLockingFailure(exception);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GeneralErrorCode.CONCURRENT_MODIFICATION.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("FK 위반은 COMMON400_1로 매핑된다")
    void handleDataIntegrityViolation_foreignKeyViolation_returnsBadRequest() {
        SQLException sqlException = new SQLException(
                "Cannot add or update a child row: a foreign key constraint fails",
                "23000",
                1452
        );
        var exception = new DataIntegrityViolationException("could not execute statement", sqlException);

        var response = advice.handleDataIntegrityViolation(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(GeneralErrorCode.BAD_REQUEST.getCode(), response.getBody().getCode());
    }
}
