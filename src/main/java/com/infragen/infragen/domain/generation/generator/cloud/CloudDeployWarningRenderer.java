package com.infragen.infragen.domain.generation.generator.cloud;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;

/** CLOUD_DEPLOY 산출물의 plan-only 실행 경계를 문서화한다. */
public class CloudDeployWarningRenderer {

    /**
     * provider 입력과 외부 runtime 파일이 필요한 비실행 scaffold 경고를 생성한다.
     *
     * @return CLOUD_DEPLOY 경고 문서
     */
    public IaCFileDTO.FileContentResDTO render() {
        String content = """
            # CLOUD_DEPLOY 계획 전용 scaffold 안내

            - `apply_ready=false`
            - 이 산출물은 사용자가 cloud 입력, 외부 `.env`, `app.jar`, provider 자격증명을
              별도로 제공하기 전까지 실행할 수 없습니다.
            - provider별 plan 전에 `terraform init -backend=false`, `terraform fmt`,
              `terraform validate`를 실행해 주세요.
            - DB 비밀번호, JWT secret, runtime 자격증명 또는 이를 포함한 tfvars를 커밋하지 마세요.
            - 이 프로젝트는 `terraform apply`나 cloud account 조회를 실행하지 않습니다.
            """;

        return IaCFileDTO.FileContentResDTO.builder()
            .fileName("CLOUD_DEPLOY_WARNING.md")
            .content(content)
            .build();
    }
}
