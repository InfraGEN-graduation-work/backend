# InfraGEN 백엔드 통합 실행 계획

> 기준 문서: `AGENTS.md`, `docs/infra-gen-project-overview.md`, `docs/roadmap/README.md`
>
> 이 문서는 기존 실행 계획을 통합한 단일 기준 실행 계획이다.
> 로드맵은 상태를, 본 문서는 실제 실행 순서와 기술 계약을 담당한다.

## 1. 목적

이 문서는 백엔드 구현을 어떤 순서로 어떤 검증과 함께 진행할지 하나의 기준으로 정리한다.

핵심 목표는 다음과 같다.

1. Generate API 계약을 안정화한다.
2. 생성 결과 저장과 이력 저장의 정합성을 보강한다.
3. 클라우드 배포, Redis, 문서 정합성까지 단계적으로 확장한다.

핵심 원칙은 다음과 같다.

- 요청 범위를 넘지 않는다.
- 변경은 가능한 최소 파일로 제한한다.
- Controller, Service, Converter, Repository 책임을 분리한다.
- 검증은 관련 테스트로 바로 확인한다.
- 기존 API 응답과 예외 규칙을 바꾸지 않는다.
- plan 문서가 여러 개로 보이더라도 역할이 겹치지 않게 정리한다.

## 2. 현재 기준선

현재 로드맵상 백엔드는 대체로 다음 상태다.

- Generate API 골격은 이미 존재한다.
- 로컬 개발용 `docker-compose.yml`과 호스트용 `.env` 생성 흐름이 있다.
- `GeneratedFile.content` 저장과 생성 이력의 원자 저장이 반영되어 있다.
- Swagger 문서와 MockMvc 기반 정리 작업은 일부 진행 중이다.
- MySQL 파서의 필수값 검증과 단위 테스트가 보강되었다.
- ZIP 다운로드는 선택 과제로 남아 있다.
- Dockerfile과 Terraform 기반 클라우드 배포는 아직 본격 착수 전이다.

즉, 지금은 기능 추가보다 계약 정리, 정합성 보강, 검증 강화가 먼저다.

## 3. 확정된 기술 방향

### 3.1 Generate 파이프라인

- `POST /api/v1/projects/{projectId}/generate`를 기준으로 유지한다.
- `projectId`는 path variable만 사용하고 request body에는 넣지 않는다.
- Controller는 요청 수신과 `ApiResponse<T>` 반환만 담당한다.
- 생성 실패는 generation 예외로 처리하고, 파싱 검증은 parsing 계층에 둔다.
- `ParsingService.parsing()`이 반환한 `ParsingResultDTO`만 generation 입력으로 사용한다.

### 3.2 LOCAL_DEV 출력

- 앱은 호스트에서 실행한다.
- Docker에는 의존 인프라만 둔다.
- `docker-compose.yml`에는 DB, Redis 같은 의존 인프라만 생성한다.
- `.env`에는 호스트 실행용 연결 정보만 생성한다.
- Spring Boot 노드는 파싱과 env 생성에는 참여하지만 compose `services:`에는 넣지 않는다.

### 3.3 CLOUD_DEPLOY 출력

- Dockerfile은 runtime-only 기준으로 설계한다.
- Terraform은 plan 가능한 스캐폴드 수준에서 시작한다.
- AWS RDS, ECS, ECR, 네트워크, 실행 역할 등을 단계적으로 분리한다.
- `apply_ready=false`와 non-runnable 경고를 명시적으로 유지한다.

### 3.4 Redis LOCAL_DEV

- 이미지, 컨테이너, 포트, 볼륨 정도만 모델링한다.
- password, ACL, TLS는 넣지 않는다.
- 호스트 앱용 `.env`에 localhost 기반 Redis 값을 생성한다.

## 4. 세부 실행 순서

### Gate 1: Generate 신뢰성

1. exact dependencies/image/properties 준비
2. persisted fixture + Security test principal 준비
3. named unique registry 구성
4. preflight 구현
5. unit/service test 통과
6. `generateApiE2e` 3개 클래스 통과

핵심 확인 사항:

- 성공 파일과 `historyId`를 실제 DB 및 ownership 경계에서 증명한다.
- 실패 시 history/generated_file delta가 0이어야 한다.
- JWT parsing과 Redis blacklist는 Generate E2E 밖에서 검증한다.

### Gate 2: Terraform plan-only scaffold

1. exact runtime mappings 준비
2. deterministic Terraform renderers 구성
3. warning과 golden invariants 정리
4. exact CLI account-free plan 검증

핵심 확인 사항:

- `terraform version -json`이 정확히 고정 버전인지 확인한다.
- no data source, no account lookup, no apply를 유지한다.
- ECS DB secret/runtime credential은 넣지 않는다.

### Gate 3: Redis LOCAL_DEV

1. Redis model/parser 정리
2. renderer/contributor 정리
3. parsing/golden/e2e 테스트 보강

핵심 확인 사항:

- auth field가 없다.
- `localhost` env만 생성한다.
- `generateApiE2e` 클래스 세트는 유지한다.

### Gate 4: 하네스·문서 정합화

1. executable names/pins 확정
2. docs/path/package/roadmap 갱신
3. 문서 인덱스 정리

핵심 확인 사항:

- `docs/plan/`의 문서 역할이 겹치지 않아야 한다.
- 로드맵은 상태, plan은 실행으로 유지한다.

## 5. 파일 단위 작업 전략

- 기본적으로 한 세션에 한 파일을 우선한다.
- 인터페이스와 구현이 함께 바뀌어야 빌드되는 경우에만 최소 파일 세트를 수정한다.
- Controller, Service, Converter, Test가 함께 맞물리는 변경은 하나의 기능 단위로 묶는다.
- 문서만 바꾸는 경우에는 코드 파일을 건드리지 않는다.

예상되는 파일 묶음 예시는 다음과 같다.

- Generate 계약 변경: Controller, Docs, Request DTO, 관련 테스트
- 이력 저장 보강: CommandService, Converter, 테스트
- 문서 정리: roadmap, AGENTS, plan 문서

## 6. 핵심 기술 계약

### 6.1 Generate E2E 경계

- exact Docker Hub multi-arch image `mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9`를 static `MySQLContainer`로 사용한다.
- shared `@DynamicPropertySource`는 datasource URL/username/password와 clean-checkout용 non-secret bootstrap 값을 등록한다.
- `REDIS_PASSWORD`, test-only `JWT_SECRET`, Kakao 관련 값은 context bootstrap 전용이다.
- request에 `Authorization` header를 보내지 않으므로 JWT filter token parsing 및 Redis blacklist lookup은 이 suite의 책임이 아니다.
- E2E 요청은 Spring Security test `user(new CustomUserDetails(persistedMemberDTO))`를 사용하고, `@AuthenticationPrincipal` → controller → ownership 검증 흐름을 실제 실행한다.
- Docker daemon 부재는 skip/pass가 아니라 prerequisite failure로 본다.

### 6.2 Generate 실패 분기

- production `composeCapabilityRegistry`를 사용한다.
- missing renderer와 missing contributor는 각각 전용 E2E로 검증한다.
- failure class는 real HTTP/security/ownership/parsing/generation/history beans를 유지한다.
- failure 시 history/generated_file delta가 0이어야 한다.

### 6.3 Terraform 계약

- runtime-only Dockerfile은 prebuilt `app.jar`만 복사한다.
- Java 17/21 이미지 digest는 고정한다.
- Terraform CLI는 `1.13.5`, AWS provider는 `6.22.0`을 사용한다.
- `skip_credentials_validation`, `skip_requesting_account_id`, `skip_metadata_api_check`를 활성화한다.
- `apply_ready=false`를 명시하고, ECS에 DB 비밀값이나 런타임 자격증명을 넣지 않는다.

### 6.4 Redis 계약

- Redis는 unauthenticated LOCAL_DEV only다.
- auth/password/ACL/TLS는 생성하지 않는다.
- 호스트 앱 env에는 `SPRING_DATA_REDIS_HOST=localhost`와 포트만 둔다.

## 7. 검증 전략

변경마다 아래 순서로 확인한다.

1. 근처 코드와 컨벤션 확인
2. 변경 파일의 책임 분리 확인
3. 관련 테스트 실행
4. 실패/성공 경계 재확인
5. 전체 빌드가 필요한 범위면 `clean build` 실행

권장 검증 항목:

- Generate 관련 단위 테스트
- Controller mapping 테스트
- History 저장 테스트
- Terraform 또는 Redis를 건드릴 때는 해당 전용 테스트

## 8. Acceptance Criteria

- Generate API 계약이 문서와 구현에서 일치한다.
- 생성 결과와 이력 저장이 테스트로 확인된다.
- Swagger와 MockMvc 정리가 끝난다.
- Terraform은 plan-only로 검증된다.
- Redis는 unauthenticated LOCAL_DEV로만 지원한다.
- `docs/plan/`의 문서 역할이 겹치지 않는다.

## 9. 리스크와 주의점

- 요청 범위를 넓히면 작업이 커지기 쉽다.
- Controller에서 비즈니스 로직이 늘어나면 구조가 흐트러질 수 있다.
- Converter에 조회나 조건 분기가 들어가면 규칙이 깨진다.
- 이력 저장과 생성 결과 저장을 따로 다루면 원자성이 약해질 수 있다.
- 문서와 실제 구현이 어긋나면 이후 작업자가 잘못된 기준을 따를 수 있다.
- plan 문서가 여러 개일 때는 인덱스 없이는 탐색 비용이 커진다.

## 10. 완료 기준

- Generate API 계약이 문서와 구현에서 일치한다.
- 생성 결과와 이력 저장이 테스트로 확인된다.
- Swagger와 MockMvc 정리가 끝난다.
- B4, B-TF, Redis, 문서 정합화가 우선순위와 함께 분리된다.

## 11. 후속 작업 메모

후속 세션에서는 다음 순서로 이어가기 좋다.

1. 현재 작업 트리에서 Generate 관련 변경 리뷰
2. B2/B3 테스트 보강
3. Redis 또는 Terraform 같은 확장 기능은 별도 세션으로 분리
4. 문서 갱신은 코드 변경과 분리해 마무리
