# Exception Convention

## 기본 원칙

- 예외와 성공 응답 코드는 도메인별로 관리한다. 메시지, HTTP 상태, 애플리케이션 코드를 Controller나 Service에 직접 쓰지 않는다.
- 비즈니스 예외는 도메인 전용 `XXXException`으로 던진다.

## 패키지 구조

```text
{domain}/exception/
├── XXXException.java
└── code/
    ├── error/XXXErrorCode.java
    └── success/XXXSuccessCode.java
```

## 도메인 Exception

공통 `GeneralException`을 상속하고, 현재 도메인의 `XXXErrorCode`만 생성자로 받는다. 생성자 타입을 `BaseErrorCode`로 넓혀 다른 도메인의 코드를 받게 하지 않는다.

```java
public class MemberException extends GeneralException {

    public MemberException(MemberErrorCode errorCode) {
        super(errorCode);
    }
}
```

## ErrorCode와 SuccessCode

- `XXXErrorCode`는 `BaseErrorCode`, `XXXSuccessCode`는 `BaseSuccessCode`를 구현하는 enum이고 HTTP 상태, 메시지, 애플리케이션 코드를 가진다.
- SuccessCode 상수는 `_SUCCESS` 접미사를 쓴다.

```java
@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 조회에 실패하였습니다.", "MEMBER404_1"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.", "MEMBER409_1");

    private final HttpStatus httpStatus;
    private final String message;
    private final String code;
}
```

## 코드 규칙

형식은 `{DOMAIN}{HTTP_STATUS}_{SEQUENCE}`다. 예: `MEMBER200_1`, `MEMBER400_1`, `MEMBER404_1`

- 도메인명은 대문자로 쓰고, 상태 번호는 실제 `HttpStatus`와 일치해야 한다.
- 순번은 같은 도메인과 상태 코드 안에서 증가시킨다. 같은 코드를 중복하거나 다른 의미로 재사용하지 않는다.
- 외부에 공개된 코드는 임의로 바꾸지 않는다.

## 사용 책임

- Service는 상황에 맞는 ErrorCode로 예외를 던지고, Controller는 정상 응답에 맞는 SuccessCode를 고른다. 예외 응답 변환은 `GeneralExceptionAdvice`가 한다.
- Converter와 DTO는 Exception과 Code enum에 의존하지 않는다.
- 모든 비즈니스 오류를 `IllegalArgumentException`으로 처리하거나 Service에서 `ResponseStatusException`을 쓰지 않는다. SuccessCode를 예외처럼 던지지 않는다.
- 응답 메시지에 내부 구현 정보나 민감 정보를 넣지 않는다.
