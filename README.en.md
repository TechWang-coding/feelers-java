# FEELers Java Implementation

Chinese: [README.md](README.md)

This project reproduces the front-end [`feelers`](https://www.npmjs.com/package/feelers) template-processing flow. Java parses the template shell, while Camunda `feel-engine` (FEEL Scala) evaluates FEEL expressions.

Supported template syntax:

```text
Hello, {{ user.name }}
{{#if count(items) > 1}}There are {{ count(items) }} items{{/if}}
{{#loop items}}- {{ this }} (from {{ parent.user.name }})
{{/loop}}
```

Supported FEEL capabilities are determined by the Camunda engine. The entry point is `FeelersTemplateService.evaluate(template, context)`; `FeelExpressionEngine` isolates the engine implementation, and the public API does not expose Camunda or Scala types.

```sh
mvn test
```

The tests use JUnit 5 and mirror the front-end Jest scenarios: insertion, conditions, loops, `this`/`parent`, newlines, strict/debug behavior, and sanitization.

Design document: [docs/feelers-java-backend-design.en.md](docs/feelers-java-backend-design.en.md)

Code conventions: [docs/code-conventions.en.md](docs/code-conventions.en.md)
