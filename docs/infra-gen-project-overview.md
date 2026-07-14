# InfraGEN 프로젝트 기획 개요

## 1. 한 줄 개요

InfraGEN은 사용자가 웹 캔버스에서 인프라 노드와 연결 관계를 설계하면, 백엔드가 이를 검증하고 로컬 개발용 IaC 파일을 자동 생성하는 웹 기반 인프라 코드 생성 플랫폼이다.

## 2. 프로젝트 목적

- 인프라 설계 경험이 적은 주니어 개발자와 학생이 복잡한 배포 설정 없이 로컬 개발 환경을 빠르게 구성할 수 있도록 돕는다.
- 수동으로 작성하기 쉬운 `docker-compose.yml`, 환경 변수 파일, 생성 이력을 자동화해 설계 실수를 줄인다.
- 포트 충돌, 필수 속성 누락, 잘못된 의존 관계 같은 논리 오류를 코드 생성 전에 사전 검증한다.

## 3. 사용자 흐름

1. 사용자가 웹 캔버스에 Spring Boot, MySQL 같은 인프라 노드를 배치한다.
2. 노드 간 의존 관계를 연결하고, 포트·환경 변수·DB 정보 같은 속성을 입력한다.
3. 프론트엔드가 그래프 데이터를 JSON으로 정리해 백엔드에 전달한다.
4. 백엔드는 입력 그래프를 역직렬화하고 정합성을 검증한다.
5. 검증을 통과하면 로컬 개발용 IaC 파일을 생성한다.
6. 생성 결과는 화면에 표시되고, 프로젝트 이력으로 저장된다.

## 4. 현재 제품 방향

### 4.1 로컬 개발 중심

- 현재 핵심 산출물은 `docker-compose.yml`과 호스트 실행용 `.env`다.
- 로컬에서는 Spring Boot 앱 자체를 compose 서비스로 띄우지 않고, DB·Redis 같은 의존 인프라만 compose로 만든다.
- Spring Boot 앱은 호스트에서 실행하고, 백엔드는 이를 위한 연결 정보를 `.env`로 제공한다.

### 4.2 향후 클라우드 배포 확장

- 장기적으로는 Dockerfile과 Terraform 기반의 클라우드 배포 산출물로 확장한다.
- 다만 현재 기준에서는 로컬 개발용 생성 흐름이 우선이다.

## 5. 백엔드 핵심 역할

백엔드는 `parsing -> generation -> project history` 흐름을 담당한다.

- `parsing`: 그래프 구조, 포트, 필수 값, 연결 방향을 검증한다.
- `generation`: 검증된 입력을 바탕으로 IaC 파일을 생성한다.
- `project history`: 생성된 파일과 메타데이터를 프로젝트 이력으로 저장한다.

### 주요 책임

- 프로젝트 소유권 확인
- 입력 그래프 검증
- 로컬 개발용 compose 및 env 생성
- 생성 결과 영속화
- API 응답 표준화

## 6. 도메인 범위

### 현재 직접 다루는 요소

- `Project`
- `ProjectHistory`
- `GeneratedFile`
- `ParsingReqDTO` / `ParsingResultDTO`
- `GenerateResDTO`

### 주요 입력 노드

- `SPRING_BOOT`
- `MYSQL`

### 주요 산출물

- `docker-compose.yml`
- `.env`
- 생성 이력 데이터

## 7. 구현 기준과 제약

- 백엔드 API는 `POST /api/v1/projects/{projectId}/generate` 형태를 사용한다.
- `projectId`는 path variable만 사용하고, request body에는 넣지 않는다.
- 생성 실패는 generation 예외로만 처리하고, 파싱 검증은 parsing 계층에서만 담당한다.
- Swagger 문서는 controller docs 인터페이스에 둔다.
- Request DTO는 outer class + nested record 형태를 따른다.
- Converter는 static 메서드 중심으로 유지한다.

## 8. 현재 구현 상태 요약

로드맵 기준으로 다음 상태다.

- Generate API 골격과 패키지 분리는 완료됐다.
- 로컬 개발용 compose 생성과 호스트 env 생성 흐름은 구현됐다.
- `GeneratedFile.content` 저장과 생성 이력의 원자적 저장이 반영됐다.
- Swagger 문서와 MockMvc 테스트는 일부 진행 중이다.
- ZIP 다운로드는 선택 과제로 남아 있다.
- Dockerfile 및 Terraform 기반 클라우드 배포는 아직 시작 전이다.

## 9. 앞으로의 작업 방향

우선순위는 다음 순서가 적절하다.

1. Generate API 계약과 Swagger 문서의 정합성을 최종 정리한다.
2. MockMvc 통합 테스트와 생성 이력 저장 테스트를 보강한다.
3. 필요 시 ZIP 다운로드를 추가한다.
4. 이후 클라우드 배포용 Dockerfile/Terraform 작업으로 확장한다.

## 10. 참고 문서

- `docs/roadmap/README.md`
- `docs/roadmap/backend-roadmap-v2.md`
- `docs/roadmap/backend-roadmap-v1.md`
- `docs/plan/backend-implementation-plan.md`
- `docs/YA3조_최종보고서.md`
- `backend/AGENTS.md`
