package com.infragen.infragen.domain.generation.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;
import com.infragen.infragen.domain.parsing.dto.request.EdgeDTO;
import com.infragen.infragen.domain.parsing.dto.request.NodeDTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public final class GenerateReqDTO {

    private GenerateReqDTO() {
    }

    public record Request(
        List<NodeDTO> nodes,
        List<EdgeDTO> edges,
        @NotNull(message = "배포 옵션은 필수입니다.")
        DeploymentOption deploymentOption,
        Boolean includeLocalSpec,
        @Valid
        @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "deploymentOption"
        )
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {

        @AssertTrue(message = "배포 옵션과 배포 대상 설정이 일치해야 합니다.")
        public boolean hasValidDeploymentTarget() {
            if (deploymentOption == null) {
                return true;
            }
            if (deploymentOption == DeploymentOption.LOCAL) {
                return Boolean.FALSE.equals(includeLocalSpec()) && deploymentTarget == null;
            }
            return deploymentTarget != null && deploymentTarget.provider() == deploymentOption;
        }
    }
}
