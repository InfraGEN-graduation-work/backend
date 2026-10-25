# 배포 옵션과 산출물 정책

Generate 요청의 배포 옵션 조합과 옵션별 산출물 구성, 생성 파일의 경계를 기록한다. 요청 검증은 `GenerationCommandService`와 `DeploymentTargetValidator`가 맡고, 파일 생성은 `DockerComposeIaCGenerator`(LOCAL_DEV)와 `TerraformIaCGenerator`(CLOUD_DEPLOY)가 맡는다.

## 배포 옵션 조합

- `deploymentOption`은 `LOCAL`, `AWS`, `OCI` 중 하나이며 필수다(`GENERATION400_4`).
- `LOCAL`은 `includeLocalSpec`을 명시적으로 `false`로 보내야 하고 `deploymentTarget`은 없어야 한다. 어긋나면 `GENERATION400_6`이다.
- `AWS`와 `OCI`는 해당 provider의 `deploymentTarget`이 필수이며(`GENERATION400_5`), provider가 배포 옵션과 같아야 한다(`GENERATION400_7`). provider별 필수값(리소스 이름, AMI 또는 이미지 ID, 관리자 접근 CIDR, 앱 접근 CIDR 등)이 비어 있으면 `GENERATION400_8`이다.
- 클라우드 요청에서 `includeLocalSpec`이 `true`이면 클라우드 산출물에 LOCAL_DEV 산출물을 이어 붙여 한 번에 반환한다. 이때 Local Compose renderer가 없는 노드(Caddy)는 로컬 쪽에서만 빠진다. 반대로 Local 단독 요청에 그런 노드가 있으면 거부한다(`GENERATION400_15`). Local renderer가 있는 NGINX는 거부하지 않는다.
- 생성 결과는 요청 한 번당 하나의 생성 이력으로 저장한다.

## Compose 이름 값의 출력

- 데이터베이스, 캐시, Caddy service의 사용자 입력 이름 값은 큰따옴표 문자열로 출력한다. LOCAL_DEV는 service 키, `image:`, `container_name:`, 루트 `volumes:` 키, CLOUD_DEPLOY는 `image:`와 루트 `volumes:` 키다. 따옴표가 없으면 YAML이 `true`, `123`, `null`을 다른 타입으로 읽어 Compose가 거부하기 때문이다(`image must be a string`, `non-string key in services`).
- 파서가 큰따옴표, 백슬래시, 줄바꿈, 제어 문자를 막으므로 이스케이프하지 않고 감싼다. 생성기에 직접 전달된 값에 큰따옴표, 백슬래시, 줄바꿈(`\n`, `\r`, NEL)이나 YAML 1.2가 허용하지 않는 문자(탭을 뺀 제어 문자, DEL, C1 제어 문자, U+FFFE, U+FFFF, 짝 없는 서로게이트)가 있으면 `GENERATION400_2`(`INVALID_COMPONENT_STATE`)로 거부한다. NEL은 YAML 1.2에서는 출력 가능 문자지만 Compose의 YAML 파서가 공백으로 바꿔 읽어 값이 바뀌므로 거부한다.
- 이미지 값은 앞뒤 공백을 제거한 뒤 감싼다. 따옴표 안의 공백은 값에 남아 Docker가 `invalid reference format`으로 거부하기 때문이다. LOCAL_DEV와 CLOUD_DEPLOY 모두 같다.
- 볼륨 마운트 줄(`- 이름:경로`)은 콜론이 있어 항상 문자열이므로 감싸지 않는다. CLOUD_DEPLOY의 service 키와 앱 이미지는 고정값이라 감싸지 않는다.

## LOCAL_DEV 산출물

- 파일은 `local/docker-compose.yml`과 `local/.env` 두 개다. NGINX가 있으면 `local/nginx/default.conf`가 더해진다.
- 애플리케이션은 Compose에 넣지 않고 호스트에서 실행한다. 의존 인프라만 Compose service로 만들고 호스트 포트는 사용자가 입력한 포트를 쓴다.
- 앱 접속 정보는 호스트에서 쓰도록 `localhost` 기준으로 `.env`에 쓴다. 입력한 사용자 이름과 비밀번호가 그대로 `.env`에 들어가는 로컬 개발용 산출물이다.

## CLOUD_DEPLOY 산출물

- 파일은 모두 `cloud/` 아래에 놓이며 아래로 구성된다.
  - provider별 Terraform: `aws/terraform/` 또는 `oci/terraform/`의 `main.tf`, `variables.tf`, `terraform.tfvars.example`
  - 앱 타입별 `Dockerfile`(Node.js는 `.dockerignore`도 함께)
  - `docker-compose.cloud.yml`
  - `CLOUD_DEPLOY_WARNING.md`
  - Caddy가 있으면 `caddy/Caddyfile`과 사용자 안내 `CADDY_GUIDE.md`, NGINX가 있으면 `nginx/default.conf`
- 애플리케이션은 하나만 지원한다. 둘 이상이면 `GENERATION400_10`으로 거부한다.
- Compose에는 앱과 앱에 연결된 의존 인프라만 포함한다. 앱 service 이름은 `app`으로, 의존 인프라는 `mysql`, `redis`처럼 타입별 이름으로 고정한다.
- 앱 컨테이너는 의존 인프라에 Compose service 이름으로 접속한다. 앱은 `${APP_PORT:-<앱 포트>}`로 호스트에 공개한다. 단 NGINX나 Caddy가 있으면 앱 포트는 호스트에 공개하지 않는다. NGINX는 NGINX 포트만, Caddy는 80/443만 공개하고, 웹 서버가 Compose 네트워크의 `app:<앱 포트>`로 프록시한다.
- 비밀번호 같은 민감한 값은 생성 파일에 쓰지 않는다. `${변수:?외부 .env에 설정 필요}` 형태로 참조만 하고 사용자가 서버의 외부 `.env`를 제공한다.
- Terraform 보안 규칙은 SSH(22)를 관리자 접근 CIDR에, 앱 포트와 웹 서버 포트를 앱 접근 CIDR에 연다. NGINX가 있으면 `app_port` 변수 값이 NGINX 포트이다. Caddy가 있으면 앱 포트 ingress와 `app_port` 변수를 만들지 않고 80/443만 연다. 새 CIDR 변수나 전체 개방 기본값을 만들지 않는다.

## 앱 접속 변수

- 의존 인프라마다 타입별 중립 변수(`MYSQL_HOST`, `MYSQL_PORT`, `REDIS_HOST` 등)를 항상 만든다.
- Spring Boot는 여기에 더해 프레임워크 변수(`SPRING_DATASOURCE_*`, `SPRING_MONGODB_*`)를 만든다. 관계형 DB가 둘 이상이면 어느 DB가 `SPRING_DATASOURCE_*`가 될지 정할 수 없으므로 만들지 않고 안내 주석을 남긴다.
- Node.js는 프레임워크 변수가 없어 중립 변수만 쓴다. CLOUD_DEPLOY Compose는 그래프에 입력한 포트를 `PORT` 환경변수로 전달한다.

## plan-only 경계

- CLOUD_DEPLOY 산출물은 계획 전용 scaffold이며 `apply_ready=false`다. 이 프로젝트는 `terraform apply`, 클라우드 계정 조회, provider 자격증명을 처리하지 않는다.
- 사용자가 앱 파일, 외부 `.env`, provider 자격증명을 따로 제공한 뒤 `terraform init -backend=false`, `fmt`, `validate`로 확인하도록 `CLOUD_DEPLOY_WARNING.md`에 명시한다.
- Terraform 버전과 provider 버전은 고정한다(Terraform `>= 1.13.5, < 2.0.0`, AWS provider `6.22.0`, OCI provider `8.8.0`). 산출물은 `terraform validate` 수준까지 검증한다.
