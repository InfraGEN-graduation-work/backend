# InfraGEN Backend Codex Guide

이 문서는 `backend/`에서 작업할 때 따르는 백엔드 전용 기준입니다. 공통 작업 원칙은 `../AGENTS.md`를 함께 따릅니다.

## Reference Docs

- Generate 관련 최신 상태는 `../docs/integration-roadmap.md`를 먼저 확인합니다.
- 백엔드 작업 전 `GEMINI.md`, `README.md`, 관련 Java 파일을 확인합니다.
- 자세한 백엔드 규칙은 `../.codex/rules/backend.md`를 따릅니다.
- Generate 파이프라인 작업은 `../.codex/context/generate-pipeline.md`를 따릅니다.

## Build And Verification

- Java 변경 후 최소 `./gradlew compileJava`를 실행합니다.
- 테스트를 추가하거나 변경했으면 `./gradlew test` 또는 해당 테스트를 실행합니다.
- Gradle 명령은 `backend/` 디렉터리에서 실행합니다.

## Architecture

- Controller -> Service -> Repository 흐름을 유지합니다.
- 새 도메인은 `com.infragen.infragen.domain.{domain}/` 아래에 둡니다.
- 쓰기 서비스는 `service/command`, 읽기 서비스는 `service/query`에 둡니다.
- Query 서비스는 클래스 레벨 `@Transactional(readOnly = true)`을 사용합니다.
- 비즈니스 로직과 트랜잭션은 Service에 둡니다.
- 참고 도메인은 `src/main/java/com/infragen/infragen/domain/project`입니다.

## API And Controller

- `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor` 패턴을 유지합니다.
- 인증 사용자 ID는 `@AuthenticationPrincipal CustomUserDetails`에서 `userDetails.getMemberId()`로 가져옵니다.
- 모든 API 응답은 `ApiResponse<T>`를 사용합니다.
- 응답은 `ApiResponse.onSuccess(SuccessCode, result)`로 반환합니다.
- 생성 API는 필요한 경우 `@ResponseStatus(HttpStatus.CREATED)`를 사용합니다.
- Swagger 어노테이션은 `controller/docs/*ControllerDocs` 인터페이스에 둡니다.

## DTO, Converter, Entity

- Request DTO는 outer class + nested record 형태를 따릅니다.
- Response record에는 필요한 경우 `@Builder`를 사용합니다.
- Converter는 static 메서드 중심으로 유지하고 Spring Bean으로 만들지 않습니다.
- MapStruct 같은 새 매핑 라이브러리를 임의로 도입하지 않습니다.
- 엔티티는 `BaseEntity`를 상속합니다.
- 테이블명은 snake_case를 사용합니다.
- 연관관계는 기본적으로 `LAZY`를 사용합니다.
- 상태 변경은 엔티티 메서드로 표현합니다.

## Exceptions

- 도메인 예외와 에러 코드를 통해 예외를 처리합니다.
- 컨트롤러에서 직접 HTTP 에러 응답을 만들지 않습니다.
- 전역 예외 처리는 `GeneralExceptionAdvice`에 맡깁니다.
- 에러 코드는 `"PROJECT404_1"` 같은 도메인 prefix + HTTP status + 순번 형식을 유지합니다.
- 메시지는 한국어로 작성합니다.

## Generate Pipeline

- Generate 파이프라인은 `parsing -> generation -> project history` 흐름입니다.
- `generation/**`은 검증된 `ParsingResultDTO`를 입력으로 받습니다.
- 필수 속성 누락, 형식 오류, 그래프 오류는 `parsing/**`에서 처리합니다.
- generation 레이어에서 parsing 예외를 던지지 않습니다.
- generation 실패는 generation 예외 코드로 처리합니다.
- LOCAL_DEV 산출물은 의존 인프라 compose와 호스트 실행용 `.env`입니다.
- LOCAL_DEV에서는 Spring Boot 앱을 compose 서비스로 만들지 않습니다. 의존 인프라만 compose에 포함합니다.

## Tests And Style

- JUnit 5와 Mockito 패턴을 따릅니다.
- `@DisplayName`은 한국어로 작성합니다.
- given / when / then 흐름이 드러나게 작성합니다.
- 참고 테스트는 `src/test/java/com/infragen/infragen/domain/project`입니다.
- 주석은 필요한 경우에만 짧게 작성합니다.
- 불필요한 추상화와 대규모 리팩터링을 피합니다.
