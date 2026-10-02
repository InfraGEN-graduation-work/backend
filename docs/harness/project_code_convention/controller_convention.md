# Controller Convention

## 목적

Controller는 HTTP 요청을 받아 검증하고 Service를 호출한 뒤, 결과를 공통 응답 `ApiResponse<T>`로 감싼다. 비즈니스 로직과 변환은 다른 계층에 위임한다.

## 구조와 명명

```text
{domain}/controller/
├── {Domain}Controller.java
└── docs/{Domain}ControllerDocs.java
```

- 실제 Controller는 `{Domain}Controller`, Swagger 문서 인터페이스는 `{Domain}ControllerDocs`로 작성하고 Controller가 Docs를 구현한다.
- `@RestController`, `@RequestMapping`, 생성자 주입을 쓴다.

## 책임

Controller가 하는 일: HTTP 요청 매핑, PathVariable·RequestParam·RequestBody 수신, Request DTO validation, 인증 사용자 정보 추출, Service 호출, SuccessCode로 `ApiResponse<T>` 생성

Controller가 하지 않는 일:

- Repository 직접 호출, Entity 조회·생성·수정
- DTO 변환, 비즈니스 조건 분기
- `@Transactional`
- 도메인 예외 `try-catch`, ErrorCode나 오류 메시지 선택, `ApiResponse.onFailure` 직접 호출. 예외는 `GeneralExceptionAdvice`가 공통 실패 응답으로 바꾼다.

## Service 호출

- GET은 `{Domain}QueryService`, POST·PUT·PATCH·DELETE는 `{Domain}CommandService`를 호출한다. CQRS를 적용하지 않는 도메인은 일반 Service를 호출할 수 있다.
- Service 결과를 추가 가공하지 않고 공통 응답으로 감싼다.

## 공통 응답

- 모든 정상 응답은 `ApiResponse<T>`로 반환하고 `ResponseEntity`를 쓰지 않는다.
- 도메인 `XXXSuccessCode`를 쓰고 메시지와 코드를 문자열로 직접 쓰지 않는다.
- 결과가 없는 성공도 `ApiResponse.onSuccess(code, null)`로 형식을 유지하며, generic 타입은 기존 방식(`ApiResponse<Void>`)을 따른다.

## Validation

- RequestBody DTO에는 `@Valid`, PathVariable·RequestParam 검증이 필요하면 `@Validated`를 쓴다.
- 형식과 단순 제약은 Request DTO에서, DB 조회가 필요한 검증과 비즈니스 규칙은 Service에서 처리한다.

## Docs 인터페이스

- Swagger/OpenAPI 문서화(`@Tag`, `@Operation`, `@ApiResponses`, `@Parameter`, 스키마 설명)만 작성하고 구현 로직이나 default 메서드는 두지 않는다.
- Docs와 Controller의 메서드 이름, 매개변수, 반환 타입은 일치해야 한다.

## 예시

```java
@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(summary = "회원 정보 조회")
    ApiResponse<MemberResDTO.Detail> getMember(Long memberId);
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class MemberController implements MemberControllerDocs {

    private final MemberQueryService memberQueryService;

    @Override
    @GetMapping("/{memberId}")
    public ApiResponse<MemberResDTO.Detail> getMember(@PathVariable Long memberId) {
        MemberResDTO.Detail result = memberQueryService.getMember(memberId);
        return ApiResponse.onSuccess(MemberSuccessCode.MEMBER_INFO_FETCH_SUCCESS, result);
    }
}
```
