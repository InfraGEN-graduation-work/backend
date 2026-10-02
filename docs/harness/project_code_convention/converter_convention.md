# Converter Convention

## 목적

Converter는 DTO와 Entity 사이의 변환을 담당한다. DTO 안에 변환 메서드를 두지 않고 객체 생성과 응답 조립을 별도 계층으로 분리한다.

## 클래스 규칙

- 위치는 `{domain}/converter/{Domain}Converter.java`다.
- `final class`, `private` 생성자, `static` 변환 메서드로 작성하고 상태 필드와 외부 의존성을 두지 않는다. 인스턴스를 만들거나 주입하지 않는다.

## 메서드 명명

- Request DTO → Entity: `toEntity`
- Entity → 응답: `toResult`, `toDetail`, `toSummary`. 응답 DTO 이름이 명확하면 그 이름에 맞춘다.
- 목록: `toResultList` 등 `to{응답}List`, Page: `toPage`

## 책임

Converter가 하는 일: Request DTO → Entity, Entity → Response DTO, 여러 값을 하나의 객체로 조합, 목록과 Page 변환. Controller와 Service에서 반복되는 DTO 조립 코드를 여기로 모은다.

Converter가 하지 않는 일:

- Repository 조회, Service 호출, 트랜잭션
- 권한·중복·상태 판단, 비즈니스 조건 분기, 예외 발생
- 비밀번호 암호화, 외부 API 호출
- HTTP 응답 생성, 기존 Entity 상태 변경

연관 Entity, 암호화된 비밀번호, 소셜 정보처럼 추가로 필요한 값은 Service가 준비해 매개변수로 넘긴다. Converter는 전달받은 값을 Builder에 매핑만 한다.

## 객체 생성과 null

- Entity와 Response DTO는 Builder로 생성하고 public setter를 쓰지 않는다.
- 역할, 활성 여부 같은 생성 기본값은 명시적으로 설정한다.
- 컬렉션 결과는 null 대신 빈 컬렉션으로 반환한다.
- 필수 입력의 null을 조용히 허용하거나 기본 객체로 임의 변환하지 않는다. 선택 필드는 null을 유지할 수 있고, null 여부로 비즈니스 결정을 내리지 않는다.

## 예시

```java
public final class MemberConverter {
    private MemberConverter() {
    }

    public static MemberResDTO.Result toResult(Member member) {
        return MemberResDTO.Result.builder()
            .id(member.getId())
            .email(member.getEmail())
            .nickname(member.getNickname())
            .build();
    }

    public static Member toEntity(MemberReqDTO.Signup request, String encodedPassword) {
        return Member.builder()
            .email(request.email())
            .password(encodedPassword)
            .nickname(request.nickname())
            .role(Role.ROLE_USER)
            .isActive(true)
            .build();
    }

    public static List<MemberResDTO.Result> toResultList(List<Member> members) {
        return members.stream().map(MemberConverter::toResult).toList();
    }

    public static Page<MemberResDTO.Result> toPage(Page<Member> members) {
        return members.map(MemberConverter::toResult);
    }
}
```
