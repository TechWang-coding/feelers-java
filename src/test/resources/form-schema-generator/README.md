# Form Schema 生成器契约测试夹具

每个 `*.case.json` 都是生成器的一个契约用例，包含：

- `formDefinition`：前端在 Form 设计/发布阶段提交的字段数组和条件规则；
- `expectedSchema`：后端应生成的完整 JSON Schema Draft 2020-12 文档。

`FormSchemaGeneratorTest` 会逐个加载这些夹具，以结构化 JSON 比较（忽略对象键顺序和整数/小数的表示差异）断言实际输出和 `expectedSchema` 完全一致。生成器不得直接使用 `expectedSchema`，该字段只用于测试。

这些夹具覆盖基础字段映射、静态必填、可选字段的值约束、FEEL `and`、`or`、括号分组以及条件必填。
