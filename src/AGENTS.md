# Java instructions

## Baseline and language policy

- Compile and run on Java 17. Use Java 17 language and standard-library APIs deliberately; never
  enable preview features.
- Prefer `record` for small immutable value types with value-based equality. Use a `final` class
  when the type needs encapsulated invariants, behaviour, framework integration, or a stable
  existing API convention. Do not convert public types merely for stylistic consistency.
- Use `sealed` types only when the set of implementations is genuinely closed in this module and
  the restriction improves exhaustive handling. Do not use `permits` as a stylistic substitute for
  package boundaries.
- Prefer pattern matching for `instanceof` when it makes a type check and cast clearer. Do not add
  pattern-switch or other preview syntax.
- Prefer `List.of`, `Map.of`, `Optional` at return boundaries, `java.time`, streams for simple
  transformations, and `var` only when the inferred type is obvious from the right-hand side.

## Design boundaries

- Organize packages by stable responsibility and dependency direction, not by arbitrary technical
  layers. A public API, application orchestration, domain logic, persistence, and external-system
  adapters must have clear ownership and must not depend on their consumers.
- Keep data structures immutable unless mutation is an explicit part of their contract. Do not
  leak framework, database-driver, HTTP-client, or vendor-SDK types through a public API; convert
  them at a named adapter boundary.
- Introduce a package only for a stable responsibility. Prefer small collaborators over utility
  classes with unrelated static methods.
- A utility class must be `final`, have a private constructor, be stateless, and contain cohesive,
  side-effect-free operations. Prefer a dedicated service or value object when behavior has policy,
  dependencies, configuration, or meaningful error handling.
- Use JDK APIs first. Use Apache Commons Lang or Guava only when their semantics add clear value;
  do not use them merely as shorter spellings for JDK code. In particular, do not silently turn a
  required `null` value into an empty value through a convenience method.

## Documentation comments

- Use Javadoc for code comments. Every public class, interface, method, configuration property,
  and exception type must have Javadoc that explains its purpose, inputs and outputs, boundaries,
  and failure behaviour where applicable.
- Every method must have a Javadoc comment that explains what it is for and why it exists. For a
  private method, keep the comment concise and describe the design reason or invariant it protects;
  do not merely repeat the method name or its implementation.
- Update Javadoc with the code change. An inaccurate comment is a defect.

## Errors, logging, and dependencies

- Validate public inputs at the boundary and throw a documented application exception that
  preserves safe context; do not expose implementation exceptions or stack traces as API behavior.
- Use SLF4J (`LoggerFactory`) when logging is needed. Never use `System.out`, string-concatenated
  log messages, or log full payloads, credentials, tokens, or personal data.
- Keep framework, transport, persistence, and observability integrations at their adapter or
  configuration boundary. If an HTTP adapter uses Logbook, keep its configuration outside core
  logic, redact authorization headers and sensitive JSON paths, and log request bodies only under
  an approved data-retention policy.
- Pin dependency versions in `pom.xml`; justify a new library in the change description and add it
  only when the JDK or an existing dependency cannot meet the need.

## Tests

- Use JUnit 5 and mirror production packages under `src/test/java`.
- Add focused tests for normal behavior, boundary values, malformed input, and error contracts.
  Keep cross-module compatibility cases data-driven where possible.
- Run `mvn test` after Java changes. Do not change generated `target/` reports.
