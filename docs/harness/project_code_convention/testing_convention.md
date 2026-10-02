# Testing Convention

## Scope

Consistent boundaries and style for unit, slice, and integration tests. Apply to new or updated tests; do not rewrite unrelated legacy tests only for formatting.

## Structure and Naming

- Arrange-Act-Assert with `// given`, `// when`, `// then`. Do not use `// when & then` in new or updated tests.
- One test covers one behavior or one failure path. Call the subject once unless repetition is the contract.
- Class names: `<Subject>Test`, or a boundary suffix when it matters (`<Subject>WebTest`, `<Subject>RepositoryTest`, `<Subject>IntegrationTest`).
- Method names: `<method>_<scenario>_<expectedResult>` where practical. `@DisplayName` is a short sentence describing the observable result; keep comments short.

## Assertions

- Keep result assertions in `then`; use `assertAll` when several checks describe one outcome.
- Exception tests capture the exception with `assertThrows` in `when` and verify its domain code or meaningful fields in `then`.
- Use `assertDoesNotThrow` only when the absence of failure is the main contract.
- Assert observable results, state, and error codes, not private details. Avoid order-dependent assertions unless ordering is the contract.

## Mockito

- `@ExtendWith(MockitoExtension.class)` for plain Mockito tests. Mock repositories, external clients, and infrastructure utilities only when isolation is needed; prefer real objects for pure parsers, validators, converters, and deterministic generators.
- Stub only what the scenario uses; no `lenient()` without a specific reason.
- Use exact values or `eq()` for contract-critical arguments and `any()` only for irrelevant ones. Use `ArgumentCaptor` when the passed content is the behavior under test.
- Use `verify` and `never()` only for interactions that matter; do not add `verifyNoMoreInteractions` mechanically.

## Test Types

- **Unit (domain, service)**: no Spring context when constructor-created objects or Mockito suffice. Verify results, state changes, domain exceptions, and important interactions. Cover success, validation, ownership, not-found, and dependency-failure paths as applicable.
- **Controller slice**: `@WebMvcTest`, `MockMvc`, `@MockitoBean`. Verify status, response code, representative JSON fields, validation failures, and service invocation. Disabling security filters is only for controller-contract tests; add filter-enabled tests for anonymous access, authentication, authorization, and invalid tokens.
- **Repository**: `@DataJpaTest` for custom queries, sorting, ownership conditions, mappings, and constraints. Use the MySQL dialect when behavior can differ, flush and clear when verifying reads or constraints, and check for unintended N+1 or lazy-loading assumptions.
- **Integration**: `@SpringBootTest` only when multiple layers or real infrastructure must work together. Use the documented local Docker services or an explicitly configured container environment (Testcontainers preferred); never assume H2. Use test-only configuration, never real credentials or `.env` values, and isolate data through rollback, cleanup, or unique scoping. Do not leave required tests `@Disabled` without a recorded reason and restoration condition.
- **Exception and advice**: direct advice tests verify exception-to-code mapping with minimal data; use MockMvc when HTTP status, serialization, validation, or resolver behavior is part of the contract.

## Fixtures and Determinism

- Keep fixtures minimal and explicit; helpers reduce repetition but must not hide preconditions.
- Restrict `ReflectionTestUtils` to persistence-managed fields such as generated IDs or audit timestamps.
- Prefer a fixed `Clock` or fixed values over `now()` when time affects assertions. Do not synchronize with `Thread.sleep()`.
- Use `@ParameterizedTest` for repeated parser, validator, or boundary cases.
- A sequential mock response does not prove concurrency; verify database or Redis atomicity with an integration test when concurrency is the contract.

## Which Tests to Run

- By default, run the most focused relevant tests that need no Docker, MySQL, Redis, Terraform CLI, or other external infrastructure.
- Run `./gradlew test`, `./gradlew clean build`, or infrastructure-dependent tests only when the user explicitly requests them, starting the documented services first.
- Report tests not run, the reason, and the remaining risk. Never bypass or weaken a failing test to make the build pass.
