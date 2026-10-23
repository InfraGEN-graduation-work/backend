package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import org.springframework.stereotype.Component;
import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.generation.generator.nginx.NginxConfigRenderer;
import com.infragen.infragen.domain.parsing.dto.response.NginxComponent;
import com.infragen.infragen.global.enums.ComponentType;

@Component
public class NginxCloudComposeServiceRenderer implements CloudComposeServiceRenderer {
    @Override
    public ComponentType getSupportedType() {
        return ComponentType.NGINX;
    }

    @Override
    public String getServiceName() {
        return "nginx";
    }

    @Override
    public boolean isEnabled(CloudDeployContext context) {
        return context.nginx().isPresent();
    }

    @Override
    public boolean isDependency(CloudDeployContext context) {
        return false;
    }

    @Override
    public SequencedMap<String, String> applicationEnvironment() {
        return new LinkedHashMap<>();
    }

    @Override
    public String render(CloudDeployContext context) {
        NginxComponent nginx = context.nginx().orElseThrow();
        return """

              nginx:
                image: "%s"
                container_name: "%s"
                ports:
                  - "%d:80"
                depends_on:
                  - app
                volumes:
                  - ./nginx/default.conf:/etc/nginx/conf.d/default.conf:ro
            """.formatted(nginx.getImageVersion(), nginx.getContainerName(), nginx.getPort());
    }

    @Override
    public List<IaCFileDTO.FileContentResDTO> additionalFiles(CloudDeployContext context) {
        return List.of(NginxConfigRenderer.render("app", context.applicationPort()));
    }

    /**
     * @return 공개 포트와 Terraform {@code app_port}, 앱 포트 비공개, 설정 파일 변경 방법을 안내하는 섹션
     */
    @Override
    public Optional<String> guideSection(CloudDeployContext context) {
        int nginxPort = context.nginx().orElseThrow().getPort();
        int applicationPort = context.applicationPort();
        // 포트와 경로는 render와 NginxConfigRenderer가 쓰는 값과 같아야 한다. NginxGenerationContractTest가 함께 확인한다.
        return Optional.of("""
            ## NGINX 웹 서버

            `nginx` 서비스는 호스트 포트(`%d`)로 요청을 받아 `app:%d` 주소로 전달하는 리버스 프록시입니다. 설정은 `%s`에 있고, 컨테이너가 읽기 전용으로 마운트해 읽습니다.

            ### 배포 전에 준비할 것

            - Terraform의 `app_port` 변수에는 앱 포트가 아니라 NGINX 포트(`%d`)가 들어갑니다. 이 포트는 앱 접근 CIDR(`aws_app_cidr` 또는 `oci_app_cidr`)로 허용되므로, 외부에 공개하려면 이 값을 접속을 허용할 범위로 지정해 주세요.

            ### 설정 변경

            `%s` 파일을 수정한 뒤 아래 명령으로 NGINX 컨테이너를 다시 시작해 설정을 읽게 합니다.

            ```
            docker compose -f docker-compose.cloud.yml restart nginx
            ```

            ### 알아둘 점

            - 앱 포트(`%d`)는 호스트에 공개하지 않습니다. NGINX만 외부에서 접근할 수 있습니다.
            - 생성된 설정은 HTTP(컨테이너 포트 80)만 처리합니다. HTTPS, 경로별 라우팅 같은 설정은 생성하지 않으므로 필요하면 `%s`에 직접 추가해 주세요.
            """.formatted(nginxPort, applicationPort, NginxConfigRenderer.FILE_NAME, nginxPort,
            NginxConfigRenderer.FILE_NAME, applicationPort, NginxConfigRenderer.FILE_NAME));
    }
}
