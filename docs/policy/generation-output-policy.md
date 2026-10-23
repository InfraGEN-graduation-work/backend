# 배포 옵션과 산출물 정책

Generate 요청의 배포 옵션 조합과 옵션별 산출물 구성, 생성 파일의 경계를 기록한다. 요청 검증은 `GenerationCommandService`와 `DeploymentTargetValidator`가 맡고, 파일 생성은 `DockerComposeIaCGenerator`(LOCAL_DEV)와 `TerraformIaCGenerator`(CLOUD_DEPLOY)가 맡는다.

## 배포 옵션 조합

- `deploymentOption`은 `LOCAL`, `AWS`, `OCI` 중 하나이며 필수다(`GENERATION400_4`).
- `LOCAL`은 `includeLocalSpec`을 명시적으로 `false`로 보내야 하고 `deploymentTarget`은 없어야 한다. 어긋나면 `GENERATION400_6`이다.
- `AWS`와 `OCI`는 해당 provider의 `deploymentTarget`이 필수이며(`GENERATION400_5`), provider가 배포 옵션과 같아야 한다(`GENERATION400_7`). provider별 필수값(리소스 이름, AMI 또는 이미지 ID, 관리자 접근 CIDR, 앱 접근 CIDR 등)이 비어 있으면 `GENERATION400_8`이다.
- 클라우드 요청에서 `includeLocalSpec`이 `true`이면 클라우드 산출물에 LOCAL_DEV 산출물을 이어 붙여 한 번에 반환한다. 이때 Local Compose renderer가 없는 노드(Caddy)는 로컬 쪽에서만 빠진다. 반대로 Local 단독 요청에 그런 노드가 있으면 거부한다(`GENERATION400_15`). Local renderer가 있는 NGINX는 거부하지 않는다.
- 생성 결과는 요청 한 번당 하나의 생성 이력으로 저장한다.

## LOCAL_DEV 산출물

- 파일은 `local/docker-compose.yml`, `local/.env` 순서이고, NGINX가 있으면 `local/nginx/default.conf`가 더해진다. 그래프에 앱이나 LOCAL_DEV가 지원하는 의존 인프라가 하나라도 있으면 실행 안내 `local/LOCAL_DEV_GUIDE.md`가 맨 뒤에 붙는다.
- 애플리케이션은 Compose에 넣지 않고 호스트에서 실행한다. 의존 인프라만 Compose service로 만들고 호스트 포트는 사용자가 입력한 포트를 쓴다.
- 앱 접속 정보는 호스트에서 쓰도록 `localhost` 기준으로 `.env`에 쓴다. 입력한 사용자 이름과 비밀번호가 그대로 `.env`에 들어가는 로컬 개발용 산출물이다.

## CLOUD_DEPLOY 산출물

- 파일은 모두 `cloud/` 아래에 놓이며 아래로 구성된다.
  - provider별 Terraform: `aws/terraform/` 또는 `oci/terraform/`의 `main.tf`, `variables.tf`, `terraform.tfvars.example`
  - 앱 타입별 `Dockerfile`(Node.js는 `.dockerignore`도 함께)
  - `docker-compose.cloud.yml`
  - Caddy가 있으면 `caddy/Caddyfile`, NGINX가 있으면 `nginx/default.conf`
  - 통합 사용자 안내 `CLOUD_DEPLOY_GUIDE.md`
  - `CLOUD_DEPLOY_WARNING.md`
- 애플리케이션은 하나만 지원한다. 둘 이상이면 `GENERATION400_10`으로 거부한다.
- Compose에는 앱과 앱에 연결된 의존 인프라만 포함한다. 앱 service 이름은 `app`으로, 의존 인프라는 `mysql`, `redis`처럼 타입별 이름으로 고정한다.
- 앱 컨테이너는 의존 인프라에 Compose service 이름으로 접속한다. 앱은 `${APP_PORT:-<앱 포트>}`로 호스트에 공개한다. 단 NGINX나 Caddy가 있으면 앱 포트는 호스트에 공개하지 않는다. NGINX는 NGINX 포트만, Caddy는 80/443만 공개하고, 웹 서버가 Compose 네트워크의 `app:<앱 포트>`로 프록시한다.
- 비밀번호 같은 민감한 값은 생성 파일에 쓰지 않는다. `${변수:?외부 .env에 설정 필요}` 형태로 참조만 하고 사용자가 서버의 외부 `.env`를 제공한다.
- Terraform 보안 규칙은 SSH(22)를 관리자 접근 CIDR에, 앱 포트와 웹 서버 포트를 앱 접근 CIDR에 연다. NGINX가 있으면 `app_port` 변수 값이 NGINX 포트이다. Caddy가 있으면 앱 포트 ingress와 `app_port` 변수를 만들지 않고 80/443만 연다. 새 CIDR 변수나 전체 개방 기본값을 만들지 않는다.

## 앱 접속 변수

- 의존 인프라마다 타입별 중립 변수(`MYSQL_HOST`, `MYSQL_PORT`, `REDIS_HOST` 등)를 항상 만든다.
- Spring Boot는 여기에 더해 프레임워크 변수(`SPRING_DATASOURCE_*`, `SPRING_MONGODB_*`)를 만든다. 관계형 DB가 둘 이상이면 어느 DB가 `SPRING_DATASOURCE_*`가 될지 정할 수 없으므로 만들지 않고 안내 주석을 남긴다.
- Node.js는 프레임워크 변수가 없어 중립 변수만 쓴다. CLOUD_DEPLOY Compose는 그래프에 입력한 포트를 `PORT` 환경변수로 전달한다.

## 사용자 안내 문서

생성 결과만 받는 사용자가 저장소 문서 없이 실행을 준비할 수 있도록, 산출물마다 안내 문서 하나에 그래프에 있는 노드의 섹션만 모은다. 노드별로 파일을 나누면 안내가 흩어지므로 섹션으로 나눈다.

- CLOUD_DEPLOY `CLOUD_DEPLOY_GUIDE.md`: 앱 섹션이 맨 앞에 오고, 의존 인프라와 웹 서버 섹션이 `ComponentType` 순서(MySQL, PostgreSQL, MongoDB, Redis, NGINX, Caddy)로 이어진다. 앱은 항상 있으므로 모든 CLOUD_DEPLOY 산출물에 생긴다. 앱 섹션은 앱 파일 배치, listen 포트, DB 접속 변수, 실행 명령을, DB와 캐시 섹션은 외부 `.env`에 설정할 변수 이름을, 웹 서버 섹션은 공개 포트와 설정 변경 방법을 안내한다.
- `CLOUD_DEPLOY_WARNING.md`는 합치지 않고 따로 둔다. plan-only 경고가 안내 섹션 사이에 묻히지 않게 하기 위해서다. 경고는 무엇이 없으면 실행할 수 없는지를, 안내는 어떻게 준비하는지를 쓴다.
- LOCAL_DEV `LOCAL_DEV_GUIDE.md`: 앱 노드마다 섹션을 만들고(LOCAL_DEV는 앱이 여러 개일 수 있다), 의존 인프라 섹션이 시작 우선순위 순서로 이어진다. 앱 섹션은 `.env`를 호스트 실행 앱에 넣는 방법을, 의존 인프라 섹션은 `localhost:<포트>`와 `.env`에 들어 있는 변수 이름을 안내한다.
- 안내 문서에는 비밀번호 같은 값을 쓰지 않고 변수 이름만 쓴다. LOCAL_DEV `.env`에 실제 값이 있어도 같다.
- CLOUD_DEPLOY 안내의 외부 `.env` 변수 이름은 Compose의 `${변수:?` 참조와, LOCAL_DEV 안내의 변수 이름은 `.env` 키와 테스트로 대조한다. 안내 문구에는 코드나 공식 문서로 확인한 사실만 쓴다.
- Node.js `.dockerignore`는 `CLOUD_DEPLOY_GUIDE.md`를 이미지에서 제외한다.

## plan-only 경계

- CLOUD_DEPLOY 산출물은 계획 전용 scaffold이며 `apply_ready=false`다. 이 프로젝트는 `terraform apply`, 클라우드 계정 조회, provider 자격증명을 처리하지 않는다.
- 사용자가 앱 파일, 외부 `.env`, provider 자격증명을 따로 제공한 뒤 `terraform init -backend=false`, `fmt`, `validate`로 확인하도록 `CLOUD_DEPLOY_WARNING.md`에 명시한다.
- Terraform 버전과 provider 버전은 고정한다(Terraform `>= 1.13.5, < 2.0.0`, AWS provider `6.22.0`, OCI provider `8.8.0`). 산출물은 `terraform validate` 수준까지 검증한다.
