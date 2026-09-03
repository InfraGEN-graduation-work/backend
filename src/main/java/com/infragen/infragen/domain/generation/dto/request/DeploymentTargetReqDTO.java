package com.infragen.infragen.domain.generation.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.validation.constraints.NotBlank;

import com.infragen.infragen.domain.generation.enums.DeploymentOption;

/** 배포 옵션으로 선택된 provider의 typed 배포 설정을 제공한다. */
public final class DeploymentTargetReqDTO {

    private DeploymentTargetReqDTO() {
    }

    /** deploymentOption을 외부 discriminator로 사용해 provider별 target을 역직렬화한다. */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "deploymentOption"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = AwsDeploymentTarget.class, name = "AWS"),
        @JsonSubTypes.Type(value = OciDeploymentTarget.class, name = "OCI")
    })
    public sealed interface Target permits AwsDeploymentTarget, OciDeploymentTarget {

        /** JSON discriminator와 동일한 provider key를 반환한다. */
        DeploymentOption provider();
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AwsDeploymentTarget(
        String region,

        @NotBlank(message = "AWS VPC 이름은 필수입니다.")
        String vpcName,

        @NotBlank(message = "AWS subnet 이름은 필수입니다.")
        String subnetName,

        @NotBlank(message = "AWS Internet Gateway 이름은 필수입니다.")
        String internetGatewayName,

        @NotBlank(message = "AWS route table 이름은 필수입니다.")
        String routeTableName,

        @NotBlank(message = "AWS security group 이름은 필수입니다.")
        String securityGroupName,

        @NotBlank(message = "AWS instance 이름은 필수입니다.")
        String instanceName,

        String vpcCidr,
        String subnetCidr,

        @NotBlank(message = "AWS AMI ID는 필수입니다.")
        String amiId,

        String instanceType,

        @NotBlank(message = "AWS 관리자 접근 CIDR은 필수입니다.")
        String adminCidr,

        @NotBlank(message = "AWS 애플리케이션 접근 CIDR은 필수입니다.")
        String appCidr
    ) implements Target {

        @Override
        public DeploymentOption provider() {
            return DeploymentOption.AWS;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record OciDeploymentTarget(
        String region,

        @NotBlank(message = "OCI VCN 이름은 필수입니다.")
        String vcnName,

        @NotBlank(message = "OCI subnet 이름은 필수입니다.")
        String subnetName,

        @NotBlank(message = "OCI Internet Gateway 이름은 필수입니다.")
        String internetGatewayName,

        @NotBlank(message = "OCI route table 이름은 필수입니다.")
        String routeTableName,

        @NotBlank(message = "OCI security list 이름은 필수입니다.")
        String securityListName,

        @NotBlank(message = "OCI instance 이름은 필수입니다.")
        String instanceName,

        @NotBlank(message = "OCI hostname label은 필수입니다.")
        String hostnameLabel,

        @NotBlank(message = "OCI compartment OCID는 필수입니다.")
        String compartmentId,

        @NotBlank(message = "OCI availability domain은 필수입니다.")
        String availabilityDomain,

        @NotBlank(message = "OCI image OCID는 필수입니다.")
        String imageId,

        String shape,
        String vcnCidr,
        String subnetCidr,

        @NotBlank(message = "OCI 관리자 접근 CIDR은 필수입니다.")
        String adminCidr,

        @NotBlank(message = "OCI 애플리케이션 접근 CIDR은 필수입니다.")
        String appCidr,

        @NotBlank(message = "OCI SSH 공개 키는 필수입니다.")
        String sshAuthorizedKeys
    ) implements Target {

        @Override
        public DeploymentOption provider() {
            return DeploymentOption.OCI;
        }
    }
}
