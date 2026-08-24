# Testing Convention

## Purpose and Scope

Define consistent boundaries and styles for unit, slice, and integration tests in this repository.
Apply these rules to new or updated tests. Do not rewrite unrelated legacy tests only for formatting.

## Basic Structure

- Write tests in Arrange-Act-Assert order and mark sections with `// given`, `// when`, and `// then`.
- Do not use `// when & then` in new or updated tests.
- Keep comments short; let the method name and `@DisplayName` explain the scenario.
- Keep one test focused on one behavior or one failure path.
- Call the subject under test once when possible. Repeated calls are allowed when repetition is the contract.

## Naming

- Use `<Subject>Test` for unit and service tests.
- Use descriptive suffixes when the test boundary matters, such as `<Subject>WebTest`,
  `<Subject>RepositoryTest`, and `<Subject>IntegrationTest`.
- Name methods `<method>_<scenario>_<expectedResult>` where practical.
- Write `@DisplayName` as a short, readable sentence describing the observable result.

## Assertion Rules

- Keep result assertions in `then`.
- Use `assertAll` when multiple related checks describe one outcome.
- In exception tests, capture the exception with `assertThrows` in `when`, then verify its domain code
  or other meaningful fields in `then`.
- Use `assertDoesNotThrow` only when the absence of failure is the main contract.
- Assert observable results, state, and error codes rather than private implementation details.
- Avoid assertions whose success depends on collection order unless ordering is part of the contract.

## Mockito Rules

- Use `@ExtendWith(MockitoExtension.class)` for plain Mockito tests.
- Mock repositories, external clients, and infrastructure utilities only when isolation is needed.
- Prefer real objects for pure parsers, validators, converters, and deterministic generators.
- Stub only behavior used by the scenario; do not use `lenient()` without a specific reason.
- Prefer exact values or `eq()` for contract-critical arguments. Use `any()` only for irrelevant values.
- Use `ArgumentCaptor` when the content passed to a collaborator is the behavior being verified.
- Use `verify` and `never()` only for interactions that matter to the contract.
- Do not add `verifyNoMoreInteractions` mechanically.

## Test Types

### Pure Domain and Service Unit Tests

- Do not load a Spring context when constructor-created real objects or Mockito are sufficient.
- Verify returned results, state changes, domain exceptions, and important collaborator interactions.
- Cover success, validation, ownership, not-found, and dependency-failure paths as applicable.

### Controller Slice Tests

- Use `@WebMvcTest`, `MockMvc`, and `@MockitoBean` for request/response contract tests.
- Verify status, response code, representative JSON fields, validation failures, and service invocation.
- Put request data and service stubbing in `given`, `perform` in `when`, and expectations in `then`.
- Disabling security filters is allowed only for controller-contract tests; it does not verify security.
- Add separate filter-enabled tests for anonymous access, authentication, authorization, and invalid tokens.

### Repository Tests

- Use `@DataJpaTest` for custom queries, sorting, ownership conditions, mappings, and constraints.
- Use the actual MySQL dialect when behavior can differ from an embedded database.
- Flush and clear the persistence context when verifying database reads or constraints.
- Check queries for unintended N+1 access and lazy-loading assumptions when relevant.

### Integration Tests

- Use `@SpringBootTest` only when multiple application layers or real infrastructure must work together.
- MySQL- or Redis-dependent tests must use the documented local Docker services or an explicitly
  configured container-based test environment; do not assume H2 is available.
- Use test-only configuration and environment variables. Never embed real credentials or `.env` values.
- Keep test data isolated through rollback, explicit cleanup, or uniquely scoped data.
- Do not leave required tests indefinitely `@Disabled`; record the reason and restoration condition.

### Exception and Advice Tests

- Direct advice tests verify exception-to-response-code mapping with minimal representative data.
- Use MockMvc when HTTP status, serialization, validation, or resolver behavior is part of the contract.

## Fixtures and Determinism

- Keep fixtures minimal and explicit. Use helper methods for repetition, not to hide preconditions.
- Restrict `ReflectionTestUtils` to persistence-managed fields such as generated IDs or audit timestamps.
- Prefer fixed time through `Clock` or fixed values over `now()` when time affects the assertion.
- Do not use `Thread.sleep()` for synchronization; use deterministic coordination or bounded waiting.
- Use `@ParameterizedTest` for repeated parser, validator, or boundary-value cases.
- A sequential mock response does not prove concurrency; verify database or Redis atomicity with an
  appropriate integration test when concurrency itself is the contract.

## Verification

- Run the most focused relevant test while editing.
- Run `./gradlew test` before handoff; use `./gradlew clean build` for broad changes.
- When an integration test requires MySQL or Redis, start the documented services before execution.
- Report skipped or unexecutable tests, their reason, and the remaining risk.
- Do not bypass or weaken a failing test merely to make the build pass.
