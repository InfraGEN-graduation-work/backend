# Caddy 웹 서버 지원 정책

Caddy 노드를 지원하면서 정한 정책과 그 이유를 기록한다. 구현 위치와 클래스 구조는 [architecture_convention.md](../harness/project_code_convention/architecture_convention.md)를, 제품 범위는 [infra-gen-project-overview.md](../infra-gen-project-overview.md)를 따른다. 사용자에게 전달할 사용 안내는 이 문서의 범위가 아니다. 그래프 연결, 배포 옵션, 입력 검증의 공통 정책은 [graph-connection-policy.md](graph-connection-policy.md), [generation-output-policy.md](generation-output-policy.md), [input-validation-policy.md](input-validation-policy.md)를 따른다.

## 지원 범위

- Caddy는 앱 앞단에서 앱 하나를 프록시하는 리버스 프록시다.
- CLOUD_DEPLOY 산출물에서만 지원한다. 도메인이 있을 때 HTTPS 인증서를 자동 발급하는 것이 Caddy를 쓰는 핵심 이유이고, 로컬에서 웹 서버를 띄울 이유는 약하기 때문이다.
- Local 단독 생성(`deploymentOption`이 `LOCAL`)에 웹 서버 노드가 있으면 `GENERATION400_15`로 거부한다. 거부하지 않으면 사용자가 배치한 노드가 에러 없이 산출물에서 빠진다.
- 클라우드 요청에서 `includeLocalSpec`을 켜면 거부하지 않는다. 웹 서버는 클라우드 산출물에만 들어가고 Local compose에서는 빠진다. 클라우드와 로컬을 함께 요청한 사용자가 막히지 않게 하려는 결정이다.
- 앱은 하나만 지원한다. 앱이 여럿이면 어느 앱을 프록시할지 정할 수 없고, CLOUD_DEPLOY가 이미 앱 하나만 허용한다.

## 노드 속성

- `imageVersion`: 필수. Compose의 `image:`에 그대로 쓴다.
- `containerName`: 선택. 파싱만 하고 CLOUD Compose에는 쓰지 않는다. 다른 CLOUD service와 같게 service 이름은 `caddy`로 고정한다.
- `domain`: 선택. 비어 있으면 HTTP(80)만 서비스하고, 있으면 Caddy가 HTTPS 인증서를 자동 발급한다. `domain`과 `volumeName`은 컴포넌트를 만들 때 앞뒤 공백을 제거해 이후 코드가 다시 정리하지 않는다.
- `volumeName`: 도메인이 있으면 필수다. 인증서는 `/data`에 저장되는데, 컨테이너를 다시 만들 때마다 재발급하면 발급 한도에 걸릴 수 있어서 영속 볼륨이 필요하다.
- 호스트 포트는 입력받지 않고 80/443으로 고정한다. 그래서 파서의 `requiresPort()`가 false이고 포트 범위(1024 이상)와 중복 검사를 거치지 않는다.
- 비밀번호나 basic auth 같은 비밀값 속성은 받지 않는다. `.env`에 새 비밀값이 생기지 않아 `EnvValueValidator`를 쓰지 않는다.

## 입력 검증

- 이미지 버전 누락은 `PARSING400_37`로 거부한다.
- 도메인은 점을 포함한 호스트명만 허용하고 위반하면 `PARSING400_38`로 거부한다. 도메인이 Caddyfile의 사이트 주소로 그대로 들어가므로 공백, 중괄호, 줄바꿈처럼 설정을 바꿀 수 있는 문자를 막아야 한다. 마지막 라벨이 영문자로 시작해야 하므로 IP와 `localhost`도 거부하고, 한글 도메인은 퓨니코드로 입력해야 한다.
- 도메인이 있는데 볼륨 이름이 없으면 `PARSING400_39`으로 거부한다.

## 그래프 규칙

- 연결 방향은 앱에서 웹 서버다. 화살표는 먼저 뜨는 쪽에서 나중에 뜨는 쪽을 가리키므로 요청이 흐르는 방향과 반대다. 반대 방향은 기존 우선순위 검사가 `PARSING400_10`으로 거부한다.
- 웹 서버 노드는 그래프에 하나만 둘 수 있고 위반하면 `PARSING400_40`이다.
- 웹 서버에는 앱에서 오는 연결선 하나만 있어야 한다. 연결이 없거나, 앱이 아닌 노드에서 오거나, 앱이 둘 이상 연결되면 `PARSING400_41`이다. 같은 연결선이 중복돼도 하나로 센다.
- 이 규칙은 `WEB_SERVER` 카테고리 기준이라 NGINX 등 다른 웹 서버에도 똑같이 적용된다. 기존 DB·캐시 규칙이 카테고리를 기준으로 하는 것과 같은 방식이고, 다른 규칙이 필요하면 해당 웹 서버 담당자가 조정한다.

## 생성 산출물

- Compose service: 이름은 `caddy`, 포트는 80과 443, Caddyfile은 `./caddy:/etc/caddy`로 디렉터리째 마운트한다. 편집기가 파일을 교체하면 컨테이너가 단일 파일 마운트의 변경을 못 읽을 수 있어서다. 볼륨이 있으면 `<volumeName>:/data`를 마운트하고 루트 `volumes:`에도 선언한다. service는 `app`에 의존한다(`depends_on`).
- Caddyfile(`cloud/caddy/Caddyfile`): 도메인이 있으면 `<domain> { reverse_proxy app:<앱 포트> }`, 없으면 `:80 { reverse_proxy app:<앱 포트> }`다. 프록시 대상 `app`은 CLOUD Compose의 앱 service 이름이며, 앱이 하나이고 이름이 고정이라 일치한다.
- Terraform: AWS와 OCI 모두 TCP 80과 443 ingress를 추가한다. 출발 CIDR은 새 변수나 전체 개방 기본값을 만들지 않고 기존 앱 CIDR 변수(`aws_app_cidr`, `oci_app_cidr`)를 재사용한다. 공개 포트는 Compose service가 알려 주는 값(`publicPorts`)을 쓰므로 Compose가 공개하는 포트와 Terraform이 여는 포트가 어긋나지 않는다.
- 사용자 안내(`cloud/CADDY_GUIDE.md`): 웹 서버가 있을 때만 별도 파일로 추가한다. 기존 `CLOUD_DEPLOY_WARNING.md`를 그대로 두어 Caddy가 없는 산출물을 바꾸지 않기 위해서다. 도메인이 있으면 DNS, 80/443 허용 범위(앱 CIDR), 인증서 볼륨, 설정 다시 읽기 명령을 도메인과 볼륨 이름을 넣어 안내하고, 없으면 HTTP만 동작함과 도메인을 추가하는 방법을 안내한다. Caddy 공식 문서에서 확인한 사실만 쓴다.
- 웹 서버가 없으면 산출물이 기존과 같다.

## 제외 범위와 알려진 한계

- HTTP/3(443/udp)은 열지 않는다. 경로별 라우팅, basic auth, 압축, 접근 로그 같은 Caddyfile 세부 기능도 지원하지 않는다.
- 80/443은 앱 CIDR이 허용한 범위에만 열린다. 외부에 공개하려면 사용자가 해당 CIDR을 지정해야 한다.
- 앱 service의 포트 매핑은 Caddy가 있어도 그대로라서 앱 포트가 호스트에 계속 공개된다.
- `imageVersion`과 `volumeName`은 다른 컴포넌트와 같게 별도 검증 없이 Compose YAML에 들어간다.
- 생성기는 Caddy가 앱에 연결됐는지 다시 확인하지 않는다. 연결 검증은 parsing 단계의 책임이며, 그래프가 이 단계를 통과해야 한다는 전제를 둔다.
- 산출물은 plan-only scaffold다. 실제 Caddy 컨테이너 기동과 Caddyfile 문법 검증, `terraform apply`는 수행하지 않는다. Terraform 산출물은 `terraform validate`까지만 확인한다.
- 프론트에서 Local 단독을 선택한 채 웹 서버 노드를 배치하면 생성 시점에야 거부된다.

## 정책을 바꿀 때 확인할 곳

- 지원 출력 경로를 바꾸면 `GenerationCommandService`의 Local 단독 거부와 `IaCGenerationErrorCode`의 `UNSUPPORTED_LOCAL_COMPONENT`를 함께 본다.
- 노드 속성과 검증을 바꾸면 `CaddyParser`, `CaddyComponent`, `ParsingErrorCode`를 함께 본다.
- 그래프 규칙을 바꾸면 `ValidateGraphStructure`의 `validateWebServerPlacement`를 본다.
- 산출물을 바꾸면 `CaddyCloudComposeServiceRenderer`(Compose service, 공개 포트, 추가 파일 목록), `CaddyfileRenderer`, `CaddyGuideRenderer`, `AwsTerraformRenderer`, `OciTerraformRenderer`를 함께 본다.
