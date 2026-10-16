# 배포 옵션과 산출물 정책

Generate 요청의 배포 옵션 조합과 옵션별 산출물 구성, 생성 파일의 경계를 기록한다. 요청 검증은 `GenerationCommandService`와 `DeploymentTargetValidator`가 맡고, 파일 생성은 `DockerComposeIaCGenerator`(LOCAL_DEV)와 `TerraformIaCGenerator`(CLOUD_DEPLOY)가 맡는다.

## 배포 옵션 조합

- `deploymentOption`은 `LOCAL`, `AWS`, `OCI` 중 하나이며 필수다(`GENERATION400_4`).
- `LOCAL`은 `includeLocalSpec`을 명시적으로 `false`로 보내야 하고 `deploymentTarget`은 없어야 한다. 어긋나면 `GENERATION400_6`이다.
- `AWS`와 `OCI`는 해당 provider의 `deploymentTarget`이 필수이며(`GENERATION400_5`), provider가 배포 옵션과 같아야 한다(`GENERATION400_7`). provider별 필수값(리소스 이름, AMI 또는 이미지 ID, 관리자 접근 CIDR, 앱 접근 CIDR 등)이 비어 있으면 `GENERATION400_8`이다.
- 클라우드 요청에서 `includeLocalSpec`이 `true`이면 클라우드 산출물에 LOCAL_DEV 산출물을 이어 붙여 한 번에 반환한다. 이때 로컬에서 지원하지 않는 웹 서버는 로컬 쪽에서만 빠진다. 반대로 Local 단독 요청에 웹 서버가 있으면 거부한다(`GENERATION400_15`).
- 생성 결과는 요청 한 번당 하나의 생성 이력으로 저장한다.

## LOCAL_DEV 산출물

- 파일은 `local/docker-compose.yml`과 `local/.env` 두 개다.
- 애플리케이션은 Compose에 넣지 않고 호스트에서 실행한다. 의존 인프라만 Compose service로 만들고 호스트 포트는 사용자가 입력한 포트를 쓴다.
- 앱 접속 정보는 호스트에서 쓰도록 `localhost` 기준으로 `.env`에 쓴다. 입력한 사용자 이름과 비밀번호가 그대로 `.env`에 들어가는 로컬 개발용 산출물이다.

## CLOUD_DEPLOY 산출물

- 파일은 모두 `cloud/` 아래에 놓이며 아래로 구성된다.
  - provider별 Terraform: `aws/terraform/` 또는 `oci/terraform/`의 `main.tf`, `variables.tf`, `terraform.tfvars.example`
  - 앱 타입별 `Dockerfile`(Node.js는 `.dockerignore`도 함께)
  - `docker-compose.cloud.yml`
  - `CLOUD_DEPLOY_WARNING.md`
  - 웹 서버가 있으면 `caddy/Caddyfile`
- 애플리케이션은 하나만 지원한다. 둘 이상이면 `GENERATION400_10`으로 거부한다.
- Compose에는 앱과 앱에 연결된 의존 인프라만 포함한다. 앱 service 이름은 `app`으로, 의존 인프라는 `mysql`, `redis`처럼 타입별 이름으로 고정한다.
- 앱 컨테이너는 의존 인프라에 Compose service 이름으로 접속한다. 앱은 `${APP_PORT:-<앱 포트>}`로 호스트에 공개한다.
- 비밀번호 같은 민감한 값은 생성 파일에 쓰지 않는다. `${변수:?외부 .env에 설정 필요}` 형태로 참조만 하고 사용자가 서버의 외부 `.env`를 제공한다.
- Terraform 보안 규칙은 SSH(22)를 관리자 접근 CIDR에, 앱 포트와 웹 서버 포트를 앱 접근 CIDR에 연다. 새 CIDR 변수나 전체 개방 기본값을 만들지 않는다.

## 앱 접속 변수

- 의존 인프라마다 타입별 중립 변수(`MYSQL_HOST`, `MYSQL_PORT`, `REDIS_HOST` 등)를 항상 만든다.
- Spring Boot는 여기에 더해 프레임워크 변수(`SPRING_DATASOURCE_*`, `SPRING_MONGODB_*`)를 만든다. 관계형 DB가 둘 이상이면 어느 DB가 `SPRING_DATASOURCE_*`가 될지 정할 수 없으므로 만들지 않고 안내 주석을 남긴다.
- Node.js는 프레임워크 변수가 없어 중립 변수만 쓴다. CLOUD_DEPLOY Compose는 그래프에 입력한 포트를 `PORT` 환경변수로 전달한다.

## plan-only 경계

- CLOUD_DEPLOY 산출물은 계획 전용 scaffold이며 `apply_ready=false`다. 이 프로젝트는 `terraform apply`, 클라우드 계정 조회, provider 자격증명을 처리하지 않는다.
- 사용자가 앱 파일, 외부 `.env`, provider 자격증명을 따로 제공한 뒤 `terraform init -backend=false`, `fmt`, `validate`로 확인하도록 `CLOUD_DEPLOY_WARNING.md`에 명시한다.
- Terraform 버전과 provider 버전은 고정한다(Terraform `>= 1.13.5, < 2.0.0`, AWS provider `6.22.0`, OCI provider `8.8.0`). 산출물은 `terraform validate` 수준까지 검증한다.
