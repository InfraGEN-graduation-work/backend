# Service Convention

## 목적

Service는 애플리케이션 유스케이스와 트랜잭션 경계를 담당하고, Controller, Repository, Converter의 책임을 침범하지 않는다.

## 구조와 CQRS

```text
{domain}/service/
├── {Domain}QueryService.java
└── {Domain}CommandService.java
```

- `{Domain}QueryService`: 조회만 담당하고 엔티티 상태를 바꾸지 않는다. 결과는 Response DTO로 반환하고 변환은 Converter에 위임한다.
- `{Domain}CommandService`: 생성, 수정, 삭제, 상태 변경을 담당하고 조회 전용 API를 섞지 않는다. 엔티티는 의미 있는 도메인 메서드로 변경한다.
- 조회 기능이 없는 도메인은 CQRS를 강제하지 않고 `{Domain}Service`로 둔다(예: `AuthService`, `TokenService`).
- 불필요한 Service 인터페이스는 만들지 않는다.

## 트랜잭션

- `@Transactional`은 클래스가 아닌 public 메서드에 선언한다. 조회 메서드는 `readOnly = true`를 쓰고, private 메서드에는 선언하지 않는다.
- 같은 클래스 내부 호출로 트랜잭션 전파를 기대하지 않는다.
- 트랜잭션 안에서 장시간 외부 API를 호출하지 않는다.

## 책임

Service가 하는 일: 유스케이스 실행과 흐름 조정, Repository를 통한 조회와 저장, 트랜잭션 경계 설정, 비즈니스 예외 발생, 여러 도메인 객체의 작업 조합

Service가 하지 않는 일: HTTP 요청·응답 처리, Swagger 문서화, validation annotation 정의, 단순 객체 매핑 구현이나 DTO 조립 반복

## 의존성

- Repository는 주입받는다. Converter는 static 유틸리티로 쓰고 주입하지 않는다.
- QueryService와 CommandService가 서로 의존하지 않게 하고, Service 간 연쇄 호출은 최소화한다. 복잡한 다중 도메인 흐름은 Facade나 Application Service를 검토한다.
- 순환 의존을 만들지 않는다.

## 예외와 반환

- 조회 실패나 규칙 위반은 도메인 예외로 던지고, 응답 변환은 전역 예외 처리기에 맡긴다. 예외 코드와 사용 책임은 [exception_convention.md](./exception_convention.md)를 따른다.
- Repository 결과에 무조건 `.get()`을 호출하지 않는다.
- Entity를 Controller에 직접 반환하지 않는다. 생성은 식별자나 결과 DTO를, 응답이 필요 없는 수정과 삭제는 `void`를 반환한다.

## 예시

```java
@Service
@RequiredArgsConstructor
public class MemberQueryService {

    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberResDTO.Detail getMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND));
        return MemberConverter.toDetail(member);
    }
}
```
