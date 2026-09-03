package com.infragen.infragen.domain.generation.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

@DisplayName("DeploymentTargetValidator")
class DeploymentTargetValidatorTest {
    private final Validator beanValidator = Validation.buildDefaultValidatorFactory().getValidator();
    private final DeploymentTargetValidator deploymentTargetValidator =
        new DeploymentTargetValidator(beanValidator);

    @Test
    @DisplayName("AWS target 필수값 누락 — generation error code 반환")
    void validate_AwsTargetMissingRequiredValue_ThrowsInvalidDeploymentTarget() {
        // given
        DeploymentTargetReqDTO.Target target = new DeploymentTargetReqDTO.AwsDeploymentTarget(
            "ap-northeast-2",
            "",
            "infragen-subnet",
            "infragen-igw",
            "infragen-public-route",
            "infragen-sg",
            "infragen-app",
            "10.0.0.0/16",
            "10.0.1.0/24",
            "ami-xxxxxxxx",
            "t3.micro",
            "203.0.113.10/32",
            "0.0.0.0/0"
        );

        // when
        IaCGenerationException exception = assertThrows(
            IaCGenerationException.class,
            () -> deploymentTargetValidator.validate(target)
        );

        // then
        assertEquals(IaCGenerationErrorCode.INVALID_DEPLOYMENT_TARGET, exception.getCode());
    }

    @Test
    @DisplayName("유효한 AWS target — 검증 통과")
    void validate_ValidAwsTarget_DoesNotThrow() {
        // given
        DeploymentTargetReqDTO.Target target = new DeploymentTargetReqDTO.AwsDeploymentTarget(
            "ap-northeast-2",
            "infragen-vpc",
            "infragen-subnet",
            "infragen-igw",
            "infragen-public-route",
            "infragen-sg",
            "infragen-app",
            "10.0.0.0/16",
            "10.0.1.0/24",
            "ami-xxxxxxxx",
            "t3.micro",
            "203.0.113.10/32",
            "0.0.0.0/0"
        );

        // when
        assertDoesNotThrow(() -> deploymentTargetValidator.validate(target));

        // then
    }
}
