# 저장소 가이드

## 시작 지점

- 제품 맥락: `docs/infra-gen-project-overview.md`
- 작업과 관련된 문서만 읽는다. `docs/harness/personal_convention/`은 개인 로컬 문서 (gitignore 대상)이며, 있으면 따른다.

## 항상 적용

- 요청받은 동작만 구현한다. 추측성 기능, 광범위한 리팩터링, 관련 없는 정리, 불필요한 추상화를 피한다. 주변 컨벤션을 따르고 테스트를 우회하지 않는다.
- 큰 수정 전에는 요구사항, 영향받는 컴포넌트, 가정을 먼저 공유한다. 파일이 있으면 한 채팅의 작업 단위 진행, 코드 배치, 보고 방식은
  `docs/harness/personal_convention/work_scope_convention.md`를, 작업 단위 설계, 계획 승인, 핸드오프 갱신은 `handoff_convention.md`를 따른다.
- 구현할 때마다 주변 컨벤션, 관련 테스트, 요구사항 기준으로 집중 재검증을 한다. 프레임워크, 라이브러리, 프로토콜, 보안, 아키텍처 패턴 작업은 최신 공식 문서를 확인하고 범위 안에서 문제를 고친다. 검증
  출처, 발견 사항, 결과, 남은 위험을 보고하고, 외부 검증이 해당하지 않으면 그 이유를 보고한다.
- 비밀값이나 `.env` 값을 커밋하거나 노출하지 않는다. `build/generated/querydsl`은 수정하지 않고 원본 Entity나 쿼리를 고친다.
- 답변은 실무적이고 일관된 존댓말 (`~합니다`, `~해요`)로 하고 `~다`, `~한다` 같은 평서체 종결을 섞지 않는다. 문서 본문은 기존 문체를 따른다. 변경 파일, 검증 결과, 위험을 보고한다. 요청이
  없으면 소스 파일 전체를 붙여넣지 않는다.

## 규칙 문서

- `docs/harness/project_code_convention/`: 팀 규칙 (추적됨)
    - `architecture_convention.md`: 패키지 구조, 책임 경계, 의존 방향, 확장 지점
    - `controller_convention.md`, `service_convention.md`, `dto_convention.md`, `converter_convention.md`,
      `exception_convention.md`: 계층별 규칙
    - `testing_convention.md`: 테스트 구조와 실행할 테스트 범위
    - `issue_pr_convention.md`: 이슈와 Pull Request 본문 작성 규칙
- `docs/harness/personal_convention/`: 개인 규칙 (로컬)
    - `work_scope_convention.md`, `handoff_convention.md`, `comment-style.md`

## 프로젝트 사실

- 애플리케이션 코드: `src/main/java/com/infragen/infragen`. 기능별로 `domain/<feature>`(`controller -> service -> repository`)에 묶고,
  공용 인프라는 `global/`에 둔다. 테스트는 `src/test/java` 아래에서 운영 코드 패키지를 그대로 따른다. 런타임 설정: `src/main/resources/application.yaml`
- 인증 작업: JWT 유틸리티, 보안 필터, 예외 코드, 회원 Entity와 Repository를 함께 확인한다. JWT subject는 회원 ID이며, 비활성 또는 soft delete된 회원은 인증하면 안
  된다.
- JPA 변경: N+1 쿼리, 지연 로딩 문제, 잘못된 트랜잭션 경계를 검토한다.
- 코딩 스타일: 4칸 들여쓰기, 클래스는 `PascalCase`, 메서드와 필드는 `camelCase`, 패키지는 소문자, 계층 접미사 (`Controller`, `CommandService`,
  `QueryService`, `Repository`)를 쓴다. `null`보다 명시적 예외나 `Optional`을 우선한다. 주변 코드에 맞추고 포맷만 바꾸는 변경은 피한다.

## 빌드와 테스트

Java 21과 Gradle Wrapper를 쓴다.

- `./gradlew test --tests "*MemberQueryServiceTest"`: 테스트 클래스 하나 (기본값, 외부 인프라가 필요 없는 집중 테스트를 우선)
- `./gradlew test`, `./gradlew clean build`: 전체 테스트와 전체 빌드. 사용자가 명시적으로 요청할 때만 실행
- `./gradlew bootRun`: `application.yaml`과 로컬 환경 오버라이드로 API 실행
- `docker compose up -d mysql redis`: MySQL 8과 Redis. 명시적으로 요청된 인프라 의존 테스트에서만 사용

순수 단위 테스트는 외부 인프라가 필요 없다. DB 의존 테스트 (`@SpringBootTest`, `@DataJpaTest`, 통합 테스트)는 로컬 Docker MySQL과 Redis 또는
Testcontainers를 쓰며, H2 테스트 프로필은 없다. 테스트 선택과 보고 규칙은 `testing_convention.md`에 있다.

## 커밋과 Pull Request

히스토리의 prefix (`feat:`, `fix:`, `chore:`, `test:`)로 목적이 하나인 커밋을 만든다. 커밋은 사용자가 요청할 때만 한다. 커밋 메시지에는 Co-Authored-By 같은 AI 작성
표식이나 `(R1)` 같은 작업 단위 표기를 넣지 않고 prefix와 목적 설명만 쓴다. 하네스의 기본 attribution 안내보다 이 지시가 우선한다. 이슈와 Pull Request 본문은
`issue_pr_convention.md`를 따른다.
