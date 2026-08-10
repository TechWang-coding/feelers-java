# 基于 JSON Schema 的统一数据校验设计

## 1. 背景与目标

用户在前端从字段配置表中选择 N 个字段绑定到一个自定义 Form，并配置字段间的条件关系。前端提交表单定义后，后端解析字段模型与 FEEL 规则，生成版本化的 JSON Schema；前后端再对同一份表单数据采用该 Schema 校验，避免字段名、类型、必填规则、枚举值和嵌套结构分别维护而产生漂移。

字段配置与 Form 定义是规则的编辑来源；由后端生成、发布并版本化的 JSON Schema 是运行时校验的唯一事实来源（single source of truth）。

本阶段目标：

- 选定并统一采用 **JSON Schema Draft 2020-12**；
- 为每类请求、响应及可持久化业务对象定义带稳定 `$id` 的 Schema；
- 前端在提交前提供即时校验，后端在接收数据时执行权威校验；
- 统一校验错误格式，供 UI 定位字段并向调用方返回可读错误；
- 支持 Schema 的版本演进、兼容性检查和自动化测试。
- 支持将可静态转译的 FEEL 条件编译为 JSON Schema 的 `allOf`、`anyOf`、`not` 和 `if`/`then`/`else`。

非目标：JSON Schema 不承担权限、跨记录一致性、数据库唯一约束、远程依赖查询、相对当前时间的动态比较等业务规则；这些规则仍由服务端 FEEL/业务层执行。

## 2. 范围与职责

| 层级 | 职责 |
| --- | --- |
| Schema 仓库/目录 | 维护 Schema、公共定义、示例与变更记录 |
| 前端 | 使用同版本 Schema 做表单提示和提交前校验；不得将其作为安全边界 |
| 后端 | 在路由/DTO 进入业务逻辑前校验请求；响应可在测试或非生产环境做契约校验 |
| 业务层 | 执行 JSON Schema 无法描述的业务与权限规则 |
| CI | 校验 Schema 自身、引用完整性、示例以及兼容性 |

后端校验结果为最终结论。前端校验仅改善体验，不能替代后端校验。

## 3. 目录与命名约定

建议在仓库根目录新增如下结构：

```text
schemas/
  common/                 # 可复用定义，例如 id、时间、分页
  requests/               # API 请求体
  responses/              # API 响应体
  domain/                 # 领域对象与事件
  examples/                # 合法/非法 JSON 样例
```

- 文件名使用 kebab-case，例如 `create-template.request.schema.json`。
- `$id` 使用稳定、可解析的 URI，例如 `https://schemas.example.com/feelers/requests/create-template/1.0.0`。
- Schema 内优先使用 `$defs` 和 `$ref` 复用，禁止复制粘贴公共字段定义。
- 对外传输对象默认设置 `additionalProperties: false`；确实允许扩展时，需明确记录扩展字段策略。

## 4. Schema 编写基线

所有 Schema 必须声明 `$schema`、`$id`、`title` 和可读的 `description`。对象应明确 `type`、`properties`、`required`，字符串应包含合理的 `minLength`/`maxLength`/`pattern`，数组应定义 `items` 及必要时的 `minItems`/`maxItems`。

示例（创建模板请求）：

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://schemas.example.com/feelers/requests/create-template/1.0.0",
  "title": "Create template request",
  "type": "object",
  "additionalProperties": false,
  "required": ["name", "template"],
  "properties": {
    "name": {
      "type": "string",
      "minLength": 1,
      "maxLength": 100,
      "description": "模板显示名称"
    },
    "template": {
      "type": "string",
      "minLength": 1,
      "maxLength": 10000,
      "description": "FEELers 模板正文"
    },
    "strict": {
      "type": "boolean",
      "default": false
    }
  }
}
```

注意：`format`（如 `date-time`、`email`）是否断言有效取决于校验器配置；前后端须显式开启相同的 format 校验策略，不能仅假设关键字必然生效。

## 5. 校验流程与错误契约

```text
客户端编辑 → 前端 JSON Schema 校验 → 提交请求
                                      ↓
                             后端 JSON Schema 校验
                                      ↓
                    业务规则/权限校验 → 执行业务逻辑 → 返回响应
```

无论前后端所用语言，错误应适配为统一结构：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请求数据校验失败",
  "errors": [
    {
      "instancePath": "/name",
      "schemaPath": "#/properties/name/minLength",
      "keyword": "minLength",
      "message": "长度不得少于 1 个字符"
    }
  ]
}
```

- `instancePath` 使用 JSON Pointer，供前端准确映射字段；根对象使用空字符串。
- `schemaPath`、`keyword` 保留机器可读信息，`message` 为面向用户或日志的文本。
- 后端对格式错误返回 HTTP 400；不要泄露内部实现、堆栈或未授权字段值。

## 6. 前后端实现要求

1. 前端打包对应 Schema（或通过构建产物统一分发），在表单变更和提交时校验；服务器返回的 `errors` 必须仍可覆盖到表单提示。
2. 后端在应用启动时加载并预编译全部 Schema，解析 `$ref` 失败应使启动失败；不得为每个请求重新加载 Schema。
3. 两端必须固定支持 Draft 2020-12 的校验器版本，并在依赖升级时运行同一组契约测试。
4. 运行时按 `$id` 或明确的路由—Schema 映射选择 Schema，禁止由客户端任意指定要加载的 Schema URI。
5. Schema 校验通过后再做对象反序列化/业务处理，避免宽松 DTO 悄然接受额外字段。

## 7. 版本与兼容性策略

- `$id` 中包含语义化版本；修订内容也须更新变更记录。
- 新增可选字段通常为向后兼容变更；新增必填字段、收紧范围/枚举、删除或改名字段均视为破坏性变更。
- 破坏性变更创建新的主版本 Schema，并在 API 迁移期同时支持旧版本；明确弃用日期和迁移说明。
- CI 对比当前 Schema 与基线版本，检测破坏性变更；所有变更必须新增或调整正反例。

## 8. 初始化交付项与验收标准

1. 建立 `schemas/` 目录、公共定义以及至少一个请求 Schema（可从“创建模板”开始）。
2. 选定前端和 Java 后端均支持 Draft 2020-12 的校验器，并封装统一的加载、校验和错误适配接口。
3. 为每个 Schema 提供至少一个合法样例和一个非法样例，纳入自动化测试。
4. 后端入口对试点 API 启用强制校验，并按第 5 节返回标准错误体。
5. CI 包含：Schema 元校验、`$ref` 校验、样例校验、前后端契约测试和兼容性检查。
6. 文档记录当前 Schema 清单、所有者、版本、关联 API 与迁移状态。

验收完成后，任意试点 API 的同一非法 JSON 在前端与后端都能定位到相同的 `instancePath` 和规则关键字；后端不会因前端绕过而接受不符合契约的数据。

## 9. 自定义 Form 的规则来源与生命周期

系统中有字段配置表。用户在表单设计器中选择字段、调整顺序、设置表单级基础规则，并构造字段间的条件关系；前端将完整 Form 定义提交给后端。后端负责校验定义、生成 Schema、保存版本并发布。

```text
字段配置表 → 前端表单设计器 → 提交 Form 定义
                                      ↓
                    后端校验定义并编译 FEEL / 生成 JSON Schema
                                      ↓
                      保存不可变 Form 版本和已编译 Schema
                                      ↓
        前端加载已发布版本 → 用户填写数据 → 后端按版本取 Schema 校验
```

必须区分两个请求：

1. **设计/发布请求**：前端发送字段数组和规则，后端生成并发布 Schema。
2. **填写/提交请求**：前端只发送 `formKey`、`formVersion` 和实际 `data`；后端按 `formKey + formVersion` 查询已发布 Schema 后校验，绝不信任本次请求中携带的字段配置作为校验依据。

第二条保证客户端无法通过篡改字段定义绕过校验，也让历史数据可以按原 Form 版本回放和审计。

## 10. 基础字段数组模型

以下模型是前端表单设计器与后端生成器之间的建议协议。真实业务可扩展展示文案、控件类型、权限和布局属性；未声明的属性不应直接进入生成后的 JSON Schema。

```json
{
  "formKey": "user-profile",
  "title": "用户资料",
  "fields": [
    {
      "key": "age",
      "label": "年龄",
      "type": "integer",
      "required": true,
      "minimum": 0,
      "maximum": 150
    },
    {
      "key": "sex",
      "label": "性别",
      "type": "string",
      "enum": ["Male", "Female"]
    },
    {
      "key": "county",
      "label": "国家/地区代码",
      "type": "string",
      "pattern": "^[A-Z]{2}$"
    },
    {
      "key": "description",
      "label": "说明",
      "type": "string",
      "minLength": 50
    }
  ],
  "conditionalRules": [
    {
      "id": "description-required-by-age-sex",
      "when": "age < 18 or (age > 60 and sex = \"Female\")",
      "then": {
        "required": ["description"]
      },
      "message": "满足条件时必须填写不少于 50 字的说明。"
    }
  ]
}
```

字段模型的最小语义如下：

| 属性 | 含义 | JSON Schema 映射 |
| --- | --- | --- |
| `key` | 唯一字段标识，仅允许受控字符集 | `properties` 的键 |
| `type` | `string`、`integer`、`number`、`boolean`、`object`、`array` | `type` |
| `required` | 静态必填标记 | 根 `required` 数组 |
| `enum` | 固定可选值 | `enum` |
| `minimum`/`maximum` | 数值边界 | 同名关键字 |
| `minLength`/`maxLength` | 字符串长度边界 | 同名关键字 |
| `pattern` | 字符串正则约束 | `pattern` |
| `format` | 格式标识，例如 `date`、`email` | `format` |

`required: false` 或缺失表示字段可缺失，并不表示字段一旦传入就可以跳过其类型、正则或长度限制。例如 `description` 不在根 `required` 中时可以不提交；但一旦提交，其值仍必须满足 `minLength: 50`。

后端在保存 Form 定义前必须校验：字段 `key` 唯一、字段类型与约束匹配、正则可编译、枚举值符合类型、条件规则引用的字段存在，且 `then` 所引用的字段已经绑定到该 Form。

## 11. FEEL 条件到 JSON Schema 的编译

FEEL 是规则作者的表达语言，不能由标准 JSON Schema 直接执行。生成器必须在后端将受支持的 FEEL 表达式解析为 AST（抽象语法树），完成字段存在性与类型检查后，再生成 Schema；禁止使用字符串替换生成 JSON。

### 11.1 支持的静态子集

| FEEL 表达式 | 编译目标 |
| --- | --- |
| `field = "value"` | `required` + `properties[field].const` |
| `field != "value"` | `required` + `not`/`const` |
| `field > n`、`>= n`、`< n`、`<= n` | `exclusiveMinimum`、`minimum`、`exclusiveMaximum`、`maximum` |
| `a and b` | `allOf: [A, B]` |
| `a or b` | `anyOf: [A, B]` |
| `not a` | `not: A` |
| 括号分组 | 生成对应层级的嵌套 `allOf`/`anyOf` |
| `count(items) > n` | 数组字段的 `minItems` |
| `user.level = "vip"` | 嵌套 `properties`，并为经过的字段生成 `required` |

条件中每个参与比较的字段都必须生成 `required`。原因是 `properties` 只校验存在的属性；若不加 `required`，字段缺失时条件分支可能被错误地视为通过。

以上例中的 `when` 将被编译为：

```json
{
  "if": {
    "anyOf": [
      {
        "required": ["age"],
        "properties": {
          "age": { "exclusiveMaximum": 18 }
        }
      },
      {
        "allOf": [
          {
            "required": ["age"],
            "properties": {
              "age": { "exclusiveMinimum": 60 }
            }
          },
          {
            "required": ["sex"],
            "properties": {
              "sex": { "const": "Female" }
            }
          }
        ]
      }
    ]
  },
  "then": {
    "required": ["description"]
  }
}
```

每条条件规则作为根 Schema `allOf` 数组中的一个元素，故多条规则之间是同时生效的关系。

### 11.2 运行时 FEEL 规则

以下表达式不能可靠地转换为标准 JSON Schema，必须保留为运行时 FEEL 校验：

- `date(effectiveDate) > today()` 等相对当前时间的比较；
- `sum(items.price) > 100` 等聚合计算；
- 权限判断、跨记录查询、外部服务或自定义函数。

源定义可将其显式标记为 `runtimeRules`。后端先做 JSON Schema 校验，再以可信业务时区和固定 FEEL 函数集执行运行时规则，并将失败结果统一适配为第 5 节的错误结构。运行时规则不能静默跳过；不支持的 FEEL 语法或类型不匹配必须在发布阶段失败。

## 12. 后端生成、发布与校验设计

后端是唯一的 Schema 编译和发布方，建议流程如下：

1. 接收 Form 定义及字段数组，完成输入和权限校验。
2. 从字段配置表解析被绑定字段，并将字段定义快照写入待发布 Form 版本。
3. 合并基础字段约束：生成 `properties`、静态 `required`、`additionalProperties: false`。
4. 解析每条 `conditionalRules.when`，编译可静态表达式并追加到根 `allOf`。
5. 为运行时规则保留受控的 `x-feel.runtimeValidation` 元数据，不将其伪装为 JSON Schema。
6. 运行 JSON Schema 元校验、引用校验、正反例测试和 FEEL 编译测试；任何一步失败均不得发布。
7. 生成不可变的 `formVersion` 与 `$id`，保存编译产物并预编译/缓存校验器。

用户提交填写数据时，API 必须使用请求中的 `formKey + formVersion` 加载已发布版本；数据结构示例：

```json
{
  "formKey": "user-profile",
  "formVersion": 3,
  "data": {
    "age": 16,
    "sex": "Female",
    "description": "这里是长度不少于五十个字符的说明文字，用于满足该表单的条件校验要求。"
  }
}
```

后端不得使用本次填写请求携带的字段数组或 Schema 执行校验。修改 Form 需要创建新版本；已发布版本不可就地修改，以保证并发填写、历史数据审计、回滚和重放的一致性。

## 13. Ant Design 前端校验设计

Ant Design Form 负责控件渲染、值管理、字段依赖刷新和错误展示；JSON Schema 校验器负责整份表单的权威前端预校验。推荐前端使用支持 Draft 2020-12 的 AJV 实现，加载后端生成的相同 `formVersion` Schema。

```text
已发布 Form 定义 + JSON Schema
        ↓
Ant Design Form 渲染字段
        ↓
用户修改值 → 防抖执行整份数据的 AJV 校验
        ↓
AJV 错误（JSON Pointer）→ Ant Design NamePath → form.setFields
        ↓
提交前完整校验 → 提交 formKey、formVersion、data
```

不要将复杂的条件规则重复翻译为 `Form.Item.rules`；这会让前端规则与 Schema 分叉。`Form.Item.rules` 可用于简单交互提示，但提交前必须以整份 JSON Schema 数据的校验结果为准。

字段间的显示、禁用和重新渲染可使用 Ant Design 的 `dependencies`，并可保留单独的 `x-feel.ui` 表达式。例如 `description` 依赖 `age` 和 `sex` 的变化重新计算可见性；但 `dependencies` 不是安全校验，条件必填仍由编译后的 `if`/`then` 控制。

AJV 返回的 `instancePath` 是 JSON Pointer，前端需转换为 Ant Design `NamePath`。对于 `required` 错误，路径指向父对象，必须从 `missingProperty` 中取得实际缺失字段。日期控件通常输出 `dayjs` 对象，提交和校验前应统一序列化为 `YYYY-MM-DD` 等 Schema 约定的 JSON 值。
