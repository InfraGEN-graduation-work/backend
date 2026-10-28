# InfraGEN 프로젝트 기획 개요

## 1. 한 줄 개요

InfraGEN은 사용자가 웹 캔버스에서 인프라 노드와 연결 관계를 설계하면, 백엔드가 이를 검증하고 IaC 파일을 자동 생성하는 웹 기반 인프라 코드 생성 플랫폼이다.

## 2. 프로젝트 목적

- 인프라 설계 경험이 적은 주니어 개발자와 학생이 복잡한 배포 설정 없이 개발 환경을 빠르게 구성하도록 돕는다.
- 손으로 쓰기 쉬운 `docker-compose.yml`, 환경 변수 파일, Terraform scaffold와 생성 이력을 자동화해 설계 실수를 줄인다.
- 포트 충돌, 필수 속성 누락, 잘못된 의존 관계 같은 논리 오류를 코드 생성 전에 검증한다.

## 3. 사용자 흐름

1. 웹 캔버스에 Spring Boot, MySQL 같은 노드를 배치하고 의존 관계를 연결한 뒤 포트, 환경 변수, DB 정보 같은 속성을 입력한다. 여러 사용자가 같은 프로젝트 캔버스를 실시간으로 함께 편집할 수 있다.
2. 프론트엔드가 그래프를 JSON으로 백엔드에 보낸다.
3. 백엔드는 그래프 정합성을 검증하고, 통과하면 선택한 배포 옵션의 IaC 파일을 생성한다.
4. 생성 결과는 화면에 표시되고 프로젝트 이력으로 저장된다.

## 4. 산출물

- **LOCAL_DEV**: Spring Boot 또는 Node.js 앱은 호스트에서 실행하고, 캔버스에 배치된 지원 의존 인프라만 `docker-compose.yml`로 만든다. 앱 연결 정보는 호스트 실행용 `.env`로 제공한다.
- **CLOUD_DEPLOY**: AWS EC2, OCI Compute Instance 대상의 plan-only Terraform scaffold, runtime Dockerfile, Docker Compose bootstrap. 실제 `terraform apply`와 클라우드 계정 연동은 하지 않는다.
- 공통: 생성 파일 본문과 생성 이력

## 5. 서비스 핵심 역할

- `parsing`: 그래프 구조, 포트, 필수 값, 연결 방향을 검증한다.
- `generation`: 검증된 입력으로 IaC 파일을 생성한다.
- `project`: 프로젝트, 생성 이력, 생성 파일, 협업자를 저장하고 제공한다.
- `collaboration`: 실시간 협업 편집의 operation 동기화와 재접속을 처리한다.

## 6. 도메인 범위

주요 개념:

- 프로젝트: 하나의 인프라 설계와 생성 결과를 묶는 단위
- 설계 그래프: 사용자가 배치한 노드와 연결 관계
- 생성 이력: 프로젝트에서 생성된 결과의 버전과 메타데이터
- 생성 파일: 생성 결과를 구성하는 파일과 내용

지원 입력 노드는 `SPRING_BOOT`, `NODE_JS`, `MYSQL`, `POSTGRESQL`, `MONGODB`, `REDIS`, `NGINX`, `CADDY`다. 의존 인프라는 캔버스에 있을 때만 Compose service와 접속 정보에 반영한다. `NGINX`와 `CADDY`는 앱 앞단의 리버스 프록시다. `NGINX`는 LOCAL_DEV와 CLOUD_DEPLOY를, `CADDY`는 CLOUD_DEPLOY만 지원한다. Local 단독 생성 요청에 Local에서 지원하지 않는 노드(`CADDY`)가 있으면 거부하고, 클라우드 요청에 로컬 스펙을 함께 포함하면 그 노드는 클라우드 산출물에만 들어간다. Apache는 지원하지 않기로 결정했다.

## 7. 앱 준비 사항

생성된 파일을 쓰려면 앱 쪽에서 맞춰야 할 것이 있다. 생성기는 앱 코드를 만들지 않는다. 노드별 준비 사항과 실행 방법은 생성 결과에 포함된 안내 문서가 그래프에 있는 노드만 골라 안내하므로, 이 절은 요약과 캔버스 입력 규칙만 적는다.

- **생성 안내 문서**: CLOUD_DEPLOY는 `cloud/CLOUD_DEPLOY_GUIDE.md`, LOCAL_DEV는 `local/LOCAL_DEV_GUIDE.md`를 만든다. 앱 섹션이 먼저 오고 DB, 캐시, 웹 서버 섹션이 이어진다. 비밀번호 같은 값은 쓰지 않고 변수 이름만 쓴다. plan-only 경고는 `cloud/CLOUD_DEPLOY_WARNING.md`에 따로 둔다. 파일 구성과 섹션 순서는 [배포 옵션과 산출물 정책](policy/generation-output-policy.md)에 기록한다.
- **Spring Boot**: 연결 설정은 `SPRING_DATASOURCE_*`, `SPRING_MONGODB_*` 같은 프레임워크 변수로 자동 전달된다. 앱 코드를 바꿀 필요가 없다. 앱은 그래프에 입력한 포트로 listen해야 한다.
- **Node.js**: DB 접속 정보는 `MYSQL_HOST`, `MYSQL_PORT`처럼 DB별 중립 변수로 전달되고, 앱 코드에서 연결 설정을 직접 조립한다. `start` 스크립트의 `exec`, CLOUD_DEPLOY의 `PORT` 환경변수, LOCAL_DEV의 `.env` 로딩 방법은 안내 문서에 있다.
- **NGINX** (LOCAL_DEV, CLOUD_DEPLOY)
  - 캔버스에서 앱 노드에서 NGINX 노드로 연결선을 긋는다. 요청이 흐르는 방향과 화살표가 반대이며, 연결이 맞지 않으면 생성 요청이 거부된다(`PARSING400_35`).
  - 외부에 공개할 포트를 입력한다. 1~65535를 허용하고 범위를 벗어나면 `PARSING400_36`으로 거부된다.
  - 프록시 대상(LOCAL_DEV는 호스트의 앱, CLOUD_DEPLOY는 `app` 컨테이너)과 공개 포트, Terraform `app_port` 값은 안내 문서에 있다.
  - 속성과 세부 규칙은 NGINX 담당자가 관리하며 이 문서는 지원 범위만 적는다.
- **Caddy** (CLOUD_DEPLOY 전용)
  - 캔버스에서 앱 노드에서 Caddy 노드로 연결선을 긋는다. 요청이 흐르는 방향과 화살표가 반대이며, 방향이 틀리거나 연결이 없으면 생성 요청이 거부된다. Caddy는 하나만 둘 수 있다.
  - 호스트 포트는 입력하지 않는다. 80/443으로 고정이다.
  - 도메인을 입력하면 Caddy가 HTTPS 인증서를 자동 발급하며, 인증서를 보관할 볼륨 이름이 필요하다. 도메인이 없으면 HTTP(80)만 동작한다. DNS와 접근 CIDR 준비는 안내 문서에 있다.
  - 정책과 이유는 [Caddy 웹 서버 지원 정책](policy/caddy-web-server-policy.md)에 기록한다.
- **공통**: `.env` 값(비밀번호, 사용자 이름 등)에 큰따옴표, 백슬래시, 달러 기호를 쓸 수 없다. DB·Redis 노드의 사용자 이름과 비밀번호는 줄바꿈과 NUL도 쓸 수 없고, 생성 요청의 파싱 단계에서 `PARSING400_33`으로 거부된다. 프로젝트 저장 시점에는 검사하지 않는다. 생성 단계의 `GENERATION400_13`, `GENERATION400_14` 검사는 마지막 방어선으로 남는다. 이 제한은 Spring 프로젝트에도 똑같이 적용된다.

## 8. 정책 문서

규칙과 그 이유는 `docs/policy/`에 기록한다.

- [그래프 연결 정책](policy/graph-connection-policy.md): 연결 방향, 시작 우선순위, 의존 중복, 연결되지 않은 노드의 처리
- [배포 옵션과 산출물 정책](policy/generation-output-policy.md): 배포 옵션 조합, LOCAL_DEV와 CLOUD_DEPLOY 산출물, plan-only 경계
- [입력 검증 정책](policy/input-validation-policy.md): 포트, 필수값과 형식, 환경변수 금지 문자
- [Caddy 웹 서버 지원 정책](policy/caddy-web-server-policy.md): 웹 서버 지원 범위와 규칙
