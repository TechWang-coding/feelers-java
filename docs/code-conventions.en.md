# Code Conventions

## Java language features

The project runs and compiles on Java 17, but source code follows a Java 8-compatible object-modeling style.

The following keywords are prohibited in production and test code:

- `sealed`
- `permits`
- `record`

For immutable value objects, use a `final class`, `private final` fields, an explicit constructor, and accessors. When value equality is required, also implement `equals`, `hashCode`, and `toString`.

Use ordinary interfaces for type contracts; do not restrict their implementation set with `sealed` or `permits`.

This convention restricts source style only. It does not change the requirement to run and compile the project on Java 17.

## Directory structure

Keep production code under `src/main/java/com/tech/feelers/templating/`. The package layout follows Spring application conventions so the library can be integrated into a Spring application without reorganizing its core code. The current core module remains framework-neutral: it does not use Spring annotations or require Spring at runtime.

```text
src/main/java/com/tech/feelers/templating/
  config/        Spring @Configuration and @Bean definitions, when Spring integration is added
  web/           Spring MVC controllers and HTTP request/response DTOs, when an HTTP API is added
  service/       Application services and the public FEELers-aligned API
  domain/        Business-domain entities, value objects, and domain rules, when business features are added
  repository/    Persistence ports and Spring Data repositories, when persistence is added
  engine/        FEEL engine abstraction and Camunda adapter
  parser/        FEELers template-shell AST and parsing logic
  model/         Cross-layer template option and result value objects
  exception/     Template-domain exceptions

src/test/java/com/tech/feelers/templating/
  ...           Tests mirror the production package of the class under test

src/test/resources/
  ...           Test fixtures only

docs/           English project documentation and design decisions
templating-syntax/
  ...           Front-end FEELers Jest contract tests
```

Directory rules:

- Keep the public template API and application orchestration in `service/`. Spring `@Service` classes belong here when the application adds Spring; the current static API remains framework-neutral.
- Put Spring bootstrapping and dependency wiring in `config/`. Do not put `@Configuration` classes in `service/`, `engine/`, or `parser/`.
- Put HTTP controllers and HTTP-only request/response DTOs in `web/`. Controllers must delegate to `service/` and must not call `engine/` or `parser/` directly.
- Put persistence interfaces and Spring Data repositories in `repository/`. Do not expose persistence entities as public template API models.
- Put business concepts in `domain/`. Do not place template AST nodes or Camunda adapter types in this package.
- Keep all Camunda FEEL types and engine-specific behavior in `engine/`; they must not leak into `service/`, `parser/`, or `model/` public signatures.
- Keep template-shell structure and syntax validation in `parser/`. Do not evaluate FEEL expressions in this package.
- Put cross-layer configuration or immutable data used by callers in `model/`; do not place business services or parser nodes there.
- Put only template-domain exceptions in `exception/`. Do not create an exception package per feature without a distinct error boundary.
- Add a new top-level package only when it owns a stable, independent responsibility. Do not create empty placeholder packages; `config/`, `web/`, `domain/`, and `repository/` are created only when their corresponding Spring responsibility exists.
- Mirror the production package in `src/test/java`. Use `src/test/resources` for fixture files instead of embedding large documents in test code.
