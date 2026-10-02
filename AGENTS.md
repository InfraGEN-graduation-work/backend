# Repository Guidelines

## Where to Start

- Issue work: read `docs/handoff/issue-{number}-handoff.md` first. It holds the scope, contracts, decisions, current state, and next task, and links the rules that apply.
- Project-wide order and future scope: `docs/handoff/plan/backend-future-plan.md`. Product context: `docs/infra-gen-project-overview.md`.
- Read only the documents relevant to the task. `docs/handoff/` and `docs/harness/personal_convention/` are personal local documents (gitignored); follow them when present.

## Always Apply

- Implement only requested behavior. Avoid speculative features, broad refactoring, unrelated cleanup, and unnecessary abstraction. Follow nearby conventions and never bypass tests.
- Before substantial edits, share the requirement, affected components, and assumptions. When present, follow `docs/harness/personal_convention/work_scope_convention.md` for work-unit limits, code layout, and reporting style, and `handoff_convention.md` for design approval and handoff updates.
- After every implementation, run a focused re-verification pass against nearby conventions, relevant tests, and the requirements. For framework, library, protocol, security, or architectural-pattern work, check current authoritative documentation and correct issues within scope. Report verification sources, findings, results, and remaining risks, or why external verification did not apply.
- Never commit or expose secrets or `.env` values. Do not edit `build/generated/querydsl`; change its source entity or query.
- Reply in practical, casual Korean. Report changed files, validation results, and risks. Do not paste complete source files unless requested.

## Rule Documents

- `docs/harness/project_code_convention/`: team rules (tracked)
  - `architecture_convention.md`: package structure, responsibility boundaries, dependency direction, extension points
  - `controller_convention.md`, `service_convention.md`, `dto_convention.md`, `converter_convention.md`, `exception_convention.md`: layer rules
  - `testing_convention.md`: test structure and which tests to run
- `docs/harness/personal_convention/`: personal rules (local)
  - `work_scope_convention.md`, `handoff_convention.md`, `comment-style.md`

## Project Facts

- Application code: `src/main/java/com/infragen/infragen`, grouped under `domain/<feature>` (`controller -> service -> repository`) with shared infrastructure in `global/`. Tests mirror production packages under `src/test/java`. Runtime configuration: `src/main/resources/application.yaml`.
- Authentication work: inspect the JWT utility, security filters, exception codes, and member entity/repository together. JWT subjects are member IDs; inactive or soft-deleted members must not be authenticated.
- JPA changes: review for N+1 queries, lazy-loading problems, and incorrect transaction boundaries.
- Coding style: four-space indentation, `PascalCase` classes, `camelCase` methods and fields, lowercase packages, layer suffixes (`Controller`, `CommandService`, `QueryService`, `Repository`). Prefer explicit exceptions or `Optional` over `null`. Match nearby code and avoid formatting-only changes.

## Build and Test

Use the Gradle Wrapper with Java 21.

- `./gradlew test --tests "*MemberQueryServiceTest"`: one test class (default; prefer focused tests without external infrastructure)
- `./gradlew test`, `./gradlew clean build`: full suite and broad build, only when the user explicitly requests them
- `./gradlew bootRun`: run the API with `application.yaml` and local environment overrides
- `docker compose up -d mysql redis`: MySQL 8 and Redis, only for explicitly requested infrastructure-dependent tests

Tests connect to the configured database; there is no H2 test profile. Test selection and reporting rules are in `testing_convention.md`.

## Commits and Pull Requests

Use focused commits with prefixes from history (`feat:`, `fix:`, `chore:`, `test:`). Pull requests explain the problem, implementation, configuration/schema impact, and verification, with request/response examples for API changes.
