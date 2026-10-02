# DTO Convention

## 구조와 명명

각 도메인은 요청 DTO와 응답 DTO를 패키지와 파일 단위로 분리한다. 패키지 선언은 실제 디렉터리와 일치해야 한다.

```text
{domain}/dto/
├── request/{Domain}ReqDTO.java
└── response/{Domain}ResDTO.java
```

- 용도별 DTO는 최상위 클래스 안의 `public record`로 정의하고 `Request`, `Response`, `DTO` 접미사를 붙이지 않는다. 사용 예: `MemberReqDTO.SignUp`, `MemberResDTO.Detail`
- 최상위 클래스는 `public final class`와 `private` 생성자를 쓴다.
- 용도별 DTO를 최상위 파일로 무분별하게 분리하거나, 요청과 응답을 하나의 클래스에 섞거나, `dto` 바로 아래에 두지 않는다.
- Lombok `@Data`, `@Getter`를 쓰지 않는다. 응답 DTO 내부 record에는 `@Builder`를 선언한다.

```java
public final class MemberResDTO {
    private MemberResDTO() {
    }

    @Builder
    public record Detail(Long id, String email, String name) {
    }
}
```

## 요청 DTO

- 입력값만으로 판단 가능한 규칙(null 여부, 길이, 범위, 형식)은 record 컴포넌트에 `jakarta.validation`으로 선언하고 구체적인 오류 메시지를 쓴다. `javax.validation`은 쓰지 않는다.
- 중첩 DTO에는 `@Valid`를 적용한다.
- 중복, 존재 여부, 권한, 상태 검증은 Service에서 처리한다. 요청 DTO는 Service와 Repository에 의존하지 않는다.

```java
public record SignUp(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이어야 합니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 30, message = "비밀번호는 8자 이상 30자 이하여야 합니다.")
        String password
) {
}
```

## 응답 DTO

- validation annotation을 쓰지 않는다.
- DTO 안에 `from`, `of` 같은 변환 메서드를 두지 않는다. Entity와 DTO 사이의 변환과 응답 조립은 Converter가 맡는다.
- Entity를 API 요청·응답 타입으로 직접 쓰지 않고, 민감 정보와 불필요한 연관관계를 노출하지 않는다.
- DTO에서 DB 조회나 비즈니스 처리를 하지 않는다.

Parsing component DTO의 예외는 `architecture_convention.md`의 "현재 구현에서 주의할 사실"을 따른다.
