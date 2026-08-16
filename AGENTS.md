# Repository instructions

## Scope and architecture

- This repository is an expression-platform codebase. New capabilities may use the expression
  engine but must remain independently understandable and maintainable.
- Preserve every declared cross-runtime contract: expression profile, custom-function
  names/signatures, input/output data shape, date/time semantics, and error behavior must agree on
  both sides.
- Treat a behavior change that affects more than one runtime or feature as a contract change:
  update the relevant Java and TypeScript tests in the same change, or document why only one
  runtime is affected.
- Keep design decisions and long-lived rationale in `docs/`; do not duplicate coding conventions
  there. Keep this file limited to operational rules and place runtime-specific coding conventions
  in the closest applicable `AGENTS.md`.

## Working rules

- Read the closest applicable `AGENTS.md` before changing code. More-specific instructions add to
  these rules; they do not relax them.
- Make focused changes. Do not mix refactors, dependency upgrades, generated output, or formatting
  churn into an unrelated feature or bug fix.
- Do not edit `target/`, `frontend/dist/`, or `node_modules/`; they are generated artifacts.
- Run the checks for every changed runtime:
  - Java: `mvn test`
  - Frontend: `cd frontend && npm test`
- Report checks not run and the reason. Do not claim front/back compatibility without evidence.

## Cross-runtime rules

- Accept and emit only JSON-compatible public data at cross-runtime boundaries; normalize dates,
  numbers, and `null` explicitly.
- A feature owns its domain rules and public API; the expression engine owns expression parsing,
  evaluation, and its allowlisted extension points. Do not let feature-specific policy leak into a
  shared engine adapter without an explicit reusable contract.
- New FEEL functions must be allowlisted, deterministic, side-effect free, documented with a
  signature and failure behavior, and covered by matching Java/TypeScript vectors.
- Never evaluate arbitrary host-language code, expose reflection, or register functions directly
  from request data or a schema.
- `today`/`now`-dependent validation receives a caller-supplied clock and business time zone. Do
  not let browser and server clocks independently determine a validation result.
