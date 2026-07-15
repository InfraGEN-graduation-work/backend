# InfraGEN 백엔드 로드맵 v1 - 아카이브

> 이 문서는 현재까지의 상태와 진행 이력을 보관하는 v1 아카이브다.
>
> 최종 수정: 2026-07-05
> 다음 버전: [backend-roadmap-v2.md](./backend-roadmap-v2.md)
> 상세 실행 계획: [backend-implementation-plan.md](../plan/backend-implementation-plan.md)
> 인덱스: [README.md](./README.md)

---

## 1. 진행 상황 (한눈에)

| Phase | 내용 | 담당 | 상태 | 브랜치 |
|-------|------|------|------|--------|
| B0 | Generate API 골격 · 패키지 분리 · 확장성 리팩터 | 공통 | **`done`** | `feat/#16-generate-api-setup` |
| P0 | Spring Boot · MySQL **정합성 검증** (`parsing/**`) | **황상임** | **`mostly_done`** | — |
| B1 | Compose 생성 골격 · 의존 서비스 renderer · 오케스트레이터 | **최유성** | **`done`** | `feat/#N-iac-generation-service` |
| **B1.5** | **LOCAL_DEV 정합** — 앱 compose 제거 · localhost env · golden 갱신 | **최유성** | **`done`** | `feat/#19-code-generation` |
| **B2** | `GeneratedFile.content` · 이력 원자 저장 | **최유성** | **`done`** *(커밋 전)* | `feat/#19-code-generation` |
| B3 | Swagger 문서 · MockMvc 통합 테스트 | 공통 | `in_progress` *(Swagger 수동 OK · 자동 E2E 미완료)* | `feat/#19-code-generation` |
| B4 | ZIP 압축 스트림 다운로드 (선택) | **최유성** | `pending` | — |
| **B-TF** | **CLOUD_DEPLOY** — Dockerfile + Terraform (RDS/ECS/ECR 등) | **최유성** | `pending` | — |
| F* | 프론트 연동 | 프론트 | `deferred` | — |

**현재 포커스 (기록 시점)**: **Generate API 계약 정리 · 변경분 커밋 준비** — body `projectId` 제거 반영 · Swagger 수동 검증 완료 · 자동 E2E 테스트 범위 결정

> **생성 목표 확정 (2026-06-24)**: 로컬은 **의존 인프라만 compose**, 앱은 **호스트 실행 + `.env`**. 클라우드는 **Dockerfile + Terraform**. §생성 목표 정의 참고.  
> **B1.5 (2026-06-30)**: **done** — LOCAL_DEV 오케스트레이터 · golden test · 레거시 renderer/contributor **삭제** (`de72acc` 등).  
> **B2 진행 (2026-07-03)**: B2-1~6 **done** — `GeneratedFile.content` LONGTEXT · 응답 DTO `content` · `saveGeneratedHistory` · `GenerationCommandService` history 저장 연동 · `GeneratedFileConverter` · `saveGeneratedHistory` 테스트. 변경분은 **커밋 전**.  
> **B3 진행 (2026-07-05)**: `ParsingControllerDocs` Swagger 문서와 `ParsingControllerWebTest`가 일부 구현됨. Swagger 수동 호출은 성공(`historyId` 반환 확인). 정식 자동 E2E 테스트 범위는 추가 정리 필요.  
> **API 계약 변경 (2026-07-05)**: Generate 요청 body에서 `projectId` 제거. 프로젝트 식별자는 path variable `projectId`만 사용.  
> **DB 배포**: OCI Compute Docker MySQL (RDS 미사용) — `content`는 DB `LONGTEXT` 저장 (B2).

**작업 원칙**: [AGENTS.md](../../AGENTS.md) · [workflow.md](../../.codex/rules/workflow.md) — 기본은 채팅당 **파일 1개** · 프론트 수정 금지

---

## 2. 백엔드 역할 분담 (Generate 파이프라인)

| 담당 | 패키지 | 책임 |
|------|--------|------|
| **황상임** | `domain/parsing/**` | 노드·그래프 정합성 검증, `ParsingException` / `ParsingErrorCode`, 파서·검증 테스트 |
| **최유성** | `domain/generation/**` (+ B2 `project` 이력) | 검증된 `ParsingResultDTO` → IaC 파일 생성, (선택) ZIP 스트림, 이력 저장 |

### 2.1 생성 레이어 계약 (B1+)

- **입력**: `ParsingService.parsing()`이 반환한 `ParsingResultDTO`만 사용 (이미 검증·파싱 완료).
- **`DockerComposeIaCGenerator`는 필수 속성을 재검증하지 않음** — 누락·형식 오류는 `parsing` 단계에서 `ParsingException`으로 처리.
- 생성 실패는 `IaCGenerationException` (`GENERATION400_x`)만 사용.
- 검증 규칙 추가·보강은 `XxxParser` / `ValidateGraphStructure` / `ParsingService`만 수정 (generation 수정 불필요).

### 2.2 미지원·부분 지원 타입 (parsing vs generation)

| 상황 | 동작 |
|------|------|
| `ComponentType` enum만 있고 **Parser 없음** | `ParsingService` → `PARSING400_11` (**Generate까지 도달 안 함**) |
| Parser 있음, **의존 서비스 Renderer 없음** (DATABASE/CACHE 등) | parsing 통과 → 오케스트레이터 **warn + 해당 compose 블록 생략** |
| **APPLICATION** (Spring Boot 등) | compose `services:` **생성 안 함** (LOCAL_DEV). 엣지 기반 **호스트용 `.env`** 만 `HostAppEnvContributor`로 생성 |
| **end-to-end 신규 의존 타입** | Parser + Component + **`XxxComposeServiceRenderer`** (+ 앱 연결 시 **`XxxHostAppEnvContributor`**) |

---

## 3. 생성 목표 정의 — LOCAL_DEV vs CLOUD_DEPLOY

동일한 `ParsingResultDTO`(캔버스 그래프)에서 **출력 목적**에 따라 산출물이 달라진다.

### 로컬 개발 — `OutputFormat.DOCKER_COMPOSE` (= **LOCAL_DEV**)

실무 패턴: **앱은 호스트에서 실행** (`./gradlew bootRun`, IDE) · **DB·Redis 등만 Docker**

| 산출물 | 내용 |
|--------|------|
| `docker-compose.yml` | **의존 인프라만** (`ComponentCategory`: DATABASE, CACHE, WEB_SERVER …). **APPLICATION은 `services:`에 넣지 않음** |
| `.env` | **호스트에서 돌릴 앱**용 연결 정보. JDBC는 `localhost` + 호스트 매핑 포트 (컨테이너 DNS `mysql` 아님) |

**Spring Boot 노드 on 캔버스** — 제거하지 않음:

- 파싱·검증 (`name`, `javaVersion`, 포트 …)
- 그래프 엣지 (MySQL → Spring 방향)
- **앱용 env 키 생성** (`SPRING_DATASOURCE_*` 등)

**로컬에서 생성하지 않는 것**: Dockerfile, 앱 컨테이너, `eclipse-temurin` JDK-only 이미지, compose `build:`

### 클라우드 배포 — `OutputFormat.TERRAFORM` (= **CLOUD_DEPLOY**, Phase **B-TF**)

| 산출물 | 내용 |
|--------|------|
| `Dockerfile` | Spring Boot JAR 멀티스테이지 빌드 명세 |
| `*.tf` | RDS, ECS/EC2, ECR, SG, 변수 등 |
| (문서/CI 스크립트) | `docker build` → 레지스트리 push → TF가 `image` URI 참조 |

MySQL 노드는 로컬에선 **컨테이너**, 클라우드에선 **RDS 등 관리형**으로 renderer가 갈라진다.

### 채택하지 않는 방식

| 구분 | 비채택 |
|------|--------|
| 로컬 | 앱 전체를 compose로 띄우기, 실행 불가능한 JDK-only Spring 서비스 블록 |
| 클라우드 | 프로덕션 배포의 **주 경로**로 docker-compose `build:` 사용 (Dockerfile + 레지스트리 + TF가 정석) |
| 공통 | 프론트 TS 단일 파일 복붙, `instanceof` 단일 오케스트레이터 |

### 레이어 관계 (목표 구조)

```
ParsingResultDTO
    ↓
IaCGenerationService (OutputFormat)
    ├─ LOCAL_DEV  → DockerComposeIaCGenerator
    │                 ├─ ComposeServiceRenderer     (의존 인프라만: MySQL, Redis …)
    │                 └─ HostAppEnvContributor      (APPLICATION용 .env, localhost JDBC)
    └─ CLOUD_DEPLOY → TerraformIaCGenerator (B-TF)
                      ├─ DockerfileGenerator        (APPLICATION)
                      └─ TerraformResourceRenderer  (RDS, ECS …)
```

---

## 4. B0 완료 체크리스트 (API 골격)

- [x] `POST /api/v1/projects/{projectId}/generate` (JWT 필수)
- [x] `ParsingController` → `GenerationCommandService` 연동
- [x] `GenerationCommandService` — 소유권 검증 + parsing → IaC 오케스트레이션
- [x] 확장성 리팩터 (ComponentType 통합, `ParsingResultDTO.components`, `IaCGenerator` 전략)
- [x] `IaCGenerationException` + `GENERATION400_1` (미지원 OutputFormat)
- [x] 패키지 분리 (`parser/` · `validator/` · `generator/` · `converter/`)
- [x] Swagger 수동 검증 (로컬 Swagger에서 Generate 성공 · `historyId` 반환 확인)

> B0는 **파이프라인 골격** 완료. 노드별 필수 속성 검증 상세는 **P0** 참고.

---

## 5. P0 — Parsing 정합성 검증 현황 (황상임)

프론트 `validation.ts`(git: `a6acb3c`) 기준과 백엔드 구현을 대조한 결과입니다.  
**요약: 그래프·포트·Spring Boot 핵심 필드는 대부분 구현됨. MySQL 필수 env 검증과 파서 단위 테스트가 보강됨.**

### 공통 (`ParsingService` · `ValidateGraphStructure`)

| 검증 항목 | 에러 코드 | 구현 | 테스트 |
|-----------|-----------|------|--------|
| 노드 없음 | `PARSING400_1` | ✅ `ParsingService` | ✅ `ParsingServiceTest` |
| componentType 누락 | `PARSING400_3` | ✅ | — |
| 포트 중복 | `PARSING400_4` | ✅ | ✅ |
| 포트 범위 (1024~65535) | `PARSING400_5` | ✅ | — |
| 지원하지 않는 타입 | `PARSING400_11` | ✅ | — |
| nodeId 누락 | `PARSING400_14` | ✅ `ValidateGraphStructure` | ✅ |
| nodeId 중복 | `PARSING400_15` | ✅ | ✅ |
| 엣지가 없는 노드 참조 | `PARSING400_8` | ✅ | ✅ |
| 엣지 source/target 누락 | `PARSING400_16` | ✅ | ✅ |
| 순환 참조 | `PARSING400_9` | ✅ | ✅ (서비스·validator 양쪽) |
| 엣지 방향 오류 (MySQL→Spring Boot) | `PARSING400_10` | ✅ | ✅ |

### Spring Boot (`SpringBootParser`)

| 검증 항목 | 에러 코드 | 구현 | 테스트 | 비고 |
|-----------|-----------|------|--------|------|
| 서비스 이름(`name`) 누락 | `PARSING400_12` | ✅ | — | |
| Java 버전 누락 | `PARSING400_13` | ✅ | — | |
| `containerName` | — | ✅ optional | — | 빈 문자열 허용 |
| `port` | `PARSING400_4/5` | ✅ `ParsingService` | ✅ | |

### MySQL (`MySQLParser`)

| 검증 항목 | 에러 코드 | 구현 | 테스트 | 비고 |
|-----------|-----------|------|--------|------|
| `env.databaseName` 누락·형식 | `PARSING400_6` | ✅ | — | blank도 동일 코드 (누락 전용 코드 없음) |
| `env.rootPassword` 8자 미만 | `PARSING400_7` | ✅ | — | null·짧은 값 거부 |
| `env.username` 누락 | `PARSING400_18` | ✅ | ✅ `MySQLParserTest` | 누락 시 전용 코드 반환 |
| `env.userPassword` 누락 | `PARSING400_19` | ✅ | ✅ `MySQLParserTest` | 누락 시 전용 코드 반환 |
| `imageVersion` 누락 | `PARSING400_17` | ✅ | ✅ `MySQLParserTest` | 빈 문자열도 동일 코드 |
| `containerName` / `volumeName` | — | ✅ optional | — | null → `""` |

### P0 남은 작업 (황상임)

- [ ] `SpringBootParserTest` 추가 또는 `ParsingServiceTest` 보강 — `400_12~13` 단위 테스트
- [ ] (선택) 누락 vs 형식 오류 메시지 분리 (`MISSING_*` vs `INVALID_*`)

### P0 완료된 기반 (B0에 포함됐던 항목)

- [x] `SpringBootComponent` — DTO silent default 제거 (검증은 파서 담당)
- [x] `SpringBootParser` — `name`, `javaVersion` 검증 (`400_12`, `400_13`)
- [x] `MySQLParser` — `databaseName` 형식·`rootPassword` 길이 (`400_6`, `400_7`), null 방어
- [x] 그래프 검증 보강 (`PARSING400_14~16`)
- [x] `./gradlew test` — `ValidateGraphStructureTest`, `ParsingServiceTest` 통과

---

## 6. 다음 채팅 — 바로 복붙 (기록용)

```
@docs/backend-roadmap-v1.md

백엔드만 / 프론트 수정 금지 / parsing/** 수정 금지 / 한 채팅 한 파일 원칙 유지
Phase: Generate API 계약 정리 + B2/B3 변경분 커밋 준비
대상: backend 작업트리 변경분 리뷰

작업:
- Generate 요청 body `projectId` 제거 변경분 확인
- B2 변경분이 커밋 가능한지 리뷰
- GenerationCommandService / ProjectHistoryCommandService / GeneratedFileConverter 중심 검토
- ProjectHistoryCommandServiceTest의 saveGeneratedHistory 검증 확인
- B3 선행 구현(ParsingControllerDocs, ParsingControllerWebTest, Swagger 수동 검증) 범위 분리 여부 결정
- ./gradlew test 재실행

참조: roadmap §Phase B2 · §Phase B3 · backend git status
```

> **워킹 트리**: B2-1~6, B3 일부 구현, Generate 요청 계약 변경이 섞여 있으며 **커밋 전**. 리뷰 전 `backend/`에서 `git status` 확인.

권장 순서: **Generate 계약 변경 + B2 변경분 리뷰/커밋** → B3 문서·MockMvc 정리 → (선택) B4 → **B-TF**

---

## 목표 API

```
POST /api/v1/projects/{projectId}/generate
Authorization: Bearer {JWT}
Body: ParsingReqDTO { nodes[], edges[] }
```

> `projectId`는 path variable만 사용합니다. 요청 body에는 포함하지 않습니다.

**성공 응답 (`GenerateResDTO.GenerateResultResDTO`)**

```json
{
  "isSuccess": true,
  "code": "GENERATION200_1",
  "message": "인프라 코드 생성에 성공했습니다.",
  "result": {
    "files": [
      { "fileName": "docker-compose.yml", "content": "..." },
      { "fileName": ".env", "content": "..." }
    ],
    "historyId": 42
  }
}
```

> `historyId`는 생성 결과를 저장한 `ProjectHistory` ID입니다.

---

## 클래스 흐름 (Generate API)

```
ParsingController
  └── GenerationCommandService          (generation/service/command/)
        ├── ProjectQueryService         getOwnedProject — 소유권 검증
        ├── ParsingService              (parsing/service/)
        │     └── → ParsingResultDTO.components
        ├── IaCGenerationService        → IaCFileDTO.BundleResDTO
        └── ProjectHistoryCommandService  saveGeneratedHistory
              └── GeneratedFileConverter.toEntityList → GeneratedFile (LONGTEXT content)
```

### 주요 클래스 위치

| 클래스 | 패키지/경로 |
|--------|-------------|
| `ComponentType` | `global/enums/ComponentType.java` (단일 enum) |
| `ParsingReqDTO`, `NodeDTO`, `EdgeDTO` | `parsing/dto/request/` |
| `ParsingResultDTO`, `BaseComponent`, `SpringBootComponent`, `MySQLComponent` | `parsing/dto/response/` |
| `ParsingResultConverter` | `parsing/converter/ParsingResultConverter.java` |
| `ComponentParser` | `parsing/parser/ComponentParser.java` |
| `ParsingService` | `parsing/service/ParsingService.java` |
| `ValidateGraphStructure` | `parsing/validator/ValidateGraphStructure.java` |
| `SpringBootParser`, `MySQLParser` | `parsing/parser/` |
| `ParsingController` | `parsing/controller/ParsingController.java` |
| `GenerationCommandService` | `generation/service/command/` |
| `IaCGenerator`, `DockerComposeIaCGenerator` | `generation/generator/` |
| `ComposeServiceRenderer`, `ComposeGenerationContext` | `generation/generator/compose/` |
| `MysqlComposeServiceRenderer` 등 | `generation/generator/compose/` (의존 인프라 `@Component`) |
| `HostAppEnvContributor`, `MysqlHostAppEnvContributor` | `generation/generator/compose/` (LOCAL_DEV `.env`) |
| `DockerComposeIaCGeneratorTest` | `generation/generator/` (B1.5-5 golden **done**) |
| `GeneratedFile`, `ProjectHistory` | `project/entity/` |
| `GeneratedFileConverter` | `project/converter/` (`toEntity` / `toFileInfoResDTO` — **B2**) |
| `ProjectQueryService.getOwnedProject` | `project/service/query/` — 소유권 검증 공통 |
| `ProjectHistoryCommandService.saveGeneratedHistory` | `project/service/command/` (**B2-3 done**) |
| `DockerfileGenerator`, `TerraformIaCGenerator` | `generation/generator/` (B-TF) |
| `IaCGenerationService` | `generation/service/IaCGenerationService.java` |
| `GenerateResDTO`, `IaCFileDTO` | `generation/dto/response/` |
| `OutputFormat` | `generation/enums/OutputFormat.java` |
| `IaCGenerationException`, `IaCGenerationErrorCode` | `generation/exception/` |
| `GenerationSuccessCode` | `generation/exception/code/success/` |

### 확장 시 추가 방법

| 확장 | 작업 |
|------|------|
| Redis, Nginx (의존 인프라) | `ComponentType` + `XxxComponent` + `XxxParser` + **`XxxComposeServiceRenderer`** |
| PostgreSQL (로컬) | `PostgreSQLParser` + **`PostgresComposeServiceRenderer`** + **`PostgresHostAppEnvContributor`** |
| Spring Boot (로컬) | Parser 유지 · compose renderer **없음** · **`HostAppEnvContributor`** 로 `.env`만 |
| Terraform / 클라우드 | **`TerraformIaCGenerator`** + **`DockerfileGenerator`** + **`XxxTerraformRenderer`** (B-TF) |
| Docker Compose 의존 블록 | **`ComposeServiceRenderer` 추가** — 오케스트레이터 수정 최소화 |
| 새 검증 규칙 | 해당 `XxxParser` 또는 `ValidateGraphStructure` (`parsing/**`만) |

### 신규 컴포넌트 타입 체크리스트

| # | 레이어 | LOCAL_DEV | CLOUD_DEPLOY (B-TF) |
|---|--------|-----------|---------------------|
| 1 | `ComponentType` + `XxxParser` | ✅ | ✅ |
| 2 | 의존 인프라 (DB, CACHE …) | **`XxxComposeServiceRenderer`** | **`XxxTerraformRenderer`** (예: RDS) |
| 3 | APPLICATION (Spring Boot …) | **`XxxHostAppEnvContributor`** (`.env`) | **`DockerfileGenerator`** + TF compute |
| 4 | 앱↔DB 엣지 | contributor가 **localhost** JDBC | TF 변수·시크릿·RDS endpoint |
| 5 | 테스트 | golden / 파싱 테스트 | 권장 |

`ParsingResultDTO`는 타입별 리스트(`springBoot[]`)가 **아닌** `components[]` 통합 리스트 — 새 노드 추가 시 DTO 필드 추가 불필요.

---

## 확장성 아키텍처

### Parsing 레이어 (B0 완료)

| 레이어 | 패턴 |
|--------|------|
| 컴포넌트 타입 | `global.enums.ComponentType` (+ `ComponentCategory`, `startupPriority`) |
| 파싱 결과 | `ParsingResultDTO.components` |
| 파서 | `ComponentParser` 전략 |
| 그래프 검증 | `ValidateGraphStructure` — `startupPriority` 기반 엣지 방향·순환 |

### Generation 레이어 (B1+ — **전략 패턴**)

| 레이어 | 패턴 | 비고 |
|--------|------|------|
| 출력 포맷 라우팅 | `IaCGenerator` + `IaCGenerationService` | B0 완료 |
| LOCAL compose 오케스트레이션 | `DockerComposeIaCGenerator` | **의존 서비스만** compose 조립 |
| 의존 서비스 YAML | `ComposeServiceRenderer` | MySQL, Redis … (**APPLICATION 제외**) |
| 호스트 앱 env | `HostAppEnvContributor` | B1.5 **done** · B1.5-3 오케스트레이터 연동 **done** |
| 공유 상태 | `ComposeGenerationContext` | `envVars`, `edges`, `findIncomingDependencies` |
| CLOUD (B-TF) | `TerraformIaCGenerator` + `DockerfileGenerator` | 별도 Phase |
| API 응답 | `GenerateResDTO.files[]` | LOCAL: compose + `.env` |

### 채택하지 않는 방식 (구현)

- `DockerComposeIaCGenerator` 안에 `instanceof` 분기만으로 전체 로직 구현
- 프론트 TS를 한 파일에 그대로 복사
- **LOCAL_DEV**에서 Spring Boot `services:` / JDK-only 이미지 생성
- **CLOUD_DEPLOY**에서 compose `build:`를 프로덕션 배포의 주 경로로 사용

### 다중 DB·다중 앱 시나리오

| 시나리오 | LOCAL_DEV 스코프 | 처리 방식 |
|----------|------------------|-------------|
| Spring Boot + MySQL (1:1) | **✅ 완료 기준** | compose: **MySQL만** · `.env`: `SPRING_DATASOURCE_*` (**localhost** JDBC) |
| Spring Boot + PostgreSQL | B1.5 이후 | `PostgresComposeServiceRenderer` + `PostgresHostAppEnvContributor` |
| MySQL + PostgreSQL → Spring 1개 | B1.5 이후 | compose에 DB 2개 · env contributor 각각 |
| Spring Boot 앱 2개 + DB 1개 | **범위 밖** | env 키 prefix 규칙 필요 |
| Redis → Spring (CACHE) | B1.5 이후 | `RedisComposeServiceRenderer` + cache env contributor |
| Parser 없는 enum | — | `PARSING400_11` |
| 의존 Renderer 없음 | — | warn + compose 블록 생략 |

> **다중 DB env 키 (호스트 `.env`)**: 1번째 DB → `SPRING_DATASOURCE_*` (primary). 추가 DB → `…_<SERVICE_NAME>` 접미사 (B1.5 이후).

> **LOCAL_DEV 명시 스코프**: MySQL **1개** + Spring Boot **1개** + 엣지 **1개**. compose에는 **MySQL만**, `.env`에 앱 연결 정보.

---

## 주요 에러 코드

### Parsing (`ParsingException`) — `parsing/**` 담당

| 코드 | 상황 |
|------|------|
| `PARSING400_1` | 노드 없음 |
| `PARSING400_3` | componentType 누락 |
| `PARSING400_4` | 포트 중복 |
| `PARSING400_5` | 포트 범위 오류 (1024~65535) |
| `PARSING400_6` | DB 이름 누락·형식 오류 (영문·숫자·`_`만) |
| `PARSING400_7` | MySQL root 비밀번호 8자 미만 |
| `PARSING400_8` | 연결선이 존재하지 않는 노드 참조 |
| `PARSING400_9` | 순환 참조 |
| `PARSING400_10` | 엣지 방향 오류 (먼저 실행할 노드 → 나중 실행 노드 순서 위반) |
| `PARSING400_11` | 지원하지 않는 컴포넌트 타입 |
| `PARSING400_12` | Spring Boot 서비스 이름 누락 |
| `PARSING400_13` | Java 버전 누락 |
| `PARSING400_14` | nodeId 누락 |
| `PARSING400_15` | nodeId 중복 |
| `PARSING400_16` | 연결선 source/target 누락 |

### Generation (`IaCGenerationException`)

| 코드 | 상황 |
|------|------|
| `GENERATION400_1` | 지원하지 않는 `OutputFormat` (예: TERRAFORM 미구현 시) |

### Project (`ProjectException`)

| 코드 | 상황 |
|------|------|
| `PROJECT404_1` | 프로젝트 없음 / 소유권 없음 |

---

## Phase B1 — Compose 골격 + 오케스트레이터 (최유성)

**입력 계약**: `ParsingResultDTO`는 P0 검증 통과. generation에서 `ParsingException` throw 금지.

**LOCAL_DEV 스코프**: MySQL 1 + Spring Boot 1 + 엣지 1. **산출물 정의는 §생성 목표 정의** — golden은 **B1.5 이후** 확정 (프론트 `a6acb3c` 전체 스택 compose는 **더 이상 목표 아님**).

**코드 현황**: B1·B1.5 **done** · LOCAL_DEV golden 확정 · 레거시 compose renderer/contributor **삭제됨**

### B1 단계별 작업 (채팅당 파일 1개)

| 단계 | 대상 파일 | 작업 | 상태 |
|------|-----------|------|------|
| **B1-1** | `ComposeServiceRenderer.java` + `ComposeGenerationContext.java` | 인터페이스 · context · `findIncomingDependencies` | **done** |
| **B1-1b** | `ComposeYamlSupport.java` | `toServiceName`, `escapeEnvValue`, `formatEnvFile` | **done** |
| **B1-2** | `MysqlComposeServiceRenderer.java` | MySQL `services:` + `MYSQL_*` envVars | **done** |
| **B1-5** | `DockerComposeIaCGenerator.java` | renderer/contributor Map · APPLICATION 제외 · `.env` | **done** |
| **B1-6** | `DockerComposeIaCGeneratorTest.java` | LOCAL_DEV golden | **done** (B1.5-5) |

> ~~B1-3/B1-4~~ (`DatabaseEnvContributor`, `SpringBootComposeServiceRenderer`) — **삭제됨** (B1.5 레거시 정리).

### B1.5 — LOCAL_DEV 정합 (B1-5 직후 · 최유성)

프론트 프로토타입(`a6acb3c`)과 달리, **로컬 실무 패턴**에 맞게 출력을 확정한다.

| 단계 | 대상 | 작업 | 상태 |
|------|------|------|------|
| B1.5-1 | `HostAppEnvContributor.java` | 호스트 앱 `.env` 전용 인터페이스 (`DatabaseEnvContributor` 대체) | **done** |
| B1.5-2 | `MysqlHostAppEnvContributor.java` | JDBC `localhost:{mappedPort}` · `MYSQL_HOST=localhost` | **done** |
| B1.5-3 | `DockerComposeIaCGenerator.java` | APPLICATION renderer **제외** · `HostAppEnvContributor` 연동 | **done** |
| B1.5-4 | 레거시 renderer/contributor 3파일 | **삭제** (`SpringBootComposeServiceRenderer` 등) | **done** |
| B1.5-5 | `DockerComposeIaCGeneratorTest.java` | LOCAL_DEV golden: compose=MySQL만, `.env`=localhost JDBC | **done** |

**LOCAL_DEV golden 예시 (개념)**

```yaml
# docker-compose.yml — MySQL만
services:
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    ...
```

```dotenv
# .env — 호스트에서 bootRun 시 사용
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/appdb
SPRING_DATASOURCE_USERNAME=user
SPRING_DATASOURCE_PASSWORD=userpass12
MYSQL_HOST=localhost
MYSQL_PORT=3306
```

### B1 완료 기준 (B1.5 포함)

- [x] B1-5 오케스트레이터 placeholder 제거
- [x] B1.5 LOCAL_DEV 정합 (앱 compose 없음, localhost env, `HostAppEnvContributor` 연동)
- [x] B1.5-5 golden test 통과 (LOCAL_DEV 스코프)
- [x] 신규 **의존** 타입 추가 시 `DockerComposeIaCGenerator` 수정 최소화
- [x] `./gradlew test` 통과

### B1 이후 확장 (별도 이슈)

- [ ] 다중 DB env (primary + `_<SERVICE_NAME>` 접미사)
- [ ] 다중 Spring Boot 앱 (env 키 prefix)
- [ ] `PostgresComposeServiceRenderer` + `PostgresHostAppEnvContributor`
- [ ] `RedisComposeServiceRenderer`, cache env contributor
- [ ] ZIP — **B4**
- [ ] **B-TF** — §Phase B-TF

---

## Phase B-TF — CLOUD_DEPLOY (Terraform + Dockerfile · 최유성)

**전제**: §생성 목표 정의 — 클라우드에서는 **앱 이미지 빌드 명세가 필수**. 로컬 compose에 앱 `build:`를 넣지 않고, **Dockerfile + 레지스트리 + Terraform**이 주 경로.

**입력**: 동일 `ParsingResultDTO` · `OutputFormat.TERRAFORM`

| 단계 | 대상 | 작업 |
|------|------|------|
| B-TF-1 | `TerraformIaCGenerator.java` | `IaCGenerator` 구현 · 오케스트레이터 골격 |
| B-TF-2 | `DockerfileGenerator` / `SpringBootDockerfileRenderer` | JAR 멀티스테이지 Dockerfile 텍스트 |
| B-TF-3 | `MysqlTerraformRenderer` (등) | RDS·SG·subnet — **컨테이너 MySQL과 다른 renderer** |
| B-TF-4 | `SpringBootTerraformRenderer` | ECS/EC2 + ECR `image` 참조 |
| B-TF-5 | `TerraformIaCGeneratorTest` | CLOUD_DEPLOY golden (최소 1스택) |

**산출물 예시 (`files[]`)**: `Dockerfile`, `main.tf`, `variables.tf`, `outputs.tf`, (선택) `README.md` — CI에서 `docker build` / `terraform apply` 안내

**채택하지 않음**: 프로덕션 배포의 중심으로 docker-compose `build:` 번들만 제공

---

## Phase B4 — ZIP 압축 스트림 (선택 · 최유성)

보고서·프론트 프로토타입은 ZIP 다운로드를 전제로 하나, **현재 백엔드 Generate API는 JSON 본문에 파일 텍스트만 반환**한다.

| 옵션 | 설명 |
|------|------|
| A. 프론트 처리 | `files[].content`를 받아 클라이언트에서 ZIP 생성 (git `downloadGeneratedBundle` 패턴) |
| B. 백엔드 엔드포인트 | `GET .../download` 또는 `Accept: application/zip` 등 별도 API — B4에서 검토 |

---

## Phase B2 — 이력 영속화

**저장소**: OCI Docker MySQL · `generated_file.content` `LONGTEXT` (S3 미사용)

| 순서 | 대상 파일 | 작업 | 상태 |
|------|-----------|------|------|
| B2-1 | `project/entity/GeneratedFile.java` | `content` 컬럼 (LONGTEXT) | **done** |
| B2-2 | `GeneratedFileResDTO.java` | `content` 필드 | **done** |
| B2-3 | `ProjectHistoryCommandService.java` | dummy 제거 · `saveGeneratedHistory()` | **done** |
| B2-4 | `GenerationCommandService.java` | parsing → IaC → history 원자 처리 · `historyId` | **done** |
| B2-5 | `GeneratedFileConverter.java` | `toEntity` / `toFileInfoResDTO` (content) | **done** |
| B2-6 | `ProjectHistoryCommandServiceTest.java` | `saveGeneratedHistory` 검증 | **done** |

**B2 변경분 (2026-07-03 기준)**: `ProjectQueryService.getOwnedProject()` · `GeneratedFileConverter` · Generate API history 저장 연동 · `saveGeneratedHistory` 테스트. 현재 `feat/#19-code-generation` 워킹 트리에 있으며 커밋 전일 수 있음.

---

## Phase B3 — 문서화 및 통합 테스트

| 순서 | 대상 파일 | 작업 | 상태 |
|------|-----------|------|------|
| B3-1 | `ParsingControllerDocs` 또는 `GenerationControllerDocs` | Swagger + 요청 예시 JSON | **partial** (`ParsingControllerDocs` 생성 · body `projectId` 제거 반영 · Swagger 수동 성공) |
| B3-2 | `GenerateApiIntegrationTest.java` 또는 Controller WebTest | MockMvc E2E | **partial** (`ParsingControllerWebTest` 생성됨, 통합 범위 정리 필요) |

---

## ParsingReqDTO 예시 (Swagger / Postman)

```json
{
  "nodes": [
    {
      "nodeId": "node-1",
      "componentType": "MYSQL",
      "positionX": 100,
      "positionY": 200,
      "properties": {
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
    },
    {
      "nodeId": "node-2",
      "componentType": "SPRING_BOOT",
      "positionX": 400,
      "positionY": 200,
      "properties": {
        "name": "app",
        "port": 8080,
        "javaVersion": "17",
        "containerName": "spring-app"
      }
    }
  ],
  "edges": [
    { "sourceNodeId": "node-1", "targetNodeId": "node-2" }
  ]
}
```

> 프론트 `settings` flat 구조와 다름. API 계약은 **path variable `projectId` + body nested properties 형식** 기준.

### 엣지 방향 계약

프론트 캔버스와 동일: **먼저 실행할 노드를 잡고 → 나중에 실행할 노드로 연결**합니다.

| 필드 | 의미 |
|------|------|
| `sourceNodeId` | 먼저 기동·실행되는 노드 (예: MySQL, `startupPriority` 낮음) |
| `targetNodeId` | 나중에 기동·실행되는 노드 (예: Spring Boot, `startupPriority` 높음) |

위 예시: `node-1`(MySQL) → `node-2`(Spring Boot) — **그래프·검증·호스트 `.env` 생성**에 사용.

- **LOCAL_DEV**: compose `depends_on`은 **의존 인프라 블록에만** 해당 (MySQL 등). Spring Boot는 compose에 없으므로 `depends_on` 없음.
- **과거 프로토타입** (`a6acb3c`): Spring도 compose에 넣어 `depends_on` 표현 — **현재 목표 아님**.

역방향(`node-2` → `node-1`)은 `PARSING400_10` (잘못된 컴포넌트 의존성 방향)으로 거부됩니다.

---

## 프론트 연동 (deferred)

B0~B3 완료 후 별도 Phase:

- `frontend/src/api/client.ts`, `apiMapper.ts`
- `DashboardPage`, `MainPage` Generate 연동
- `LoginPage` `USE_MOCK` 제거

---

## 알려진 정리 항목 (선택)

- `ParsingController`가 `parsing` 패키지에 있으나 `generation` API 역할 — 추후 `GenerationController`로 rename 검토
- `ParsingResultDTO` 등 내부 모델을 `.codex/rules/backend.md` record 컨벤션으로 정리 검토
- B3 테스트를 Controller slice 테스트로 유지할지 Generate E2E 통합 테스트로 확장할지 결정

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-06-20 | 문서 최초 작성 |
| 2026-06-20 | 확장성 리팩터: ComponentType 통합, ParsingResultDTO.components, IaCGenerator 전략, GenerateResDTO.files |
| 2026-06-20 | B0 완료 반영: SpringBoot 파서 검증, GenerationCommandService 연동, IaCGenerationException 추가. **다음: B1** |
| 2026-06-21 | 패키지 구조 반영 (`parser/`·`validator/`·`generator/`), `ParsingResultConverter`, 엣지 방향 계약 수정 (MySQL→Spring Boot), `MySQLParser` null 방어 |
| 2026-06-21 | B0 보강: `PARSING400_14~16`, `ValidateGraphStructureTest`·`ParsingServiceTest` 추가. **B0 PR → B1** |
| 2026-06-22 | 역할 분담(황상임·P0 / 최유성·B1+) 반영. P0 검증 현황 매트릭스 추가. MySQL username·userPassword·imageVersion 미구현 명시. B4 ZIP 선택 항목 추가 |
| 2026-07-14 | MySQLParser 필수값 검증 보강: `imageVersion` · `username` · `userPassword` 전용 코드 추가, `MySQLParserTest` 반영 |
| 2026-06-22 | **B1 목표 변경**: `ComposeServiceRenderer`/`DatabaseEnvContributor` 전략. B1-1~B1-6 단계 표 |
| 2026-06-22 | 계획 검토 반영: B1 스코프 명시, parsing/generation 미지원 구분, 신규 타입 체크리스트, 다중 Spring·CACHE B1 이후, 채팅별 문서 첨부 가이드, B1-1b ComposeYamlSupport |
| 2026-06-24 | **생성 목표 확정**: LOCAL_DEV(compose=의존 인프라만, 호스트 `.env`) vs CLOUD_DEPLOY(Dockerfile+TF). B1.5·B-TF Phase 추가. Spring compose 블록·프론트 golden 전체 스택 **목표에서 제외** |
| 2026-06-24 | **진행 동기화**: B1-1~B1-5 done. B1.5-1~2 done. B1.5-3 next. compose `version:` 제거. 레거시 contributor/Spring renderer 정리 항목 추가 |
| 2026-06-30 | **B1.5 done** · **B2 in_progress**: golden test·레거시 삭제·B2-1~3·`GeneratedFileConverter`·`getOwnedProject`. **다음: B2-4** |
| 2026-07-03 | **B2 done(커밋 전)**: B2-4 history 저장 연동·B2-6 테스트 반영. **B3 partial**: `ParsingControllerDocs`, `ParsingControllerWebTest` 선행 구현. Codex 문서 링크 반영 |
| 2026-07-05 | **Generate API 계약 정리**: 요청 body `projectId` 제거, path variable만 사용. Swagger 수동 검증 성공(`historyId` 반환). 자동 E2E 테스트는 후속 정리 |
