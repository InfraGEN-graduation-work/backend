package com.infragen.infragen.domain.generation.validator;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

/** provider target DTO에 선언된 Bean Validation 규칙을 실행한다. */
@Component
@RequiredArgsConstructor
public class DeploymentTargetValidator {
    private final Validator validator;

    /**
     * typed deployment target의 provider별 필수값을 검증한다.
     *
     * @param target 검증할 provider target
     * @throws IaCGenerationException target이 없거나 field constraint를 위반한 경우
     */
    public void validate(DeploymentTargetReqDTO.Target target) {
        if (target == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.MISSING_DEPLOYMENT_TARGET);
        }

        Set<ConstraintViolation<DeploymentTargetReqDTO.Target>> violations =
            validator.validate(target);
        if (!violations.isEmpty()) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_DEPLOYMENT_TARGET);
        }
    }
}
