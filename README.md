# FEELers 的 Java 实现

这个工程复现了前端 [`feelers`](https://www.npmjs.com/package/feelers) 的模板处理流程。模板外壳由 Java 解析，FEEL 表达式由 Camunda 的 `feel-engine`（FEEL Scala）执行。

支持的模板语法：

```text
您好，{{ user.name }}
{{#if count(items) > 1}}共有 {{ count(items) }} 项{{/if}}
{{#loop items}}- {{ this }}（来自 {{ parent.user.name }}）
{{/loop}}
```

支持的 FEEL 能力由 Camunda 引擎决定。入口为 `new FeelersTemplate().render(template, context)`；引擎实现通过 `FeelExpressionEngine` 隔离，公共 API 不泄露 Camunda/Scala 类型。

```sh
mvn test
```

测试使用 JUnit 5，并与前端 Jest 用例保持同一组模板场景：插值、条件、循环、`this`/`parent`、换行、strict/debug 与 sanitizer。
