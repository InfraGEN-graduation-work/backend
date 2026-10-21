package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.List;

import com.infragen.infragen.domain.generation.dto.request.DeploymentTargetReqDTO;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.enums.DeploymentOption;

/** 하나의 cloud provider Terraform 산출물을 생성하는 계약이다. */
public interface CloudTerraformRenderer {

    /** @return 이 renderer가 지원하는 cloud provider */
    DeploymentOption getProvider();

    /**
     * provider별 Terraform 파일을 생성한다.
     *
     * @param context 파싱된 runtime graph 실행 정보
     * @param deploymentTarget provider별 배포 대상 입력
     * @param publicPorts 앱 포트 외에 호스트에 공개할 TCP 포트. ingress로 연다.
     * @param applicationPortIngress 앱 포트(또는 NGINX 포트) ingress와 {@code app_port} 변수를 만들지 여부. 앞단 웹 서버가
     *                               앱 포트를 닫는 경우 {@code false}라서 ingress와 변수를 만들지 않는다.
     * @return provider Terraform 파일 목록
     */
    List<IaCFileDTO.FileContentResDTO> render(
        CloudDeployContext context,
        DeploymentTargetReqDTO.Target deploymentTarget,
        List<Integer> publicPorts,
        boolean applicationPortIngress
    );
}
