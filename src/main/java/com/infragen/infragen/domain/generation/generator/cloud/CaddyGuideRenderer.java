package com.infragen.infragen.domain.generation.generator.cloud;

import java.util.Optional;

import com.infragen.infragen.domain.generation.dto.response.IaCFileDTO;
import com.infragen.infragen.domain.parsing.dto.response.CaddyComponent;
import com.infragen.infragen.global.enums.ComponentType;

/**
 * CLOUD_DEPLOY 산출물에 Caddy 사용자 안내 파일을 만든다.
 *
 * <p>Caddy가 없으면 파일을 만들지 않아 기존 산출물이 바뀌지 않는다. 안내는 도메인 유무에 따라 달라진다.
 * 도메인이 있으면 DNS와 인증서 볼륨을, 없으면 HTTP만 동작한다는 점과 도메인을 추가하는 방법을 안내한다.
 * 내용은 Caddy 공식 문서(자동 HTTPS 조건, Docker 이미지의 {@code /data} 볼륨과 설정 다시 읽기)에서 확인한 사실만 쓴다.
 */
public class CaddyGuideRenderer {

    /**
     * 그래프에 Caddy가 있으면 안내 파일을 만든다.
     *
     * @param context CLOUD_DEPLOY renderer가 공유하는 파싱 결과
     * @return Caddy가 없으면 빈 값
     */
    public Optional<IaCFileDTO.FileContentResDTO> render(CloudDeployContext context) {
        return context.component(ComponentType.CADDY, CaddyComponent.class)
            .map(caddy -> IaCFileDTO.FileContentResDTO.builder()
                .fileName("CADDY_GUIDE.md")
                .content(content(caddy, context.applicationPort()))
                .build());
    }

    private String content(CaddyComponent caddy, int applicationPort) {
        String preparation = caddy.hasDomain()
            ? domainPreparation(caddy.getDomain(), caddy.getVolumeName())
            : httpOnlyPreparation();

        return """
            # Caddy 웹 서버 안내

            `caddy` 서비스는 앱 앞단에서 요청을 받아 `app:%d`로 전달하는 리버스 프록시입니다. 설정은 `caddy/Caddyfile`에 있고, 컨테이너가 `caddy` 디렉터리를 `/etc/caddy`로 읽습니다.

            %s
            ## 설정 변경

            `caddy/Caddyfile`을 수정한 뒤 아래 명령으로 실행 중인 Caddy가 설정을 다시 읽게 합니다.

            ```
            docker compose -f docker-compose.cloud.yml exec -w /etc/caddy caddy caddy reload
            ```

            ## 알아둘 점

            - HTTP/3에 쓰는 443/udp는 열지 않습니다. TCP 80과 443만 엽니다.
            - 앱 포트 %d도 호스트에 매핑되어 있어서 Caddy를 거치지 않고 접근할 수 있습니다. 앱 접근 CIDR 값으로 허용 범위를 정해 주세요.
            - 경로별 라우팅, 인증, 압축 같은 설정은 생성하지 않습니다. 필요하면 `caddy/Caddyfile`에 직접 추가해 주세요.
            - 이 산출물은 계획 전용입니다. `terraform apply`는 이 프로젝트가 실행하지 않으므로 `CLOUD_DEPLOY_WARNING.md`의 안내를 먼저 확인해 주세요.
            """.formatted(applicationPort, preparation, applicationPort);
    }

    private String domainPreparation(String domain, String volumeName) {
        return """
            ## 배포 전에 준비할 것

            - 도메인 `%s`의 A 레코드(IPv6를 쓰면 AAAA 레코드)가 서버의 공인 IP를 가리키도록 설정해 주세요. DNS가 맞아야 Caddy가 HTTPS 인증서를 자동으로 발급합니다.
            - TCP 80과 443이 외부에서 열려 있어야 합니다. Terraform은 두 포트를 앱 접근 CIDR(`aws_app_cidr` 또는 `oci_app_cidr`)로 허용합니다. 외부에 공개하려면 이 값을 접속을 허용할 범위로 지정해 주세요.
            - 인증서와 키는 볼륨 `%s`의 `/data`에 저장됩니다. 이 볼륨을 지우면 인증서를 다시 발급받아야 하고 발급 한도에 걸릴 수 있으니 유지해 주세요.
            """.formatted(domain, volumeName);
    }

    private String httpOnlyPreparation() {
        return """
            ## 배포 전에 준비할 것

            - 도메인을 지정하지 않아서 HTTP(80)만 동작하고 HTTPS 인증서는 발급되지 않습니다.
            - TCP 80이 외부에서 열려 있어야 합니다. Terraform은 80과 443을 앱 접근 CIDR(`aws_app_cidr` 또는 `oci_app_cidr`)로 허용합니다. 외부에 공개하려면 이 값을 접속을 허용할 범위로 지정해 주세요.
            - HTTPS를 쓰려면 캔버스의 Caddy 노드에 도메인과 인증서를 보관할 볼륨 이름을 입력한 뒤 다시 생성해 주세요.
            """;
    }
}
