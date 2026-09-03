# Repository Guidelines

## Working Principles

Implement only requested behavior. Keep changes focused; avoid speculative features, broad refactoring, unrelated cleanup, and unnecessary abstraction. Follow nearby conventions and never bypass tests.

Start with referenced files, nearby code, and relevant tests instead of loading all documentation. Before substantial edits, summarize the requirement, affected components, and assumptions.

## Project Structure & Architecture

Application code lives in `src/main/java/com/infragen/infragen`. Features are grouped under `domain/<feature>` and follow `controller -> service -> repository`. Keep controllers thin, business rules in services, persistence in repositories, and shared infrastructure in `global/`.

Tests mirror production packages under `src/test/java`. Runtime configuration is in `src/main/resources/application.yaml` and documentation is in `docs/`.

## Documentation Context

Read only the documents relevant to the task before making substantial changes.

Use these as the default entry points:

- `docs/handoff/issue-{number}-handoff.md` -> issue scope, technical contracts, current work state, and verification results
- `docs/infra-gen-project-overview.md` -> project goal and product context
- `docs/harness/personal_convention/work_scope_convention.md` -> work scope limits
- `docs/harness/personal_convention/comment-style.md` -> comment and Javadoc style rules
- `docs/harness/project_code_convention/architecture_convention.md` -> package structure, responsibility boundaries, and dependency direction
- `docs/harness/project_code_convention/controller_convention.md` -> controller, response, validation, and Swagger rules
- `docs/harness/project_code_convention/service_convention.md` -> CQRS, transactions, service responsibilities, and exceptions
- `docs/harness/project_code_convention/dto_convention.md` -> request/response DTO layout and naming
- `docs/harness/project_code_convention/converter_convention.md` -> entity/DTO conversion rules
- `docs/harness/project_code_convention/exception_convention.md` -> domain error/success code rules
- `docs/harness/personal_convention/handoff_convention.md` -> handoff writing and update rules
- `docs/harness/project_code_convention/testing_convention.md` -> test structure, AAA comments, Mockito usage, and test naming rules

`docs/` is the project reference library.

- `handoff/` covers execution details, technical contracts, cross-session context, and current work state.
- `harness/` covers work boundaries and coding conventions.
- `infra-gen-project-overview.md` provides the project-level summary.

Start from the relevant document instead of reading everything. For issue work, read the matching
`docs/handoff/issue-{number}-handoff.md`; use `docs/handoff/plan/backend-future-plan.md` for project-wide order and future scope.

For authentication work, inspect the existing JWT utility, security filters, exception codes, and member entity/repository together. JWT subjects currently represent member IDs; inactive or soft-deleted members must not be authenticated.

After every code implementation, perform a focused re-verification pass. Check the changed code against nearby project conventions, relevant tests, and the task requirements. When the work involves a framework, library, protocol, security concern, or established architectural pattern, research current authoritative documentation and representative real-world examples, then correct issues found within the requested scope. In the handoff, report the verification sources, findings, validation results, and remaining risks. If external verification is unavailable or not applicable, state why.

## Build, Test, and Development Commands

Use the Gradle Wrapper with Java 21:

- `./gradlew test` — run all JUnit tests; use only when the user explicitly requests the full suite.
- `./gradlew test --tests "*MemberQueryServiceTest"` — run one test class when it does not require external infrastructure.
- `./gradlew clean build` — compile, test, generate QueryDSL sources, and create the application JAR; use only when the user explicitly requests a broad build.
- `./gradlew bootRun` — run the API using `application.yaml` values and local environment overrides.
- `docker compose up -d mysql redis` — start MySQL 8 and Redis only for explicitly requested infrastructure-dependent tests.

The current test setup connects to the configured database; start local MySQL only when the user
explicitly requests a Docker- or database-dependent test. Do not describe the test environment as
H2 unless an H2 test profile and dependency have been added.

Never commit or expose secrets or `.env` values. Do not edit `build/generated/querydsl`; change its source entity or query.

## Coding Style & Spring Rules

Use four-space Java indentation. Classes use `PascalCase`; methods and fields use `camelCase`; packages are lowercase. Follow suffixes such as `Controller`, `CommandService`, `QueryService`, and `Repository`. No formatter is enforced, so match nearby code and avoid formatting-only changes.

Use method-level transactions only where needed and `readOnly = true` for appropriate queries. Prefer explicit exceptions or `Optional` over `null`. Preserve existing API response and exception patterns. Review JPA changes for N+1 queries, unnecessary calls, lazy-loading problems, and incorrect transaction boundaries.

## Testing Guidelines

Tests use JUnit 5, Mockito, and Spring Test. Name classes `<Subject>Test`; use descriptive methods such as `createMember_Success`. Cover success, validation, ownership, repository interactions, and edge cases.

By default, run only focused relevant tests that do not require Docker, MySQL, Redis, Terraform CLI,
or other external infrastructure. Run the full suite or `clean build` only when the user explicitly
requests it. If a relevant test requires external infrastructure and is not run, report why and state
the remaining risk.

## Commits, Pull Requests, and Handoff

Use focused commits with prefixes found in history, especially `feat:` and `fix:`. Pull requests should explain the problem, implementation, configuration/schema impact, and verification. Include request/response examples for API changes.

Reply in practical, casual Korean. After editing, report changed files, validation results, and risks. Do not paste complete source files unless requested.
