# InfraGEN 백엔드 로드맵 v2 - 활성

> 역할 분리
>
> - 인덱스: [README.md](./README.md)
> - 이전 버전: [backend-roadmap-v1.md](./backend-roadmap-v1.md)
> - 상세 실행 순서와 기술 계약: [backend-implementation-plan.md](../plan/backend-implementation-plan.md)
> - 이 문서: 앞으로 남은 마일스톤과 우선순위만 정리하는 활성 로드맵

## 1. 목차

1. 목적
2. 현재 상태 요약
3. 다음 마일스톤
4. 작업 원칙
5. 비중복 규칙
6. 완료 기준
7. 참조 문서

## 2. 목적

이 문서는 지금 시점 이후에 실제로 진행할 백엔드 작업의 큰 순서만 정리한다.

이 로드맵은 다음을 하지 않는다.

- 세부 구현 방법을 반복하지 않는다.
- 파일 단위 변경 목록을 나열하지 않는다.
- 테스트 명령이나 코드 레벨 계약을 다시 쓰지 않는다.

그런 내용은 `docs/plan/backend-implementation-plan.md`가 담당한다.

## 3. 현재 상태 요약

현재 기준으로 다음 상태로 본다.

- Generate API 기본 파이프라인은 구현되어 있다.
- `ProjectHistoryCommandService.saveGeneratedHistory()`와 `GeneratedFileConverter`까지 연결되어 있다.
- `ParsingController`와 `ParsingControllerDocs`는 Generate 계약을 유지하고 있다.
- `ParsingControllerWebTest`는 얇은 웹 계층 테스트 수준으로 존재한다.
- `B3`는 진행 중이고, `B-TF`와 Redis 확장은 아직 남아 있다.

즉, 지금 이후의 로드맵은 “새 기능의 시작”보다 “남은 단계의 마감”에 가깝다.

## 4. 다음 마일스톤

### M1. B3 마감

목표:

- Swagger 문서와 실제 Controller 시그니처를 끝까지 맞춘다.
- MockMvc 테스트를 얇은 매핑 검증으로 정리한다.
- Generate 계약이 문서와 구현에서 어긋나지 않게 고정한다.

종료 기준:

- `projectId` path 계약이 문서와 코드에서 동일하다.
- Generate 성공 응답 구조가 일관된다.
- 문서와 테스트가 같은 시그니처를 가리킨다.

### M2. B-TF 준비

목표:

- Cloud Deploy용 산출물을 준비한다.
- Dockerfile과 Terraform 스캐폴드를 plan-only 수준으로 정리한다.
- 아직 배포 실행 단계로 넘어가지 않는다.

종료 기준:

- runtime-only Dockerfile 방향이 유지된다.
- Terraform은 plan 가능한 형태로만 다룬다.
- apply-ready로 오해될 만한 표현을 남기지 않는다.

### M3. Redis LOCAL_DEV

목표:

- 로컬 개발용 Redis만 단순 지원한다.
- 인증/암호화 기능은 넣지 않는다.
- 호스트 실행용 env 생성 흐름과만 연결한다.

종료 기준:

- Redis는 unauthenticated LOCAL_DEV only로 설명된다.
- password/ACL/TLS 관련 산출물이 없다.
- LOCAL_DEV Compose와 env 생성 흐름에만 반영된다.

### M4. 문서와 하네스 정리

목표:

- roadmap와 plan의 역할을 다시 섞지 않는다.
- 경로, 링크, 인덱스를 정리한다.
- 이후 작업자가 어디를 먼저 읽어야 하는지 분명하게 만든다.

종료 기준:

- roadmap는 상태와 마일스톤만 가진다.
- plan은 상세 실행만 가진다.
- 오래된 링크는 포인터 또는 아카이브로 남긴다.

### M5. 선택 과제

목표:

- ZIP 다운로드가 실제로 필요할 때만 별도 검토한다.
- 우선순위가 내려가면 뒤로 미룬다.

종료 기준:

- 핵심 로드맵의 흐름을 방해하지 않는다.
- 필수 항목이 아니라면 독립 과제로 남긴다.

## 5. 작업 원칙

- 로드맵은 “무엇을 다음에 할지”만 적는다.
- 구현 세부는 `docs/plan/`으로 보낸다.
- 문서는 서로 역할이 겹치지 않게 유지한다.
- 이미 완료된 B0, B1, B1.5, B2는 다시 설명하지 않는다.
- 아직 시작하지 않은 기능은 여기서만 큰 방향을 잡고, 상세는 plan으로 넘긴다.

## 6. 비중복 규칙

이 문서는 다음 내용을 반복하지 않는다.

- Controller/Service/Converter의 세부 책임
- 테스트 파일명과 정확한 실행 명령
- Terraform 리소스 구성 세부
- Redis 관련 구체 구현
- E2E 하네스 내부 세부

이 내용은 모두 `docs/plan/backend-implementation-plan.md`를 본다.

## 7. 완료 기준

다음이 충족되면 이 로드맵의 남은 구간은 정리된 것으로 본다.

- B3가 마감된다.
- B-TF 준비가 끝난다.
- Redis LOCAL_DEV 범위가 분명해진다.
- 문서/하네스가 분리된다.
- 선택 과제는 필수 흐름과 분리된다.

## 8. 참조 문서

- [backend-roadmap-v1.md](./backend-roadmap-v1.md)
- [backend-implementation-plan.md](../plan/backend-implementation-plan.md)
