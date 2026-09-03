# Architecture & Responsibility Convention

## 목적과 문서 경계

이 문서는 현재 구현된 백엔드의 구조, 주요 의존 방향, 인터페이스 확장 지점을 빠르게 파악하기 위한 지도다.
Controller, Service, DTO, Converter, Exception, Test의 세부 규칙은 아래 문서에 정의되어 있으므로 이 문서에서 반복하지 않는다.

- [`controller_convention.md`](./controller_convention.md)
- [`service_convention.md`](./service_convention.md)
- [`dto_convention.md`](./dto_convention.md)
- [`converter_convention.md`](./converter_convention.md)
- [`exception_convention.md`](./exception_convention.md)
- [`testing_convention.md`](./testing_convention.md)

새 기능의 세부 구현 규칙은 위 문서를 따르고, 이 문서는 책임이 어느 계층과 도메인에 속하는지만 판단할 때 사용한다.

## 현재 구조

실제 애플리케이션 소스 루트는 `com.infragen.infragen`이다.

```text
src/main/java/com/infragen/infragen
├── domain
│   ├── auth        인증·OAuth 흐름
│   ├── member      회원 persistence와 회원 use case
│   ├── project     프로젝트·graph·history persistence
│   ├── parsing     graph 검증과 component parsing
│   └── generation  IaC 출력 형식 선택과 산출물 생성
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

기본 의존 방향은 다음과 같다.

```text
Controller
  → CommandService / QueryService
    → Repository, Converter, 외부 Client, 협력 Service
      → Entity / 외부 시스템

Generate
  → ParsingService
    → ValidateGraphStructure + ComponentParser
      → ParsingResultDTO
        → IaCGenerationService
          → IaCGenerator
            → Renderer / Context / Contributor / Assembler
              → IaCFileDTO.BundleResDTO
```

Controller는 HTTP 진입점이고, Service는 유스케이스 흐름과 transaction을 조정한다. Repository는 persistence query,
Converter는 표현 변환, Entity는 저장 상태와 domain method를 담당한다. 이 세부 책임은 각 전용 convention 문서에서 관리한다.

## 도메인 책임과 협력 관계

| 도메인 | 현재 책임 | 주요 협력 관계 |
| --- | --- | --- |
| `auth` | 일반·소셜 로그인, 회원가입 위임, JWT 발급, refresh token·blacklist 처리 | `member` Service, `KakaoOAuthClient`, `JwtUtil`, `RedisUtil` |
| `member` | 회원 생성·조회와 회원 Entity/Repository 관리 | `auth` Service, `MemberConverter` |
| `project` | 프로젝트 graph와 history·generated file의 저장·조회·삭제 | `member` 소유권 조회, project Repository/Converter |
| `parsing` | raw graph의 component type·port·edge·cycle 검증과 내부 component 변환 | `ComponentParser`, `ValidateGraphStructure` |
| `generation` | 출력 형식별 generator 선택, IaC 산출물 생성, 생성 history 저장 흐름 | `parsing`, `project`, renderer 계열 |
| `global` | 인증 filter, 공통 응답/예외, Redis/Jackson/RestClient 등 infrastructure 제공 | 모든 domain의 횡단 관심사 |

현재 주요 유스케이스 흐름:

- Project 저장: `ProjectController` → `ProjectCommandService` → project Repository/Converter
- Project 조회: `ProjectController` → `ProjectQueryService` → project Repository/Converter
- Generate: `GenerationController` → `GenerationCommandService` → `ParsingService` → `IaCGenerationService` → history 저장
- 일반 로그인: `AuthController` → `AuthService` → `MemberQueryService` → `JwtUtil`·`RedisUtil`
- 소셜 로그인: `AuthController` → `AuthService` → `KakaoOAuthClient` → member 조회·생성 → token 발급
- 인증된 요청: `JwtExceptionFilter` → `JwtAuthFilter` → `CustomUserDetailsService` → `SecurityContext`

도메인 간 호출은 유스케이스 조정에 필요한 범위로 제한한다. global 계층은 domain 업무 규칙을 소유하지 않는다.

## 주요 인터페이스와 확장 지점

| 인터페이스 | 현재 구현체 | 확장 책임 |
| --- | --- | --- |
| `ComponentParser` | `MySQLParser`, `RedisParser`, `SpringBootParser` | component별 node property를 검증하고 `BaseComponent` 구현체로 변환 |
| `IaCGenerator` | `DockerComposeIaCGenerator`, `TerraformIaCGenerator` | `ParsingResultDTO`를 `OutputFormat`별 file bundle로 변환 |
| `ComposeServiceRenderer` | MySQL·Redis renderer | LOCAL_DEV dependency의 Compose service block 생성 |
| `HostAppEnvContributor` | MySQL·Redis contributor | 호스트 실행 Spring Boot의 dependency 연결 정보 생성 |
| `CloudComposeServiceRenderer` | `MysqlCloudComposeServiceRenderer` | CLOUD_DEPLOY Compose bootstrap의 dependency block 생성 |
| `OAuth2UserInfo` | `KakaoUserInfoDTO` | provider별 사용자 응답을 공통 social identity로 제공 |
| `BaseErrorCode` / `BaseSuccessCode` | domain·general enum | 공통 HTTP status, code, message 계약 제공 |
| `VolumeComponent` | `MySQLComponent`, `RedisComponent` | volume 정보를 제공하는 component marker |

### Interface 사용 규칙

- 구현체 선택이 필요한 parser·generator·renderer 경계에는 기존 interface를 재사용한다.
- Spring이 주입한 구현체 목록은 `getSupportedType()` 또는 `getOutputFormat()`을 map key로 등록한다.
- 같은 key를 반환하는 구현체를 중복 등록하지 않는다.
- interface 구현체는 자신의 출력·변환 책임만 수행하고, Repository 접근이나 HTTP 응답 생성을 맡지 않는다.
- enum에 component type이 있다는 사실만으로 parser·renderer 지원이 완료된 것으로 판단하지 않는다.

## Parsing과 Generation의 책임 경계

### Parsing

`ParsingService`는 request graph를 내부 `ParsingResultDTO`로 바꾼다.

- `ValidateGraphStructure`: node id, component type, edge endpoint, dependency 방향, cycle 검증
- `ComponentParser`: component별 property와 필수값 검증 및 component DTO 생성
- `ParsingService`: parser registry, port range·중복 검증, 결과 조립
- `ParsingResultConverter`: 생성 결과에서 component를 type/class 기준으로 추출

Parsing은 그래프의 정합성을 판단하지만 Docker Compose나 Terraform 문법을 생성하지 않는다.

### Generation

`IaCGenerationService`는 `OutputFormat`에 맞는 `IaCGenerator`를 선택한다. Generator는 parsing 결과를 재검증하지 않고
출력 형식의 renderer와 assembler를 조정한다.

- LOCAL_DEV의 `DockerComposeIaCGenerator`는 application을 Compose service에서 제외하고 dependency만 렌더링한다.
- LOCAL_DEV application 연결 정보는 `HostAppEnvContributor`가 호스트 `.env`에 추가한다.
- CLOUD_DEPLOY의 `TerraformIaCGenerator`는 AWS·OCI Terraform, runtime Dockerfile, cloud Compose, plan-only warning을 조립한다.
- `CloudDeployContext`와 `ComposeGenerationContext`는 renderer가 공유할 parsing 결과와 생성 session 상태를 제공한다.
- `CloudDeployFileAssembler`는 renderer 결과를 API 응답용 bundle로 감싼다.

## 공통 횡단 경계

- 정상 API 응답은 `ApiResponse`와 domain SuccessCode를 사용한다.
- domain 예외는 `GeneralException`과 domain ErrorCode를 사용하며, HTTP 변환은 `GeneralExceptionAdvice`에 위임한다.
- JWT filter 내부 예외는 `JwtExceptionFilter`, 인증되지 않은 요청은 `AuthenticationEntryPointImpl`이 처리한다.
- `JwtUtil`은 JWT 생성·서명·claim 검증을 담당하고, token lifecycle 정책은 `AuthService`가 담당한다.
- `RedisUtil`은 Redis CRUD·blacklist 저장 동작을 제공하고, key 의미·TTL 정책은 호출 domain이 결정한다.
- `BaseEntity`는 공통 `createdAt`·`updatedAt` audit field를 제공한다.
- MySQL·Redis가 필요한 integration test는 문서화된 로컬 Docker 서비스 또는 명시적으로 구성한 container-based 환경을 사용하며 H2를 전제로 하지 않는다.
- 순수 parser·validator·converter·generator는 Spring context 없이 테스트할 수 있는 구조를 우선한다.

세부 응답, 예외, transaction, 테스트 규칙은 각각의 전용 convention 문서를 우선한다. 이 문서에서 다시 정의하지 않는다.

## 현재 구현에서 주의할 사실

다음은 현재 코드의 사실이며, 목표 계약으로 추정해 변경하지 않는다.

1. Parsing component DTO와 Project 저장 request, `ProjectNode` Entity는 문자열 `nodeId`를 사용한다.
   `nodeName`은 표시용 값으로 분리되어 있으며, `ProjectEdgeConverter`는 `sourceNodeId`·`targetNodeId`로 map을 조회한다.
   Project node response는 canvas `nodeId`와 내부 DB `Long id`를 함께 제공하고, edge response의 endpoint는 문자열 nodeId를 반환한다.
2. `ComponentType`에는 PostgreSQL, MongoDB, NGINX, Apache가 있지만 현재 parser·generator 구현은 MySQL·Redis·Spring Boot 중심이다.
3. Project graph 수정은 patch merge가 아니라 기존 edge·node를 삭제한 뒤 전체 graph를 교체하는 방식이다.
4. LOCAL_DEV는 Spring Boot를 호스트에서 실행하고 MySQL·Redis 같은 dependency만 Compose service로 생성한다.
5. CLOUD_DEPLOY Terraform은 plan-only scaffold이며 `terraform apply`, 실제 cloud account 조회, provider credential을 처리하지 않는다.
6. 현재 `MemberCommandService`, `ProjectQueryService`, `ProjectHistoryQueryService`에는 class-level transaction annotation이 존재한다.
   새 코드는 `service_convention.md`의 method-level transaction 규칙을 따른다.
7. `AuthController`는 현재 Docs interface를 구현하지 않지만, Generation·Member·Project Controller는 `*ControllerDocs`를 구현한다.
8. `GeneralExceptionAdvice`의 `ResponseEntity`는 global 예외 변환 경계의 구현이며, 일반 Controller의 정상 응답 규칙과 다르다.

## 새 기능 추가 시 확인 순서

- [ ] 새 책임이 속할 domain과 계층을 정한다.
- [ ] 해당 세부 convention 문서를 먼저 확인한다.
- [ ] 기존 interface 확장 지점이 있는지 확인하고 중복 interface를 만들지 않는다.
- [ ] domain 간 호출이 유스케이스 조정 범위를 넘지 않는지 확인한다.
- [ ] 현재 구현 사실과 future plan을 구분한다.
- [ ] 관련 unit/web/integration test 경계를 선택한다.
- [ ] 기본값으로 Docker·외부 인프라가 필요 없는 관련 focused test와 `git diff --check`를 실행한다.
- [ ] 전체 `./gradlew test` 또는 외부 인프라 의존 테스트는 사용자 명시 요청이 있을 때만 실행한다.
