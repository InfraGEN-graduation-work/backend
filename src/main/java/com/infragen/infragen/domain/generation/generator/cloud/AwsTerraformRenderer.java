package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

/** AWS 단일 인스턴스 scaffold의 provider-specific Terraform 파일을 생성한다. */
public class AwsTerraformRenderer {

    /**
     * 파싱된 애플리케이션 포트를 AWS security group 변수의 기본값으로 반영한다.
     *
     * @param applicationPort parsing 결과의 애플리케이션 포트
     * @return AWS Terraform 파일 목록
     */
    public List<IaCFileDTO.FileContentResDTO> render(int applicationPort) {
        return List.of(
            file("terraform/aws/main.tf", mainTerraform()),
            file("terraform/aws/variables.tf", variablesTerraform(applicationPort))
        );
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
                aws = {
                  source  = "hashicorp/aws"
                  version = "6.22.0"
                }
              }
            }

            provider "aws" {
              region = var.aws_region
            }

            resource "aws_vpc" "main" {
              cidr_block           = var.aws_vpc_cidr
              enable_dns_hostnames = true
              enable_dns_support   = true
              tags = {
                Name = var.aws_vpc_name
              }
            }

            resource "aws_subnet" "public" {
              vpc_id                  = aws_vpc.main.id
              cidr_block              = var.aws_subnet_cidr
              map_public_ip_on_launch = true
              tags = {
                Name = var.aws_subnet_name
              }
            }

            resource "aws_internet_gateway" "main" {
              vpc_id = aws_vpc.main.id
              tags = {
                Name = var.aws_internet_gateway_name
              }
            }

            resource "aws_route_table" "public" {
              vpc_id = aws_vpc.main.id
              route {
                cidr_block = "0.0.0.0/0"
                gateway_id = aws_internet_gateway.main.id
              }
              tags = {
                Name = var.aws_route_table_name
              }
            }

            resource "aws_route_table_association" "public" {
              subnet_id      = aws_subnet.public.id
              route_table_id = aws_route_table.public.id
            }

            resource "aws_security_group" "instance" {
              name        = var.aws_security_group_name
              description = "InfraGEN 단일 인스턴스 scaffold를 위한 최소 접근 규칙"
              vpc_id      = aws_vpc.main.id

              ingress {
                description = "SSH 관리 접근"
                from_port   = 22
                to_port     = 22
                protocol    = "tcp"
                cidr_blocks = [var.aws_admin_cidr]
              }

              ingress {
                description = "Spring Boot 애플리케이션"
                from_port   = var.app_port
                to_port     = var.app_port
                protocol    = "tcp"
                cidr_blocks = [var.aws_app_cidr]
              }

              egress {
                from_port   = 0
                to_port     = 0
                protocol    = "-1"
                cidr_blocks = ["0.0.0.0/0"]
              }
            }

            resource "aws_instance" "app" {
              ami                         = var.aws_ami_id
              instance_type               = var.aws_instance_type
              subnet_id                   = aws_subnet.public.id
              vpc_security_group_ids      = [aws_security_group.instance.id]
              associate_public_ip_address = true
              user_data                   = <<-EOT
                #!/bin/bash
                set -e
                apt-get update -y
                apt-get install -y docker.io docker-compose-plugin
                systemctl enable --now docker
                EOT

              tags = {
                Name = var.aws_instance_name
              }
            }

            resource "aws_eip" "app" {
              domain = "vpc"
              tags = {
                Name = var.aws_instance_name
              }
            }

            resource "aws_eip_association" "app" {
              instance_id   = aws_instance.app.id
              allocation_id = aws_eip.app.id
            }
            """;
    }

    private String variablesTerraform(int applicationPort) {
        return """
            variable "aws_region" {
              type        = string
              description = "단일 인스턴스 scaffold를 생성할 AWS 리전"
              default     = "ap-northeast-2"
            }

            variable "aws_vpc_name" {
              type        = string
              description = "생성할 VPC의 사용자 지정 이름"
            }

            variable "aws_subnet_name" {
              type        = string
              description = "생성할 public subnet의 사용자 지정 이름"
            }

            variable "aws_internet_gateway_name" {
              type        = string
              description = "생성할 Internet Gateway의 사용자 지정 이름"
            }

            variable "aws_route_table_name" {
              type        = string
              description = "생성할 route table의 사용자 지정 이름"
            }

            variable "aws_security_group_name" {
              type        = string
              description = "생성할 security group의 사용자 지정 이름"
            }

            variable "aws_instance_name" {
              type        = string
              description = "생성할 EC2 인스턴스의 사용자 지정 이름"
            }

            variable "aws_vpc_cidr" {
              type        = string
              description = "생성할 VPC의 CIDR 블록"
              default     = "10.0.0.0/16"
            }

            variable "aws_subnet_cidr" {
              type        = string
              description = "생성할 public subnet의 CIDR 블록"
              default     = "10.0.1.0/24"
            }

            variable "aws_ami_id" {
              type        = string
              description = "선택한 리전에서 사용할 AMI ID"
            }

            variable "aws_instance_type" {
              type        = string
              description = "EC2 인스턴스 타입"
              default     = "t3.micro"
            }

            variable "aws_admin_cidr" {
              type        = string
              description = "SSH 접근을 허용할 CIDR 블록입니다. apply 전에 범위를 제한해 주세요."
            }

            variable "aws_app_cidr" {
              type        = string
              description = "Spring Boot 애플리케이션 접근을 허용할 CIDR 블록"
            }

            variable "app_port" {
              type        = number
              description = "파싱된 그래프의 Spring Boot 애플리케이션 포트"
              default     = %d
            }
            """.formatted(applicationPort);
    }
}
