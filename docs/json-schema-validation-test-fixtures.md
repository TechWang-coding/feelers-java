# 校验测试用例（配套设计文档的公共 Fixture 起点）

> 状态：草案，对应 [json-schema-validation-design.md](./json-schema-validation-design.md) 第 10 节
> 第 4 项"公共 JSON fixture"的起点，尚未覆盖嵌套对象、数组项等更多组合场景
>
> 最后更新：2026-08-17

## 1. 业务场景

"折扣申请表单"：客户提交一笔折扣申请，需要同时验证：

- 联系方式按渠道条件必填（`x-feel-required`）；
- 折扣金额的上下限来自同一份数据中的 `minAmount`/`maxAmount`（动态数字关键字）；
- 折扣金额的小数位数不能超过币种允许的精度（`x-feel-assertions` + 精度函数，设计文档 5.1）；
- 客户号必须存在于客户主数据中（`x-feel-assertions` + 受控查询函数，设计文档 8.2）；
- 到期日不能早于下一个工作日（`x-feel-assertions`，设计文档 6、9.3）。

这份 fixture 里假定的注册函数行为（仅用于测试向量，不是最终规范）：

| 函数 | 行为假设 |
| --- | --- |
| `decimalPlaces(n)` | 返回数字的小数位数；`25.5` → 1，`25.505` → 3 |
| `currencyScale(code)` | `USD`→2、`CNY`→2、`JPY`→0 |
| `businessDay(date, n)` | 从 `date` 起跳过周六周日，返回第 `n` 个工作日（本 fixture 暂不考虑节假日日历，见设计文档 10.2） |
| `existsInReferenceSet(setName, key)` | 受控查询函数（8.2），查询客户主数据中是否存在该 `customerId` |

`clock.today = "2026-08-17"`（周一）；据此 `businessDay(clock.today, 1) = "2026-08-18"`（周二）。

## 2. Form DSL JSON（源）

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://forms.example.com/discount-application/v1",
  "type": "object",
  "required": ["channel", "customerId", "currency", "minAmount", "maxAmount", "discount", "dueDate"],
  "x-feel-required": {
    "email": "data.channel = \"email\"",
    "phone": "data.channel = \"sms\""
  },
  "properties": {
    "channel": { "type": "string", "enum": ["email", "sms"] },
    "email": { "type": "string", "format": "email" },
    "phone": { "type": "string", "pattern": "^\\+[1-9]\\d{6,14}$" },
    "customerId": { "type": "string", "minLength": 1 },
    "currency": { "type": "string", "enum": ["USD", "JPY", "CNY"] },
    "minAmount": { "type": "number" },
    "maxAmount": { "type": "number" },
    "discount": {
      "type": "number",
      "minimum": "{{ data.minAmount }}",
      "maximum": "{{ data.maxAmount }}"
    },
    "dueDate": { "type": "string", "format": "date" }
  },
  "additionalProperties": false,
  "x-feel-assertions": [
    {
      "id": "customer-exists",
      "assert": "existsInReferenceSet(\"customerId\", value.customerId)",
      "target": "/customerId",
      "messageKey": "customerId.notFound"
    },
    {
      "id": "discount-scale-matches-currency",
      "assert": "decimalPlaces(value.discount) <= currencyScale(value.currency)",
      "target": "/discount",
      "messageKey": "discount.invalidScale"
    },
    {
      "id": "due-date-not-before-tomorrow",
      "assert": "date(value.dueDate) >= businessDay(clock.today, 1)",
      "target": "/dueDate",
      "messageKey": "dueDate.notBeforeNextBusinessDay"
    }
  ]
}
```

## 3. 生成的 JSON Schema（示例）

生成结果依赖当次提交的数据，不是固定产物。以下是针对第 5 节"场景 1"输入生成的纯 JSON
Schema：`x-feel-required`、`x-feel-assertions` 已被消费掉、不出现在输出中；`{{ }}` 已替换为
具体数字；`email` 已合并进 `required`。

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://forms.example.com/discount-application/v1",
  "type": "object",
  "required": ["channel", "customerId", "currency", "minAmount", "maxAmount", "discount", "dueDate", "email"],
  "properties": {
    "channel": { "type": "string", "enum": ["email", "sms"] },
    "email": { "type": "string", "format": "email" },
    "phone": { "type": "string", "pattern": "^\\+[1-9]\\d{6,14}$" },
    "customerId": { "type": "string", "minLength": 1 },
    "currency": { "type": "string", "enum": ["USD", "JPY", "CNY"] },
    "minAmount": { "type": "number" },
    "maxAmount": { "type": "number" },
    "discount": {
      "type": "number",
      "minimum": 0,
      "maximum": 100
    },
    "dueDate": { "type": "string", "format": "date" }
  },
  "additionalProperties": false
}
```

若渠道换成 `sms`、`minAmount`/`maxAmount` 换一组值，`required` 会换成包含 `phone`、
`minimum`/`maximum` 也会换成对应数字——生成物是"数据 + Form DSL JSON"的函数，两套测试都应
按输入重新生成后再比较，不能把上面这份当成唯一的黄金 Schema 去逐字比对。

## 4. FEEL 求值上下文

```json
{
  "data": { "...": "本次提交的完整 JSON，见各场景" },
  "value": "== data（因为 x-feel-required / x-feel-assertions 都在根对象上）",
  "clock": {
    "today": "2026-08-17",
    "now": "2026-08-17T09:00:00+08:00",
    "timeZone": "Asia/Shanghai"
  }
}
```

## 5. 测试场景

| # | 场景 | 预期 category/code |
| --- | --- | --- |
| 1 | 全部通过 | 无错误 |
| 2 | 条件必填未满足 | `data` / `data.required` |
| 3 | 动态上限被突破 | `data` / `data.maximum` |
| 4 | 到期日早于下一工作日 | `assertion` / `assertion.failed`（`due-date-not-before-tomorrow`） |
| 5 | 折扣精度超过币种允许位数 | `assertion` / `assertion.failed`（`discount-scale-matches-currency`） |
| 6 | 客户号查询超时 | `upstream` / `upstream.lookupTimeout` |
| 7 | `x-feel-required` 表达式结果非布尔 | `schema` / `schema.feelResultType` |
| 8 | `{{ }}` 内 FEEL 语法错误 | `schema` / `schema.feelSyntax` |

### 场景 1：全部通过

输入：

```json
{
  "channel": "email",
  "email": "alice@example.com",
  "customerId": "CUST-1001",
  "currency": "USD",
  "minAmount": 0,
  "maxAmount": 100,
  "discount": 25.5,
  "dueDate": "2026-08-19"
}
```

预期：`existsInReferenceSet` 返回 true；`decimalPlaces(25.5)=1 <= currencyScale(USD)=2`；
`2026-08-19 >= 2026-08-18`。JSON Schema 与三条 `x-feel-assertions` 均通过，错误数组为 `[]`。

### 场景 2：条件必填未满足（`data.required`）

在场景 1 基础上，删除 `email` 字段（`channel` 仍是 `email`）。

预期错误：

```json
[
  {
    "category": "data",
    "code": "data.required",
    "instancePath": "",
    "schemaPath": "#/required",
    "keyword": "required",
    "assertionId": null,
    "messageKey": "validation.required",
    "arguments": { "property": "email" }
  }
]
```

`x-feel-assertions` 不执行（9.3：有数据错误时关系断言短路），所以即使 `customerId`/`discount`/
`dueDate` 本身没问题，也只返回这一条错误。

### 场景 3：动态上限被突破（`data.maximum`）

在场景 1 基础上，把 `discount` 改成 `150`（`maxAmount` 仍是 `100`）。

预期错误：

```json
[
  {
    "category": "data",
    "code": "data.maximum",
    "instancePath": "/discount",
    "schemaPath": "#/properties/discount/maximum",
    "keyword": "maximum",
    "assertionId": null,
    "messageKey": "validation.maximum",
    "arguments": { "limit": 100 }
  }
]
```

同样触发 JSON Schema 阶段的数据错误，`x-feel-assertions` 短路不执行。

### 场景 4：到期日早于下一工作日（`assertion.failed`）

在场景 1 基础上，把 `dueDate` 改成 `"2026-08-17"`（等于 `clock.today`，早于
`businessDay(clock.today, 1) = "2026-08-18"`）。

预期错误：

```json
[
  {
    "category": "assertion",
    "code": "assertion.failed",
    "instancePath": "/dueDate",
    "schemaPath": "#/x-feel-assertions/2",
    "keyword": null,
    "assertionId": "due-date-not-before-tomorrow",
    "messageKey": "dueDate.notBeforeNextBusinessDay",
    "arguments": {}
  }
]
```

这条只在 JSON Schema 校验全部通过后才会出现——如果同一份数据还缺 `email`，应该只看到场景 2
那条 `data.required`，这条断言错误不会一起出现。

### 场景 5：折扣精度超过币种允许位数（`assertion.failed`）

在场景 1 基础上，把 `discount` 改成 `25.505`（`currency` 仍是 `USD`，`currencyScale=2`，
`decimalPlaces(25.505)=3`）。

预期错误：

```json
[
  {
    "category": "assertion",
    "code": "assertion.failed",
    "instancePath": "/discount",
    "schemaPath": "#/x-feel-assertions/1",
    "keyword": null,
    "assertionId": "discount-scale-matches-currency",
    "messageKey": "discount.invalidScale",
    "arguments": {}
  }
]
```

### 场景 6：客户号查询超时（`upstream.lookupTimeout`）

在场景 1 基础上，把 `customerId` 改成 `"CUST-9999"`，并假定测试桩把
`existsInReferenceSet` 模拟为超时（不是"查不到"，是"没查完"）。

预期错误：

```json
[
  {
    "category": "upstream",
    "code": "upstream.lookupTimeout",
    "instancePath": "/customerId",
    "schemaPath": "#/x-feel-assertions/0",
    "keyword": null,
    "assertionId": "customer-exists",
    "messageKey": "system.lookupUnavailable",
    "arguments": { "function": "existsInReferenceSet", "timeoutMs": 3000 }
  }
]
```

> **待确认（开放问题）**：设计文档 8.2/9.3 里 `messageKey` 是"断言为 `false`"时展示给用户的
> 文案键，没有定义"断言因上游故障而无法求值"时该用哪个 key。这里先用一个固定的系统级
> `system.lookupUnavailable` 占位，而不是复用 Form DSL 里该断言自己的 `customerId.notFound`
> ——因为"没查完"和"查完了但不存在"在业务语义上不该提示同一句话。是否要在 `x-feel-assertions`
> 的 schema 里为每条断言额外加一个可选的 `upstreamMessageKey`，需要和 10 节第 6 项一起定。

### 场景 7：`x-feel-required` 表达式结果非布尔（`schema.feelResultType`）

发布一份改坏的 Form DSL 变体（其余不变）：

```json
{
  "x-feel-required": {
    "email": "data.channel"
  }
}
```

`data.channel` 求值结果是字符串 `"email"`，不是严格布尔值。

预期错误（无论提交什么数据都会先于数据校验触发，属于服务端故障）：

```json
[
  {
    "category": "schema",
    "code": "schema.feelResultType",
    "instancePath": "/email",
    "schemaPath": "#/x-feel-required/email",
    "keyword": "x-feel-required",
    "assertionId": null,
    "messageKey": null,
    "arguments": { "expected": "boolean", "actual": "string" }
  }
]
```

### 场景 8：`{{ }}` 内 FEEL 语法错误（`schema.feelSyntax`）

发布一份改坏的 Form DSL 变体：

```json
{
  "discount": {
    "type": "number",
    "minimum": "{{ data.minAmount + }}",
    "maximum": "{{ data.maxAmount }}"
  }
}
```

预期错误：

```json
[
  {
    "category": "schema",
    "code": "schema.feelSyntax",
    "instancePath": "/discount",
    "schemaPath": "#/properties/discount/minimum",
    "keyword": "minimum",
    "assertionId": null,
    "messageKey": null,
    "arguments": { "expression": "data.minAmount + " }
  }
]
```

## 6. 尚未覆盖、留给下一份 fixture 的场景

- 嵌套对象、数组 `items` 内的 `x-feel-required`/`x-feel-assertions`（9.2 提到的 `value` 作用域
  切换，还没有测试向量验证）；
- `exclusiveMinimum`/`exclusiveMaximum` 的动态化与冲突检测（`minimum` 与 `exclusiveMinimum`
  同时出现应判为 `schema.conflictingBound`）；
- `format` 白名单外的值（如 `format: "ipv4"`）在发布期应被拒绝，本 fixture 未覆盖；
- `x-feel-assertions` 中同一条断言的 `assert` 执行异常（非语法错误、非布尔结果，而是运行时
  抛异常）对应的 `schema.feelEvaluation` 场景。
