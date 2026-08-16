# Frontend TypeScript instructions

## Project structure and verification

- Keep production code in `src/` and Jest tests in `test/`; write both in TypeScript.
- Preserve ESM and named public exports. Do not edit `dist/` or `node_modules/`.
- Run `npm run build` for strict type checking and `npm test` before handing off frontend changes.
- Keep a module focused on one feature or cohesive abstraction. Do not create catch-all `common`,
  `shared`, `helpers`, `utils`, or `manager` modules; place reusable code in a clearly named domain
  or technical module.

## Module layout and public API

- Start with one file for a small, private, cohesive operation. Create a directory only when a
  feature has a public API or several collaborators that change together.
- A public feature uses this shape; omit files or folders that have no current purpose:

  ```text
  src/<FeatureName>/
    index.ts             # the only public entry point
    <FeatureName>.ts     # primary facade or use case
    types.ts             # public and feature-local contracts, when needed
    constants.ts         # stable feature constants, when needed
    config.ts            # typed feature options and defaults, when needed
    execution/           # execution lifecycle, runtime rules, and call wrapping
    functions/           # registered or extensible callable functions
    adapters/            # browser, network, storage, or third-party boundaries
    validation/          # feature-owned validation, when needed
    errors/              # feature-specific typed errors and formatting, when needed
    strategies/          # interchangeable algorithms, only when they exist
    factories/           # complex construction/selection, only when it exists
  ```

- `index.ts` explicitly exports the small supported API and types; it contains no business logic,
  side-effectful initialization, wildcard re-exports, or imports from another feature's internals.
  Modules outside a feature import only from that feature's `index.ts`, never a deep path.
- A feature with no root `index.ts` is private implementation code. Create the root entry before
  another feature consumes it. A child entry such as `functions/index.ts` exposes only that child
  capability; it does not replace the parent feature's public API.
- Inside a feature, import a sibling implementation directly rather than through its own
  `index.ts`; this prevents barrel-induced dependency cycles. A feature must not import a consumer
  of its own public API.
- Use lowercase directory names for technical subdivisions, such as `execution/`, `functions/`,
  `adapters/`, `validation/`, `strategies/`, and `factories/`. PascalCase directories are reserved
  for a main domain Feature, such as `FeelExpressionEngine/`, or a TSX component directory.
- Use PascalCase for class files, Feature facades, key cohesive capability or sub-entry modules,
  and TSX components, for example `FeelExpressionEngine.ts`, `FeelFunctionRegistry.ts`,
  `WrapFeelFunctions.ts`, `BusinessDay.ts`, and `DatePicker.tsx`.
  Use lowercase fixed names for conventional role files: `index.ts`, `types.ts`, `errors.ts`,
  `constants.ts`, and `config.ts`; use lower camel case for private helper modules. Test files use
  `<Subject>.test.ts`, for example `BusinessDay.test.ts`. Use PascalCase for classes, types, and
  interfaces, and verb-led camelCase for functions. Keep one primary class or cohesive operation
  per file. Rename existing source paths when their containing module is refactored; do not mix
  casing migrations into unrelated behavior changes.
- Promote code out of its owning Feature only after at least two independent Features need the
  same stable abstraction. The resulting module has a responsibility-based name and cannot import
  from consuming Features.
- Mirror feature tests under `test/<FeatureName>/`. Test the public API by default; test an
  internal collaborator directly only when its algorithm has independent, meaningful behavior.

## Feature validation

- Create `<FeatureName>/validation/` only when the feature has validation behavior. The directory
  owns that feature's input guards, validation result/error mapping, and validators; do not create
  it merely as a placeholder.
- Group a growing validation directory by the object or rule it validates, for example
  `validation/expression/`, `validation/schema/`, or `validation/temporal/`. Its `index.ts`
  exposes the feature-supported validation API.
- Validation code may depend on the feature's local types and adapters, but it must not import the
  feature facade. Pass data or narrow collaborators in as arguments to prevent cyclic imports.
- Do not move a validator out of its owning feature just because it looks reusable. Extract a
  shared validator only after two independent Features require the same stable contract.

## Cross-feature code

- Do not create generic `common/`, `shared/`, or `utils/` directories.
- Keep a capability in its owning Feature until two independent Features need the same stable
  abstraction and neither Feature owns its policy. Then create a top-level module named for its
  responsibility, such as `time/`, `serialization/`, or `contracts/`; it must not import from a
  consuming Feature, UI, application flow, or business-domain module.
- Create `global/` only for a true application-wide concern with at least two independent
  consumers. It is not a substitute for cross-feature utilities or business policy.

## Extensible sub-capabilities inside a feature

- Split a growing feature by explicit responsibility. Use domain names such as `rules/`,
  `constraints/`, `calendar/`, or `resolution/` for domain sub-capabilities; use established
  technical boundaries such as `execution/`, `validation/`, and `adapters/` for their respective
  roles. Avoid vague names such as `part1/`, `services/`, or `misc/`.
- The parent feature remains the public boundary. Its `index.ts` exposes one coherent facade and
  the supported types; sub-capabilities are internal unless they are intentionally reusable across
  features.
- Use a direct function/module when a sub-capability has one stable implementation. Add a
  `strategies/` subdirectory only after multiple algorithms share one explicit contract. Add a
  `factories/` subdirectory only when selection or dependency assembly becomes a real concern.
- Each sub-capability owns its local types, validation, errors, and tests when they are not shared
  by siblings. Extract only genuinely shared contracts to the parent feature's `types.ts`.
- Dependencies flow inward: parent facade may coordinate sub-capabilities; sibling
  sub-capabilities must not reach into each other's internals. Share a parent-level contract or
  collaborator instead of creating cyclic imports.
- Code that knows a third-party package's undocumented behavior, error shape, parser tree, or
  warning format belongs in a named `adapters/<package-name>/` boundary. Core feature code depends
  on local contracts rather than scattering third-party details across directories.
- Do not use a feature-level `utils/` directory. Place error formatting in `errors/`, syntax-tree
  operations in `syntax/`, registered function implementations in `functions/`, function
  registration and call wrapping in `execution/`, and third-party result conversion in the relevant
  adapter. A directory name must answer what it owns.

## TypeScript safety

- Keep `strict: true`; do not disable strict compiler options to make a change compile.
- Do not write `any`, `any[]`, `Array<any>`, or `as any`. At an untyped boundary, accept
  `unknown`, validate it with type guards or a schema, and expose a precise type after validation.
- Do not use non-null assertions (`!`) or broad type assertions to suppress an error. Prefer a
  guard, discriminated union, optional chaining with an explicit fallback, or a narrow assertion
  justified by an invariant.
- Model variants with discriminated unions and exhaustive `switch` handling. Use `readonly` for
  immutable public inputs and outputs; do not mutate arguments received from callers.
- Export interfaces/types for public contracts. Keep implementation-only types unexported. Prefer
  `type` for unions and mapped types; use `interface` for an object contract intended to be
  extended or implemented.
- Catch values as `unknown`, then narrow them. Convert third-party errors into a local typed error
  at the adapter boundary instead of leaking library-specific shapes through the public API.

## Documentation comments

- Use TSDoc for code comments. Every public class, interface, function, configuration value, and
  error type must have TSDoc that explains its purpose, inputs and outputs, boundaries, and failure
  behaviour where applicable.
- Every function and method must have a TSDoc comment that explains what it is for and why it
  exists. For a private helper, keep the comment concise and describe the design reason or
  invariant it protects; do not merely repeat the function name or its implementation.
- Test cases do not require TSDoc. Use a descriptive test name and add a comment only when the
  rule, fixture, or assertion cannot be understood from the test itself.
- Update TSDoc with the code change. An inaccurate comment is a defect.

## Imports, side effects, and asynchronous work

- Use `import type` for type-only dependencies. Keep imports acyclic; if two modules require each
  other, extract their shared contract or introduce a parent-level collaborator.
- Importing a module must not start timers, network requests, storage writes, event listeners, or
  global plugin registration. Expose an explicit bootstrap/factory operation for initialization.
- Await or return every Promise. For cancellable network, worker, timer, or long-running work,
  accept and propagate an `AbortSignal`; cleanup listeners and resources on completion or abort.
- Keep browser and third-party side effects behind named adapters. Core feature logic receives data
  and dependencies as arguments and remains deterministic where practical.

## Public API evolution

- `index.ts` exports are the supported API. Additive changes are preferred; changing or removing a
  public export, option default, error contract, or serialized value requires a compatibility note
  and updated consumer-facing tests.
- Do not expose third-party types from a public API unless that dependency is intentionally part of
  the package contract. Prefer a local structural type or adapter at the boundary.

## Functions, classes, and state

- Prefer a function for a pure transformation, validation, formatter, or small operation with no
  lifecycle and no injected dependencies.
- Create a class only when it owns state, a lifecycle, resource/cache management, a cohesive set
  of injected dependencies, or a substitutable implementation of a public interface.
- Keep classes small and cohesive. Constructor parameters are explicit dependencies; store stable
  dependencies in `private readonly` fields. Do not use mutable module singletons, service
  locators, or hidden global configuration.
- Use static members only for constants and stateless factories. A singleton is allowed only when
  its state is immutable or safely isolated and its application-wide lifetime is intentional.
- Prefer composition to inheritance. Extend a class only for a real substitutable `is-a` relation;
  otherwise depend on an interface and compose collaborators.

## Utility and time libraries

- Use built-in JavaScript/TypeScript APIs first: `Array` methods, `Object`, `Map`, `Set`,
  `URL`, `Intl`, `structuredClone`, and `Promise` utilities cover most needs.
- Add lodash only for a demonstrated semantic, compatibility, or performance need that native APIs
  do not cover cleanly. Import the individual function (or an ESM/tree-shakeable equivalent), not
  the `_` namespace or an entire utility bundle. Do not use lodash merely to avoid a few lines of
  clear native code.
- Use Day.js only when its parsing, duration, plugin, locale, or timezone behavior is required;
  otherwise use the native `Date`/`Intl` APIs. Enable Day.js plugins once in a dedicated time
  adapter, never ad hoc throughout feature code.
- Date/time APIs receive an explicit clock and IANA time zone when business behavior depends on
  either. Do not use the browser clock, local default zone, or mutable global timezone settings in
  deterministic domain logic. Keep date-only values distinct from instants.

## Feature configuration and constants

- Keep a feature's own values with that feature:
  - `<FeatureName>/config.ts`: typed feature options and default values, when configuration exists.
  - `<FeatureName>/constants.ts`: stable domain literals with no environment-dependent behavior.
- Do not create empty `config.ts` or `constants.ts` files. When present, their ownership is the
  feature rather than a repository-wide template.
- Do not create a `global/` directory until a real application-wide concern has at least two
  independent consumers. When that happens, `global/config/` is the single adapter for deployment
  or runtime configuration; it parses, validates, and injects a typed immutable object.
- Until then, keep configuration with its owning feature. Do not read `import.meta.env`,
  `process.env`, `window`, storage, or a remote runtime-config document throughout feature code;
  isolate such a read at the feature boundary that owns it.
- Frontend configuration is public to the browser: never put credentials, signing keys, or server
  secrets in it. Treat remotely supplied configuration as untrusted input until validated.
- Keep defaults next to the feature contract. Merge defaults and caller options once in a factory or
  constructor; consumers receive the resolved read-only options rather than repeatedly merging
  partial configuration.
- Prefer `export const` for static values. `as const` gives compile-time literal types but does not
  freeze an object at runtime; use `Object.freeze` only when runtime mutation protection matters.
  Do not create `Static`, `Config`, or constants classes merely to hold values.

## Design-pattern admission rules

- Start with the simplest direct implementation that has one stable behavior. Do not introduce a
  pattern to anticipate hypothetical variants.
- Use the Strategy pattern when two or more algorithms implement the same typed operation, the
  caller selects one based on explicit input/configuration, and variants can evolve independently.
  Define a narrow strategy interface and a feature-specific typed selection mechanism; do not use
  a switch plus loosely typed callbacks disguised as a strategy registry.
- Use a Factory when object construction selects an implementation, assembles several dependencies,
  validates construction invariants, or hides a third-party runtime. Keep selection in one factory;
  do not create factories for a single `new` call with no policy.
- Use Adapter when isolating a third-party/browser API behind a stable local contract. Use Builder
  only for genuinely complex optional construction. Prefer plain object literals for simple data.
- Patterns must reduce a current, demonstrated source of complexity. A change introducing one must
  name the variation point it removes and add tests for every registered/constructed variant.

## Errors, dependencies, and tests

- Validate external input at the UI, network, storage, and plugin boundaries. Return or throw a
  documented typed error; never silently coerce malformed values.
- Add a dependency only when native APIs and existing dependencies are insufficient. Put shipped
  libraries in `dependencies`, build/test-only libraries in `devDependencies`, and justify the
  addition in the change description.
- Add focused tests for behavior, boundary values, invalid input, error contracts, and every
  strategy/factory branch. Tests must not depend on the current clock, machine locale, or local
  timezone unless those are the subject under test.
