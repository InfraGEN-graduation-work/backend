# InfraGEN 프론트엔드·백엔드 계약서

> 프론트엔드가 사용자 흐름에 따라 API를 연동할 때 사용하는 기준 문서다.
>
> 최종 갱신일: 2026-08-27
>
> 상태: 현재는 현재 코드에 구현된 계약, 예정은 설계만 있고 아직 구현되지 않은 계약, 진행 중은 Issue #31 등에서 변경 중인 계약이다.

## 1. 제품 흐름

InfraGEN은 캔버스에서 인프라 graph를 편집하고, 이를 검증해 IaC 파일과 생성 이력을 제공한다.

    로그인
    → 프로젝트 생성
    → 캔버스 복원
    → node·edge 편집
    → 프로젝트 저장
    → 생성 범위 선택
    → IaC 생성
    → 파일 확인·다운로드
    → 생성 이력 조회

LOCAL_DEV와 CLOUD_DEPLOY는 서로 다른 실행 환경이다.

### LOCAL_DEV

- Spring Boot: 호스트에서 실행
- dependency: Docker Compose로 실행
- 주요 산출물: docker-compose.yml, .env

### CLOUD_DEPLOY

- Spring Boot: Docker container로 실행
- dependency: cloud Compose로 실행
- 주요 산출물: Dockerfile, docker-compose.cloud.yml

### CLOUD_DEPLOY 기반시설

- 실행 대상: AWS EC2 또는 OCI Compute Instance
- 생성 도구: Terraform
- 결과 성격: 실제 배포가 아닌 plan-only scaffold

## 2. 공통 API 규칙

### 2.1 응답 envelope

대부분의 API는 다음 envelope을 사용한다.

    {
      "isSuccess": true,
      "code": "PROJECT200_1",
      "message": "프로젝트 목록 조회에 성공했습니다.",
      "result": {}
    }

실패 응답:

    {
      "isSuccess": false,
      "code": "PROJECT404_1",
      "message": "프로젝트 조회에 실패하였습니다.",
      "result": null
    }

프론트엔드는 HTTP status와 함께 code를 사용해 화면 동작을 결정한다. message는 사용자에게 표시할 수 있지만, 안정적인 분기 기준으로 사용하지 않는다.

health check는 예외적으로 공통 envelope을 사용하지 않는다.

    GET /health
    응답 body: UP

## 3. 회원 인증 정책

### 3.1 회원가입

용도: 신규 회원을 생성한다.

호출 시점: 회원가입 form 제출 시.

    POST /api/v1/auth/signup

요청:

    {
      "email": "user@example.com",
      "password": "password",
      "nickname": "user"
    }

성공:

- HTTP 201
- code: AUTH201_1
- result: null

UX:

- 성공하면 로그인 화면으로 이동한다.
- 실패하면 field validation 또는 중복 가입 오류를 표시한다.

### 3.2 일반 로그인

    POST /api/v1/auth/login

요청:

    {
      "email": "user@example.com",
      "password": "password"
    }

성공 result:

    {
      "accessToken": "..."
    }

동시에 refresh_token HttpOnly cookie가 설정된다.

UX:

1. accessToken을 메모리 상태에 저장한다.
2. members/me를 호출해 사용자 정보를 확인한다.
3. 로그인 후 프로젝트 목록으로 이동한다.

### 3.3 카카오 로그인

    POST /api/v1/auth/login/{provider}

현재 provider는 kakao 기준이다.

요청:

    {
      "authorizationCode": "..."
    }

성공 시 일반 로그인과 동일하게 accessToken body와 refresh cookie를 받는다.

### 3.4 토큰 재발급

    POST /api/v1/auth/reissue

요청 body는 없다. refresh_token cookie를 포함해야 한다.

UX:

- access token 만료 시 한 번만 재발급을 시도한다.
- 재발급 성공 시 원래 요청을 재시도한다.
- 재발급 실패 시 저장된 accessToken을 제거하고 로그인 화면으로 이동한다.

### 3.5 내 정보와 로그아웃

    GET /api/v1/members/me
    POST /api/v1/members/logout

로그아웃에는 Authorization header가 필요하다.

UX:

- 로그아웃 성공 시 메모리의 accessToken과 사용자 상태를 제거한다.
- refresh cookie는 백엔드가 삭제한다.

주의:

- logout 경로는 /api/v1/auth/logout이 아니라 /api/v1/members/logout이다.
- refresh cookie가 Secure=true, SameSite=None이므로 로컬 HTTP 환경에서는 cookie 동작을 별도로 확인한다.

## 4. 프로젝트 관리 정책

### 4.1 프로젝트 생성

용도: 캔버스 편집을 저장할 빈 프로젝트를 만든다.

    POST /api/v1/projects

요청:

    {
      "title": "my-infra",
      "description": "개발용 인프라"
    }

성공 result:

    {
      "projectId": 1,
      "createdAt": "2026-08-27T12:00:00"
    }

최초 status는 DRAFT다.

### 4.2 프로젝트 목록

    GET /api/v1/projects

성공 result:

    {
      "projectList": [
        {
          "projectId": 1,
          "title": "my-infra",
          "description": "개발용 인프라",
          "status": "DRAFT",
          "createdAt": "2026-08-27T12:00:00"
        }
      ]
    }

UX:

- 프로젝트 목록 진입 시 호출한다.
- projectId를 선택해 캔버스 편집 화면으로 이동한다.

### 4.3 프로젝트 상세 복원

    GET /api/v1/projects/{projectId}

용도: 프로젝트 정보와 저장된 node·edge를 캔버스에 복원한다.

UX:

- 편집 화면 진입 시 호출한다.
- 로딩 중에는 캔버스 skeleton을 표시한다.
- 성공하면 nodes와 edges를 캔버스 상태로 변환한다.
- 404이면 프로젝트 목록으로 돌아간다.

### 4.4 프로젝트 저장

    PUT /api/v1/projects/{projectId}

용도: 프로젝트 제목, 설명, 캔버스 node·edge 전체를 저장한다. 부분 수정이 아니라 기존 graph를 전체 교체하는 방식이다.

현재 진행 중인 Issue #31 계약:

    {
      "title": "my-infra",
      "description": "개발용 인프라",
      "nodes": [
        {
          "nodeId": "mysql-node-1",
          "nodeName": "mysql",
          "componentType": "MYSQL",
          "positionX": 100,
          "positionY": 200,
          "properties": {}
        }
      ],
      "edges": [
        {
          "sourceNodeId": "mysql-node-1",
          "targetNodeId": "spring-node-1"
        }
      ]
    }

현재 코드에는 Issue #31의 DTO·entity·converter·프로젝트 수정 nodeMap 전환이 반영되어 있다. 프로젝트 상세 응답의 node는 `nodeId`와 persistence용 `id`를 함께 제공하고, edge의 `sourceNodeId`·`targetNodeId`는 프론트 nodeId를 반환한다. 기존 DB 행의 nodeId migration과 end-to-end 검증은 아직 남아 있다.

프로젝트 상세 응답의 현재 node·edge 식별자 구조:

    {
      "nodes": [
        {
          "nodeId": "mysql-node-1",
          "nodeName": "mysql",
          "id": 101
        }
      ],
      "edges": [
        {
          "id": 201,
          "sourceNodeId": "mysql-node-1",
          "targetNodeId": "spring-node-1"
        }
      ]
    }

`id`는 백엔드 persistence 식별자이며 graph endpoint 연결에는 사용하지 않는다.

UX:

- 저장 중에는 중복 저장을 막거나 저장 상태를 표시한다.
- 저장 성공 시 “저장 완료” 상태를 표시한다.
- 저장 실패 시 현재 캔버스 내용을 지우지 않고 재시도할 수 있어야 한다.

### 4.5 프로젝트 삭제

    DELETE /api/v1/projects/{projectId}

프로젝트와 연결된 캔버스·history·generated file을 삭제한다.

UX:

- 삭제 전 확인 modal을 표시한다.
- 성공 시 프로젝트 목록으로 이동한다.

## 5. 캔버스 컴포넌트 정책

### 5.1 공통 node 구조

Generate 요청의 node는 다음 구조다.

    {
      "nodeId": "spring-node-1",
      "componentType": "SPRING_BOOT",
      "positionX": 400,
      "positionY": 200,
      "properties": {}
    }

nodeId는 캔버스가 생성하고 유지하는 문자열 식별자다. nodeName은 사용자가 보는 표시 이름이다.

### 5.2 현재 지원 컴포넌트

현재 parsing과 LOCAL_DEV 생성이 지원되는 컴포넌트:

- SPRING_BOOT
- MYSQL
- REDIS

enum에는 있지만 parser가 없는 컴포넌트:

- POSTGRESQL
- MONGODB
- NGINX
- APACHE

지원되지 않는 컴포넌트는 캔버스 palette에서 비활성화한다.

### 5.3 Spring Boot properties

    {
      "name": "app",
      "port": 8080,
      "javaVersion": "17",
      "containerName": "spring-app"
    }

검증:

- name 필수
- port는 1024~65535
- javaVersion은 숫자 형식
- 전체 graph에서 port 중복 불가

### 5.4 MySQL properties

    {
      "imageVersion": "mysql:8.0",
      "containerName": "mysql",
      "volumeName": "mysql_data",
      "port": 3306,
      "env": {
        "databaseName": "appdb",
        "username": "user",
        "userPassword": "userpass12",
        "rootPassword": "rootpass12"
      }
    }

검증:

- imageVersion 필수
- databaseName은 영문·숫자·언더바만 허용
- rootPassword 8자 이상
- username 필수
- userPassword 필수
- volumeName 선택

### 5.5 Redis properties

    {
      "imageVersion": "redis:7.4",
      "containerName": "redis",
      "volumeName": "redis_data",
      "port": 6379,
      "password": "redis-password"
    }

검증:

- imageVersion 필수
- password 필수
- port 1024~65535
- volumeName 선택

Redis password는 LOCAL_DEV Compose command와 호스트 .env에 사용된다.

### 5.6 edge 정책

Generate graph edge 구조:

    {
      "edgeId": "edge-1",
      "sourceNodeId": "mysql-node-1",
      "targetNodeId": "spring-node-1"
    }

정책:

- source → target 방향이다.
- source가 먼저 준비되고 target이 나중에 실행된다.
- MySQL·Redis → Spring Boot 연결을 사용한다.
- 존재하지 않는 node를 edge가 참조할 수 없다.
- 순환 참조를 허용하지 않는다.
- 중복 edge는 무시된다.
- 프로젝트 저장 요청에는 `edgeId`를 받지 않는다.
- 프로젝트 상세 응답의 edge `id`는 DB persistence 식별자이며, endpoint는 `sourceNodeId`·`targetNodeId`를 사용한다.

## 6. IaC 생성 정책

### 6.1 현재 Generate API

    POST /api/v1/projects/{projectId}/generate

현재 query parameter:

- 생략 또는 outputFormat=DOCKER_COMPOSE: LOCAL_DEV
- outputFormat=TERRAFORM: CLOUD_DEPLOY
- 지원하지 않는 값: HTTP 400, GENERATION400_1

생성에 실패하면 history를 저장하지 않는다.

### 6.2 LOCAL_DEV 생성

생성 결과:

- docker-compose.yml
- .env
- historyId

실행 방식:

- Spring Boot는 호스트에서 실행
- MySQL·Redis는 Docker Compose에서 실행
- .env에는 DB와 Redis password가 포함될 수 있음

UX:

- Generate 버튼 클릭 전 graph validation을 수행한다.
- 생성 중에는 중복 요청을 막는다.
- 생성 성공 후 LOCAL_DEV 결과 탭을 표시한다.
- .env는 기본 마스킹한다.

### 6.3 CLOUD_DEPLOY 생성

outputFormat=TERRAFORM일 때 현재 생성 결과:

- AWS Terraform
- OCI Terraform
- Dockerfile
- docker-compose.cloud.yml
- CLOUD_DEPLOY_WARNING.md

현재 Terraform generator는 AWS와 OCI scaffold를 모두 생성한다. 사용자가 캔버스에서 AWS 또는 OCI node를 선택하는 계약은 아직 구현되지 않았다.

CLOUD_DEPLOY는 plan-only scaffold이며 자동 terraform apply를 제공하지 않는다.

### 6.4 통합 출력 예정

현재는 LOCAL_DEV와 CLOUD_DEPLOY를 한 번의 요청으로 함께 생성할 수 없다.

예정 계약:

    {
      "outputs": [
        "LOCAL_DEV",
        "CLOUD_DEPLOY"
      ],
      "deploymentTarget": {
        "provider": "AWS",
        "region": "ap-northeast-2",
        "instanceType": "t3.micro",
        "instanceName": "infragen-app"
      },
      "graph": {
        "nodes": [],
        "edges": []
      }
    }

예정 UX:

- DeploymentTarget이 없으면 LOCAL_DEV를 기본 선택
- DeploymentTarget이 있으면 LOCAL_DEV와 CLOUD_DEPLOY를 기본 선택
- 사용자가 생성 범위를 변경할 수 있음
- 결과를 LOCAL_DEV와 CLOUD_DEPLOY 탭으로 분리

위 요청·응답은 아직 백엔드에 구현되지 않았으므로 현재 프론트 요청에 사용하지 않는다.

## 7. 생성 이력 정책

Generate 성공 시 생성 결과가 자동으로 history에 저장된다.

현재 응답 result:

    {
      "files": [
        {
          "fileName": "docker-compose.yml",
          "content": "..."
        },
        {
          "fileName": ".env",
          "content": "..."
        }
      ],
      "historyId": 42
    }

API:

    POST /api/v1/projects/{projectId}/histories
    GET /api/v1/projects/{projectId}/histories
    GET /api/v1/projects/{projectId}/histories/{historyId}

UX:

- Generate 성공 후 historyId를 보관한다.
- history 목록은 최신순으로 표시한다.
- history 상세에서 생성 파일 목록과 본문을 표시한다.
- .env 본문은 기본 마스킹한다.

## 8. 에러 처리와 UX

### HTTP 400

- 입력 또는 graph 오류를 해당 필드·node·edge에 표시한다.

### HTTP 401

- access token 재발급 후 원래 요청을 한 번 재시도한다.

### HTTP 403

- 권한 없음 안내를 표시한다.

### HTTP 404

- 프로젝트 또는 history 없음 안내를 표시한다.

### HTTP 409

- 동시 수정 또는 중복 충돌 안내를 표시한다.

### HTTP 500

- 서버 오류와 재시도 안내를 표시한다.

주요 code:

- AUTH401_1: 유효하지 않은 token
- AUTH401_2: 로그아웃된 token
- AUTH401_3: 만료된 token
- AUTH400_1: 지원하지 않는 social provider
- PROJECT404_1: 프로젝트 없음
- PROJECT409_1: 동시 수정 충돌
- PROJECT400_5: 프로젝트 내부 nodeId 중복
- PARSING400_4: 포트 중복
- PARSING400_5: 포트 범위 오류
- PARSING400_9: 순환 참조
- PARSING400_10: 잘못된 dependency 방향
- PARSING400_20: Redis imageVersion 누락
- PARSING400_22: Redis password 누락
- GENERATION400_1: 지원하지 않는 output format
- GENERATION400_2: 생성에 필요한 component 상태 오류

## 9. 민감 정보 처리

- access token은 localStorage보다 메모리 보관을 우선한다.
- refresh token은 HttpOnly cookie로만 관리한다.
- MySQL·Redis password가 포함된 .env는 기본 마스킹한다.
- password를 일반 로그, analytics, URL query parameter에 넣지 않는다.
- CLOUD_DEPLOY Terraform에 DB password, JWT secret, runtime credential을 넣지 않는다.
- .env 다운로드 전 민감 정보 포함 안내를 표시한다.

## 10. 프론트 연동 전 결정 필요 사항

1. Issue #31을 통해 nodeId를 프로젝트 저장·조회·Generate 전체의 기준으로 통일한다.
2. nodeName은 표시용으로만 사용하고 edge endpoint에서 제거한다.
3. 현재 애플리케이션은 `ddl-auto: update`를 사용한다. 개발 서버에는 기존 프로젝트 데이터가 없으므로 DB 초기화 후 새 schema를 반영한다. 기존 데이터를 보존하는 DB의 backfill과 schema 제약조건 반영은 후속 migration 작업으로 둔다.
4. LOCAL_DEV와 CLOUD_DEPLOY 통합 출력의 요청·응답 구조를 확정한다.
5. AWS·OCI를 runtime graph 밖의 DeploymentTarget으로 관리할지 결정한다.
6. 통합 출력 시 하나의 history에 여러 output을 어떤 구조로 저장할지 결정한다.

## 11. 관련 기준 파일

- 인증 Controller: src/main/java/com/infragen/infragen/domain/auth/controller/AuthController.java
- 프로젝트 Controller: src/main/java/com/infragen/infragen/domain/project/controller/ProjectController.java
- history Controller: src/main/java/com/infragen/infragen/domain/project/controller/ProjectHistoryController.java
- Generate Controller: src/main/java/com/infragen/infragen/domain/generation/controller/GenerationController.java
- parsing Service: src/main/java/com/infragen/infragen/domain/parsing/service/ParsingService.java
- Issue #31 handoff: docs/handoff/issue-31-handoff.md
- 전체 계획: docs/handoff/plan/backend-future-plan.md
