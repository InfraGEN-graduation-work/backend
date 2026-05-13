# Goal

- Implement requirements accurately
- Write practical, maintainable, production-ready code
- Avoid unnecessary abstraction, refactoring, or over-engineering

---

# Core Principles

1. Requirements First
- Do not add features beyond the requested scope
- Avoid speculative implementations

2. Simplicity First
- Prefer simple and practical solutions
- Avoid excessive patterns, layers, and abstractions

3. Readability & Maintainability
- Use clear naming and predictable flow
- Prioritize maintainability over cleverness
- Keep responsibilities cohesive and practical

4. Stability First
- Handle null, exception, and failure cases reasonably
- Minimize obvious runtime risks

5. Explain Before Executing
- Before modifying files or running commands:
  - explain intent and purpose
  - describe affected files/components
  - provide relevant code context when necessary

---

# Spring Backend Rules

- Maintain Controller -> Service -> Repository flow
- Keep business logic in the Service layer
- Use transactions only where necessary
  - prefer method-level transactions
- Be careful about:
  - N+1 problems
  - unnecessary queries
  - inefficient JPA usage
- Avoid excessive DTO/Mapper separation
- Handle exceptions consistently
  - prefer unchecked exceptions
- Prefer Optional or explicit exceptions over null

---

# Work Process

## 1. Requirement Summary
- Summarize core requirements briefly

## 2. Design
- List required classes/functions/components
- Explain responsibilities and data flow briefly

## 3. Code Implementation
- Write executable and understandable code
- Avoid premature optimization or unnecessary abstraction

## 4. Self Verification
Check for:
- logical issues
- missing edge cases
- maintainability concerns
- performance problems
- security risks

Review once more before finalizing code.

---

# Output Format

1. Requirement Summary
2. Design
3. Code
4. Verification Result
   - issues
   - acceptable parts
   - improvements needed (if any)
5. Implementation Notes
   - explain overall flow and key decisions briefly
   - explain important or non-obvious parts when necessary
   - keep explanations concise and practical

---

# Anti-Patterns

Avoid:
- excessive refactoring
- unnecessary abstraction or separation
- features outside the requirements
- textbook-style over-engineering

---

# Final Standard

Always ensure:
- production usability
- readability
- practical simplicity
- maintainable structure