# 테스트 컨벤션

## 범위

단위, 슬라이스, 통합 테스트의 경계와 스타일을 일관되게 한다. 새로 작성하거나 수정하는 테스트에 적용하며, 관련 없는 기존 테스트를 포맷만 이유로 고치지 않는다.

## 구조와 명명

- Arrange-Act-Assert 구조로 `// given`, `// when`, `// then`을 쓴다. 새로 작성하거나 수정하는 테스트에는 `// when & then`을 쓰지 않는다.
- 테스트 하나는 동작 하나 또는 실패 경로 하나를 다룬다. 반복이 계약이 아니면 대상 메서드는 한 번만 호출한다.
- 클래스 이름은 `<Subject>Test`이고, 경계가 중요하면 접미사를 붙인다 (`<Subject>WebTest`, `<Subject>RepositoryTest`, `<Subject>IntegrationTest`).
- 메서드 이름은 가능하면 `<method>_<scenario>_<expectedResult>`로 쓴다. `@DisplayName`은 관찰 가능한 결과를 설명하는 짧은 문장으로 쓰고, 주석은 짧게 쓴다.

## 단언

- 결과 단언은 `then`에 둔다. 여러 검사가 하나의 결과를 설명하면 `assertAll`을 쓴다.
- 예외 테스트는 `when`에서 `assertThrows`로 예외를 잡고, `then`에서 도메인 코드나 의미 있는 필드를 검증한다.
- `assertDoesNotThrow`는 실패하지 않는 것이 핵심 계약일 때만 쓴다.
- private 세부 구현이 아니라 관찰 가능한 결과, 상태, 에러 코드를 단언한다. 순서가 계약이 아니면 순서에 의존하는 단언을 피한다.

## Mockito

- 일반 Mockito 테스트는 `@ExtendWith(MockitoExtension.class)`를 쓴다. 격리가 필요할 때만 Repository, 외부 클라이언트, 인프라 유틸리티를 mock하고, 순수 parser, validator, converter, 결정적인 generator는 실제 객체를 쓴다.
- 시나리오가 쓰는 것만 stub한다. 특별한 이유 없이 `lenient()`를 쓰지 않는다.
- 계약상 중요한 인자는 정확한 값이나 `eq()`로 검증하고, 중요하지 않은 인자에만 `any()`를 쓴다. 전달된 내용이 검증 대상이면 `ArgumentCaptor`를 쓴다.
- `verify`와 `never()`는 의미 있는 상호작용에만 쓴다. `verifyNoMoreInteractions`를 기계적으로 추가하지 않는다.

## 테스트 유형

- **단위 (도메인, 서비스)**: 생성자로 만든 객체나 Mockito로 충분하면 Spring context를 쓰지 않는다. 결과, 상태 변경, 도메인 예외, 중요한 상호작용을 검증한다. 해당하는 만큼 성공, 검증, 소유권, not-found, 의존성 실패 경로를 다룬다.
- **Controller 슬라이스**: `@WebMvcTest`, `MockMvc`, `@MockitoBean`을 쓴다. 상태, 응답 코드, 대표 JSON 필드, 검증 실패, Service 호출을 검증한다. 보안 필터 비활성화는 controller 계약 테스트에서만 쓴다. 익명 접근, 인증, 인가, 잘못된 토큰은 필터를 켠 테스트로 따로 검증한다.
- **Repository**: 커스텀 쿼리, 정렬, 소유권 조건, 매핑, 제약에는 `@DataJpaTest`를 쓴다. 동작이 다를 수 있으면 MySQL dialect를 쓰고, 읽기나 제약을 검증할 때는 flush와 clear를 한다. 의도하지 않은 N+1과 지연 로딩 가정도 확인한다.
- **통합**: 여러 계층이나 실제 인프라가 함께 동작해야 할 때만 `@SpringBootTest`를 쓴다. 문서화된 로컬 Docker 서비스나 명시적으로 설정한 컨테이너 환경(Testcontainers 우선)을 쓰고 H2를 가정하지 않는다. 테스트 전용 설정을 쓰고 실제 자격 증명이나 `.env` 값을 쓰지 않는다. 롤백, 정리, 고유 범위로 데이터를 격리한다. 필요한 테스트를 복원 조건과 사유 기록 없이 `@Disabled`로 두지 않는다.
- **예외와 advice**: advice 직접 테스트는 최소 데이터로 예외와 코드의 매핑을 검증한다. HTTP 상태, 직렬화, validation, resolver 동작이 계약이면 MockMvc를 쓴다.

## fixture와 결정성

- fixture는 최소로, 명시적으로 만든다. helper는 반복을 줄이되 전제 조건을 숨기지 않는다.
- `ReflectionTestUtils`는 생성된 ID나 audit 시각처럼 영속성이 관리하는 필드에만 쓴다.
- 시간이 단언에 영향을 주면 `now()`보다 고정 `Clock`이나 고정 값을 쓴다. `Thread.sleep()`으로 동기화하지 않는다.
- 반복되는 parser, validator, 경계 케이스에는 `@ParameterizedTest`를 쓴다.
- 순차적인 mock 응답은 동시성을 증명하지 않는다. 동시성이 계약이면 통합 테스트로 DB나 Redis의 원자성을 검증한다.

## 실행할 테스트 범위 (Which Tests to Run)

- 기본적으로 Docker, MySQL, Redis, Terraform CLI 같은 외부 인프라가 필요 없는, 가장 집중된 관련 테스트를 실행한다.
- `./gradlew test`, `./gradlew clean build`, 인프라 의존 테스트는 사용자가 명시적으로 요청할 때만 실행하고, 문서화된 서비스를 먼저 시작한다.
- 실행하지 않은 테스트, 그 이유, 남은 위험을 보고한다. 빌드를 통과시키려고 실패하는 테스트를 우회하거나 약화하지 않는다.
