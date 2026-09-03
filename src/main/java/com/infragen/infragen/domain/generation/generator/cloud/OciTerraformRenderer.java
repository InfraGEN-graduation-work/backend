package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;

import org.springframework.stereotype.Component;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.exception.IaCGenerationException;
import com.infragen.infragen.domain.generation.exception.code.error.IaCGenerationErrorCode;

/** OCI 단일 인스턴스 scaffold의 provider-specific Terraform 파일을 생성한다. */
@Component
public class OciTerraformRenderer implements CloudTerraformRenderer {

    @Override
    public Provider getProvider() {
        return Provider.OCI;
    }

    @Override
    public List<IaCFileDTO.FileContentResDTO> render(
        CloudDeployContext context,
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        DeploymentTargetReqDTO.OciDeploymentTarget target = requireTarget(deploymentTarget);
        return List.of(
            file("terraform/oci/main.tf", mainTerraform()),
            file("terraform/oci/variables.tf", variablesTerraform(context.applicationPort())),
            file("terraform/oci/terraform.tfvars.example", tfvarsExample(
                context.applicationPort(), target))
        );
    }

    private DeploymentTargetReqDTO.OciDeploymentTarget requireTarget(
        DeploymentTargetReqDTO.Target deploymentTarget
    ) {
        if (!(deploymentTarget instanceof DeploymentTargetReqDTO.OciDeploymentTarget target)) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return target;
    }

    private IaCFileDTO.FileContentResDTO file(String fileName, String content) {
        return IaCFileDTO.FileContentResDTO.builder()
            .fileName(fileName)
            .content(content)
            .build();
    }

    private String mainTerraform() {
        return """
            # InfraGEN CLOUD_DEPLOY 계획 전용 scaffold입니다. apply_ready=false 상태입니다.
            terraform {
              required_version = ">= 1.13.5, < 2.0.0"

              required_providers {
                oci = {
                  source  = "oracle/oci"
                  version = "8.8.0"
                }
              }
            }

            provider "oci" {
              region = var.oci_region
            }

            resource "oci_core_vcn" "main" {
              compartment_id = var.oci_compartment_id
              cidr_blocks    = [var.oci_vcn_cidr]
              display_name   = var.oci_vcn_name
            }

            resource "oci_core_internet_gateway" "main" {
              compartment_id = var.oci_compartment_id
              vcn_id         = oci_core_vcn.main.id
              enabled        = true
              display_name   = var.oci_internet_gateway_name
            }

            resource "oci_core_route_table" "public" {
              compartment_id = var.oci_compartment_id
              vcn_id         = oci_core_vcn.main.id
              display_name   = var.oci_route_table_name

              route_rules {
                destination       = "0.0.0.0/0"
                destination_type  = "CIDR_BLOCK"
                network_entity_id = oci_core_internet_gateway.main.id
              }
            }

            resource "oci_core_security_list" "instance" {
              compartment_id = var.oci_compartment_id
              vcn_id         = oci_core_vcn.main.id
              display_name   = var.oci_security_list_name

              egress_security_rules {
                protocol    = "all"
                destination = "0.0.0.0/0"
              }

              ingress_security_rules {
                protocol = "6"
                source   = var.oci_admin_cidr
                tcp_options {
                  min = 22
                  max = 22
                }
              }

              ingress_security_rules {
                protocol = "6"
                source   = var.oci_app_cidr
                tcp_options {
                  min = var.app_port
                  max = var.app_port
                }
              }
            }

            resource "oci_core_subnet" "public" {
              compartment_id             = var.oci_compartment_id
              vcn_id                     = oci_core_vcn.main.id
              cidr_block                 = var.oci_subnet_cidr
              route_table_id             = oci_core_route_table.public.id
              security_list_ids          = [oci_core_security_list.instance.id]
              prohibit_public_ip_on_vnic = false
              display_name               = var.oci_subnet_name
            }

            resource "oci_core_instance" "app" {
              availability_domain = var.oci_availability_domain
              compartment_id      = var.oci_compartment_id
              shape               = var.oci_shape

              source_details {
                source_id   = var.oci_image_id
                source_type = "image"
              }

              create_vnic_details {
                subnet_id        = oci_core_subnet.public.id
                assign_public_ip = true
                hostname_label   = var.oci_hostname_label
              }

              metadata = {
                ssh_authorized_keys = var.oci_ssh_authorized_keys
              }

              display_name = var.oci_instance_name
            }
            """;
    }

    private String variablesTerraform(int applicationPort) {
        return """
            variable "oci_region" {
              type        = string
              description = "단일 인스턴스 scaffold를 생성할 OCI 리전"
              default     = "ap-seoul-1"
            }

            variable "oci_vcn_name" {
              type        = string
              description = "생성할 VCN의 사용자 지정 이름"
            }

            variable "oci_subnet_name" {
              type        = string
              description = "생성할 public subnet의 사용자 지정 이름"
            }

            variable "oci_internet_gateway_name" {
              type        = string
              description = "생성할 Internet Gateway의 사용자 지정 이름"
            }

            variable "oci_route_table_name" {
              type        = string
              description = "생성할 route table의 사용자 지정 이름"
            }

            variable "oci_security_list_name" {
              type        = string
              description = "생성할 security list의 사용자 지정 이름"
            }

            variable "oci_instance_name" {
              type        = string
              description = "생성할 Compute Instance의 사용자 지정 이름"
            }

            variable "oci_hostname_label" {
              type        = string
              description = "Compute Instance에 사용할 DNS hostname label"
            }

            variable "oci_compartment_id" {
              type        = string
              description = "사용자가 제공하는 OCI compartment OCID"
            }

            variable "oci_availability_domain" {
              type        = string
              description = "사용자가 제공하는 OCI availability domain"
            }

            variable "oci_image_id" {
              type        = string
              description = "사용자가 제공하는 OCI image OCID"
            }

            variable "oci_shape" {
              type        = string
              description = "OCI Compute 인스턴스 shape"
              default     = "VM.Standard.E2.1.Micro"
            }

            variable "oci_vcn_cidr" {
              type        = string
              description = "생성할 VCN의 CIDR 블록"
              default     = "10.0.0.0/16"
            }

            variable "oci_subnet_cidr" {
              type        = string
              description = "생성할 public subnet의 CIDR 블록"
              default     = "10.0.1.0/24"
            }

            variable "oci_admin_cidr" {
              type        = string
              description = "SSH 접근을 허용할 CIDR 블록입니다. apply 전에 범위를 제한해 주세요."
            }

            variable "oci_app_cidr" {
              type        = string
              description = "Spring Boot 애플리케이션 접근을 허용할 CIDR 블록"
            }

            variable "oci_ssh_authorized_keys" {
              type        = string
              description = "사용자가 제공하는 SSH 공개 키"
            }

            variable "app_port" {
              type        = number
              description = "파싱된 그래프의 Spring Boot 애플리케이션 포트"
              default     = %d
            }
            """.formatted(applicationPort);
    }

    private String tfvarsExample(
        int applicationPort,
        DeploymentTargetReqDTO.OciDeploymentTarget target
    ) {
        return """
            # InfraGEN이 생성한 OCI target 예시입니다. 실제 credential은 포함하지 않습니다.
            oci_region = %s
            oci_vcn_name = %s
            oci_subnet_name = %s
            oci_internet_gateway_name = %s
            oci_route_table_name = %s
            oci_security_list_name = %s
            oci_instance_name = %s
            oci_hostname_label = %s
            oci_compartment_id = %s
            oci_availability_domain = %s
            oci_image_id = %s
            oci_shape = %s
            oci_vcn_cidr = %s
            oci_subnet_cidr = %s
            oci_admin_cidr = %s
            oci_app_cidr = %s
            oci_ssh_authorized_keys = %s
            app_port = %d
            """.formatted(
                hcl(valueOrDefault(target.region(), "ap-seoul-1")),
                hcl(target.vcnName()),
                hcl(target.subnetName()),
                hcl(target.internetGatewayName()),
                hcl(target.routeTableName()),
                hcl(target.securityListName()),
                hcl(target.instanceName()),
                hcl(target.hostnameLabel()),
                hcl(target.compartmentId()),
                hcl(target.availabilityDomain()),
                hcl(target.imageId()),
                hcl(valueOrDefault(target.shape(), "VM.Standard.E2.1.Micro")),
                hcl(valueOrDefault(target.vcnCidr(), "10.0.0.0/16")),
                hcl(valueOrDefault(target.subnetCidr(), "10.0.1.0/24")),
                hcl(target.adminCidr()),
                hcl(target.appCidr()),
                hcl(target.sshAuthorizedKeys()),
                applicationPort
            );
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String hcl(String value) {
        if (value == null) {
            throw new IaCGenerationException(IaCGenerationErrorCode.INVALID_COMPONENT_STATE);
        }
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            + "\"";
    }
}
