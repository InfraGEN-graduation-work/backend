# Architecture & Responsibility Convention

## 목적

현재 백엔드의 구조, 의존 방향, 확장 지점을 빠르게 파악하기 위한 지도다. 새 책임이 어느 도메인과 계층에 속하는지 판단할 때 쓴다. 계층별 세부 규칙은 [controller](./controller_convention.md), [service](./service_convention.md), [dto](./dto_convention.md), [converter](./converter_convention.md), [exception](./exception_convention.md), [testing](./testing_convention.md) 문서를 따르고 여기서 반복하지 않는다.

## 현재 구조

소스 루트는 `com.infragen.infragen`이다.

```text
src/main/java/com/infragen/infragen
├── domain
│   ├── auth           인증·OAuth 흐름
│   ├── member         회원 persistence와 회원 use case
│   ├── project        프로젝트·graph·history·협업자 persistence
│   ├── parsing        graph 검증과 component parsing
│   ├── generation     IaC 출력 형식 선택과 산출물 생성
│   └── collaboration  STOMP operation·snapshot·replay와 project graph materialization
└── global
    ├── apiPayload  공통 응답·예외 응답
    ├── auth        JWT filter와 Spring Security adapter
    ├── config      application infrastructure bean
    ├── controller  health endpoint
    ├── entity      공통 audit Entity
    ├── enums       전역 component 분류
    ├── properties  환경 설정 binding
    └── util        JWT·Redis 공통 utility
```

기본 의존 방향:

```text
Controller → CommandService / QueryService → Repository, Converter, 외부 Client, 협력 Service → Entity / 외부 시스템

Generate
  → ParsingService → ValidateGraphStructure + ComponentParser → ParsingResultDTO
    → IaCGenerationService → IaCGenerator → Renderer / Context / Contributor / Assembler → IaCFileDTO.BundleResDTO

Collaboration
  → STOMP CONNECT/SUBSCRIBE/SEND 인증·project access 검증
    → operation validator + serverVersion 발급
      → ProjectNode·ProjectEdge materialization + operation log → snapshot checkpoint / replay / compaction
```

## 도메인 책임과 협력 관계

- `auth`: 일반·소셜 로그인, 회원가입 위임, JWT 발급, refresh token·blacklist. 협력: `member` Service, `KakaoOAuthClient`, `JwtUtil`, `RedisUtil`
- `member`: 회원 생성·조회와 회원 Entity/Repository. 협력: `auth` Service, `MemberConverter`
- `project`: 프로젝트 graph, history, generated file, 협업자와 초대의 저장·조회·삭제. 협력: `member` 소유권 조회
- `parsing`: raw graph의 component type, port, edge, cycle, 의존 타입 중복 검증과 내부 component 변환
- `generation`: 출력 형식별 generator 선택, IaC 산출물 생성, 생성 history 저장 흐름. 협력: `parsing`, `project`
- `collaboration`: project별 operation 계약, 권한 연결, serverVersion, materialized graph, snapshot/replay. 협력: `project`, `member`, STOMP infrastructure
- `global`: 인증 filter, 공통 응답/예외, Redis·Jackson·RestClient 등 infrastructure. domain 업무 규칙은 소유하지 않는다.

도메인 간 호출은 유스케이스 조정에 필요한 범위로 제한한다.

주요 유스케이스 흐름:

- Project 저장, 조회: `ProjectController` → `ProjectCommandService` / `ProjectQueryService` → project Repository/Converter
- Generate: `GenerationController` → `GenerationCommandService` → `ParsingService` → `IaCGenerationService` → history 저장
- Collaboration operation: `CollaborationOperationMessageController` → `CollaborationOperationCommandService` → version·materialization·operation log → STOMP broadcast
- Collaboration reconnect: `CollaborationSnapshotController` → `CollaborationSnapshotQueryService` → snapshot/replay 또는 materialized graph fallback
- 일반 로그인: `AuthController` → `AuthService` → `MemberQueryService` → `JwtUtil`·`RedisUtil`
- 소셜 로그인: `AuthController` → `AuthService` → `KakaoOAuthClient` → member 조회·생성 → token 발급
- 인증된 요청: `JwtExceptionFilter` → `JwtAuthFilter` → `CustomUserDetailsService` → `SecurityContext`

## 주요 인터페이스와 확장 지점

- `ComponentParser` (`MySQLParser`, `PostgreSQLParser`, `RedisParser`, `SpringBootParser`): component별 node property 검증과 `BaseComponent` 구현체 변환
- `IaCGenerator` (`LocalIaCGenerator`: `DockerComposeIaCGenerator`, `TargetAwareIaCGenerator`: `TerraformIaCGenerator`): `ParsingResultDTO`를 `OutputFormat`별 file bundle로 변환
- `ComposeServiceRenderer` (MySQL, PostgreSQL, Redis): LOCAL_DEV dependency의 Compose service block 생성
- `HostAppEnvContributor` (MySQL, PostgreSQL, Redis): 호스트 실행 Spring Boot의 dependency 연결 정보 생성
- `CloudComposeServiceRenderer` (MySQL, PostgreSQL, Redis): CLOUD_DEPLOY Compose bootstrap의 dependency block 생성, 앱 컨테이너용 타입별 중립 변수와 JDBC 연결 정보 제공. `CloudComposeRenderer`는 이를 `ComponentType` 순서로 모으고 Spring 매핑(단일 DB일 때만 `SPRING_DATASOURCE_*`)만 맡는다.
- `OAuth2UserInfo` (`KakaoUserInfoDTO`): provider별 사용자 응답을 공통 social identity로 제공
- `BaseErrorCode` / `BaseSuccessCode` (domain·general enum): 공통 HTTP status, code, message 계약
- `VolumeComponent` (`MySQLComponent`, `PostgreSQLComponent`, `RedisComponent`): volume 정보를 제공하는 component marker

사용 규칙:

- 구현체 선택이 필요한 parser·generator·renderer 경계에는 기존 interface를 재사용하고 중복 interface를 만들지 않는다.
- Spring이 주입한 구현체 목록은 `getSupportedType()`, `getDependencyType()`, `getOutputFormat()`을 map key로 등록하며, 같은 key의 구현체를 중복 등록하지 않는다.
- 구현체는 자신의 출력·변환 책임만 수행하고 Repository 접근이나 HTTP 응답 생성을 맡지 않는다.
- enum에 component type이 있다는 사실만으로 parser·renderer 지원이 완료된 것으로 판단하지 않는다.

## Parsing과 Generation의 경계

Parsing은 그래프 정합성을 판단하지만 Compose나 Terraform 문법을 생성하지 않는다.

- `ValidateGraphStructure`: node id, component type, edge endpoint, dependency 방향, cycle, 애플리케이션별 같은 타입 의존 중복 검증
- `ComponentParser`: component별 property와 필수값 검증, component DTO 생성
- `ParsingService`: parser registry, port range·중복 검증, 결과 조립
- `ParsingResultConverter`: 생성 결과에서 component를 type/class 기준으로 추출

Generator는 parsing 결과를 재검증하지 않고 출력 형식의 renderer와 assembler를 조정한다.

- LOCAL_DEV `DockerComposeIaCGenerator`: application은 Compose에서 제외하고 dependency만 렌더링한다. application 연결 정보는 `HostAppEnvContributor`가 호스트 `.env`에 추가한다.
- CLOUD_DEPLOY `TerraformIaCGenerator`: AWS·OCI Terraform, runtime Dockerfile, cloud Compose, plan-only warning을 조립한다.
- `ComposeGenerationContext`, `CloudDeployContext`: renderer가 공유할 parsing 결과와 생성 session 상태를 제공한다.
- `CloudDeployFileAssembler`: renderer 결과를 API 응답용 bundle로 감싼다.

## 공통 횡단 경계

- 정상 응답은 `ApiResponse`와 domain SuccessCode, 예외는 `GeneralException`과 domain ErrorCode를 쓰고 HTTP 변환은 `GeneralExceptionAdvice`에 위임한다.
- JWT filter 내부 예외는 `JwtExceptionFilter`, 인증되지 않은 요청은 `AuthenticationEntryPointImpl`이 처리한다.
- `JwtUtil`은 JWT 생성·서명·claim 검증, token lifecycle 정책은 `AuthService`가 맡는다.
- `RedisUtil`은 Redis CRUD·blacklist 저장 동작만 제공하고, key 의미·TTL 정책은 호출 domain이 정한다.
- 순수 parser·validator·converter·generator는 Spring context 없이 테스트할 수 있는 구조를 우선한다.

## 현재 구현에서 주의할 사실

다음은 현재 코드의 사실이며, 목표 계약으로 추정해 바꾸지 않는다.

1. Parsing component DTO, Project 저장 request, `ProjectNode` Entity는 문자열 `nodeId`를 쓰고 `nodeName`은 표시용이다. Project node response는 canvas `nodeId`와 내부 DB `Long id`를 함께 제공하고, edge response endpoint는 문자열 nodeId다.
2. `ComponentType`에는 MongoDB, NGINX, Apache도 있지만 parser·generator는 MySQL, PostgreSQL, Redis, Spring Boot만 구현돼 있다. 애플리케이션의 DATABASE 의존이 둘 이상이면 LOCAL_DEV와 CLOUD_DEPLOY 모두 `SPRING_DATASOURCE_*` 대신 Compose 안내 주석을 만든다.
3. Parsing component DTO(`BaseComponent` 상속)는 HTTP 응답으로 노출되지 않는 내부 전달 객체라 `dto_convention.md`의 `ResDTO` 내부 record 구조를 따르지 않는다.
4. Project graph 수정은 patch merge가 아니라 기존 edge·node를 삭제한 뒤 전체 graph를 교체한다.
5. LOCAL_DEV는 Spring Boot를 호스트에서 실행하고 의존 인프라만 Compose service로 생성한다.
6. CLOUD_DEPLOY Terraform은 plan-only scaffold이며 `terraform apply`, 실제 cloud account 조회, provider credential을 처리하지 않는다.
7. `MemberQueryService`, `MemberCommandService`에는 class-level transaction annotation이 있다. 새 코드는 `service_convention.md`의 method-level 규칙을 따른다.
8. STOMP 협업 controller(`CollaborationOperationMessageController`, `CollaborationSnapshotController`)와 `HealthCheckController`는 Docs interface를 구현하지 않는다. 나머지 REST controller는 `*ControllerDocs`를 구현한다.
9. `GeneralExceptionAdvice`의 `ResponseEntity`는 global 예외 변환 경계의 구현이며, 일반 controller의 정상 응답 규칙과 다르다.

## 새 기능 추가 시 확인 순서

- [ ] 새 책임이 속할 domain과 계층을 정하고 해당 세부 convention 문서를 확인한다.
- [ ] 기존 interface 확장 지점이 있는지 확인한다.
- [ ] domain 간 호출이 유스케이스 조정 범위를 넘지 않는지 확인한다.
- [ ] 현재 구현 사실과 future plan을 구분한다.
- [ ] 테스트 경계와 실행 범위는 `testing_convention.md`를 따른다.
