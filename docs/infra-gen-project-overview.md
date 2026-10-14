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

지원 입력 노드는 `SPRING_BOOT`, `NODE_JS`, `MYSQL`, `POSTGRESQL`, `MONGODB`, `REDIS`다. 의존 인프라는 캔버스에 있을 때만 Compose service와 접속 정보에 반영한다. NGINX는 다른 팀원이 별도로 진행하며 아직 지원하지 않고, Apache는 지원하지 않기로 결정했다.

## 7. 앱 준비 사항

생성된 파일을 쓰려면 앱 쪽에서 아래 내용을 맞춰야 한다. 생성기는 앱 코드를 만들지 않는다.

- **Spring Boot**: 연결 설정은 `SPRING_DATASOURCE_*`, `SPRING_MONGODB_*` 같은 프레임워크 변수로 자동 전달된다. 앱 코드를 바꿀 필요가 없다.
- **Node.js**
  - DB 접속 정보는 `MYSQL_HOST`, `MYSQL_PORT`처럼 DB별 중립 변수로 전달된다. 앱 코드에서 연결 설정을 직접 조립한다.
  - LOCAL_DEV에서 Node.js는 `.env`를 자동으로 읽지 않는다. `node --env-file=local/.env`(Node 20.6 이상)를 쓰거나 dotenv로 읽는다.
  - `package.json`의 `start` 스크립트는 `exec node server.js`처럼 `exec`로 시작해야 한다. 그래야 `docker stop`의 종료 신호(SIGTERM)가 앱에 전달된다. 앱이 `process.on("SIGTERM", ...)`로 신호를 받아 정리하는 코드를 가져야 정리 작업이 실행된다.
  - CLOUD_DEPLOY의 Compose는 앱 컨테이너에 그래프에 입력한 포트를 `PORT` 환경변수로 전달한다. 앱은 `process.env.PORT`로 listen해야 포트 연결이 맞는다. LOCAL_DEV는 호스트에서 실행하므로 `PORT`를 전달하지 않고, 앱이 직접 맞춘다.
  - CLOUD_DEPLOY에서는 `cloud/` 폴더에 `package.json`, `package-lock.json`과 앱 파일을 둔다. 생성된 `.dockerignore`가 `.env`와 `node_modules`를 이미지에서 제외한다.
- **공통**: `.env` 값(비밀번호, 사용자 이름 등)에 큰따옴표, 백슬래시, 달러 기호를 쓸 수 없다. 생성 시 `GENERATION400_14`로 거부된다. 이 제한은 Spring 프로젝트에도 똑같이 적용된다.
