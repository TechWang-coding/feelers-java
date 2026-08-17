# JSON Schema 与 FEEL 动态校验设计（草案）

> 状态：草案，待下一阶段细化
>
> 范围：前端与 Java 后端共用的表单/数据校验契约
>
> 最后更新：2026-08-17

## 1. 背景与目标

系统需要在前端输入阶段和后端提交阶段对同一份数据执行一致的校验。JSON Schema
适合描述稳定、局部、无需计算的数据约束；FEEL 适合表达依赖当前数据、日期、金额或其他
字段的关系规则。

本设计的目标是：

- 以 **Form DSL JSON** 作为表单/数据规则的权威源，其中同时承载可转写为 JSON Schema 的静态
  结构和 `x-feel-*` 扩展（动态必填、动态数字边界、关系断言）；
- 以 JSON Schema 作为静态结构与基础数据类型校验的共同标准，且始终是从 Form DSL JSON 生成、
  不含任何 `x-feel-*` 扩展的纯 Draft 2020-12 产物；
- 以 FEEL 作为字段关系与动态约束的共同计算语言；
- 在校验前基于 Form DSL JSON 与当前数据，由 FEEL 计算出的动态约束生成一份有效的纯 JSON
  Schema；`x-feel-assertions` 关系断言不进入这份生成的 JSON Schema，而是直接对 Form DSL JSON 求值；
- 保证前端与后端使用相同的扩展语法、求值顺序、错误分类与日期语义；对同一条规则，两端要么
  给出相同结论，要么前端不执行它——前端校验始终是后端校验的真子集（见 2.1）；
- 不允许执行任意宿主语言代码，也不允许由请求数据注册或修改表达式函数。

本设计不定义 UI 渲染、字段展示/隐藏、表单状态管理、权限判断或规则编辑器。

## 2. 职责边界

| 规则类型 | 责任方 | 示例 |
| --- | --- | --- |
| 对象、数组、字符串、数字、枚举、固定必填、未知字段 | JSON Schema | `type`、`properties`、`required`、`enum`、`additionalProperties` |
| 固定数值边界 | JSON Schema | `minimum: 0`、`multipleOf: 0.01` |
| 某字段是否必填取决于另一字段 | FEEL + JSON Schema | `email` 在 `data.channel = "email"` 时必填 |
| 数值边界引用当前数据中的另一个字段 | FEEL + JSON Schema | `maximum: "{{ data.maxAmount }}"` |
| 日期相对今天、相对另一日期、工作日规则 | FEEL | `date(data.dueDate) >= businessDay(clock.today, 1)` |
| 数值边界含运算（折扣、手续费、税费等，见 5.1） | FEEL | `value.discount <= bigDecimalCalc('value.price * value.discountRate')` |
| 字段值是否存在于外部/共享数据源（受控查询函数，8.2） | FEEL | `existsInReferenceSet("customerType", value.customerType)` |

`{{ }}` 动态数字关键字只承载**引用** 和 **条件**：表达式求值后必须直接得到一个已存在的数值，不得包含
算术运算或计算型函数调用。所有数值计算都放在 `x-feel-assertions` 里，理由与写法见 5.1。

以下 JSON Schema 关键字不作为产品规则作者表达字段关系的方式：`oneOf`、`anyOf`、
`not`、`dependentRequired`、`if`/`then`/`else`。它们虽然能表示部分条件，但会使条件语言
分散在 JSON Schema 与 FEEL 两处。`allOf` 仅可用于纯静态 schema 复用，不能承载字段关系。

### 2.1 表达式边界

上表划分的是"用哪种机制"，本表划分的是"表达式里能写什么、写了之后在哪一端执行"。同一种
机制下，表达式的写法不同，可执行的位置也不同：

| 规则 | 示例 | 前端支持 | 后端支持 |
| --- | --- | --- | --- |
| `x-feel-required` | `data.channel = "email"` | ✅ | ✅ |
| `x-feel-assertions`（日期） | `date(value.dueDate) >= businessDay(clock.today, 1)` | ✅ ※ | ✅ |
| `x-feel-assertions`（数值边界含运算，扩展函数） | `value.discount <= bigDecimalCalc('value.price * value.discountRate')` | ✅ | ✅ |
| `x-feel-assertions`（数值边界含运算，裸运算符） | `value.discount <= value.price * value.discountRate` | ❌ 浮点运算结果不可靠 | ✅ |
| `x-feel-assertions`（数据源查询） | `existsInReferenceSet("customerType", value.customerType)` | ❌ 无网络访问能力 | ✅ |
| `{{ }}` 动态边界（引用） | `maximum: "{{ data.maxAmount }}"` | ✅ | ✅ |
| `{{ }}` 动态边界（含运算） | `maximum: "{{ data.price * data.rate }}"` | ❌ 不允许 | ❌ 不允许 |

※ 前提是 `businessDay` 依赖的节假日/补班日历在前端可得；若该日历只在后端持有，此行前端列
同样为 ❌（数据可得性问题，与精度无关）。

由此得到两条边界规则：

- **`{{ }}` 内只能写引用**，含运算的写法两端都不接受，发布期即为 Schema 配置错误（第 5 节）；
  数值计算一律改写成 `x-feel-assertions`。
- **前端校验是后端校验的真子集**：前端只执行前端列为 ✅ 的规则，可以少报错误（后端兜底），但
  绝不能多报，也不能对同一条规则给出与后端不同的结论。

某条规则是否为后端专属由发布期校验器（10.1）解析表达式自动推导，判据只有两条：表达式调用了
8.2 的受控查询函数，或表达式使用了裸算术运算符。Form DSL 作者不需要、也不能手工声明执行位置，
以免标注错误破坏上述子集性质。

## 3. 校验模型

**Form DSL JSON** 是发布、存储与编辑的权威源：一份既包含可转写为 JSON Schema 的静态结构，
也包含 `x-feel-required`、动态数字关键字（`{{ }}`）、`x-feel-assertions` 等 `x-feel-*` 扩展的
JSON 文档。每次校验从它生成一份**纯 JSON Schema**（标准 Draft 2020-12，不含任何 `x-feel-*`
键、不含任何 `{{ }}` 字符串），交给标准 validator 处理结构与静态/动态数值校验；`x-feel-assertions`
不参与这次生成，而是单独、直接对 Form DSL JSON 求值。

```mermaid
flowchart LR
  A["Form DSL JSON"] --> E["解析 x-feel-required"]
  A --> F["解析 {{ }} 数值约束"]
  C["待校验数据"] --> D["FEEL 上下文"]
  D --> E
  D --> F
  E --> F
  F --> G["生成 JSON Schema（纯 Draft 2020-12，不含 x-feel-*）"]
  C --> H["JSON Schema Validator"]
  G --> H
  H --> I["结构与值错误"]
  A --> J["对 x-feel-assertions 求值（直接读取 Form DSL JSON）"]
  D --> J
  H -. 无 schema/data 错误时 .-> J
  J --> K["关系断言错误"]
```

1. 不修改 Form DSL JSON；每次校验据其生成一份新的 JSON Schema，Form DSL JSON 本身不会被
   当作 Schema 直接交给 validator。
2. 以待校验数据和受限、预先注册的 FEEL 函数构造表达式上下文。
3. 执行 Form DSL JSON 中的 `x-feel-required`，将结果为 `true` 的字段名合并到生成的 JSON
   Schema 的 `required`。
4. 解析 Form DSL JSON 数字关键字中以 `{{ ... }}` 标记的 FEEL 表达式；成功求得有限数字后写入
   生成的 JSON Schema 对应关键字的值。
5. 第 3、4 步的输出是一份纯 JSON Schema：`x-feel-required`、`x-feel-assertions`、`{{ }}` 表达式
   字符串都只保留在 Form DSL JSON 里，不会出现在生成的 JSON Schema 中。
6. 使用标准 JSON Schema validator 校验数据；只有在没有 Schema 配置错误且没有数据错误时，才
   对 Form DSL JSON 中的 `x-feel-assertions` 求值——这一步直接读取 Form DSL JSON，不经过第 5 步
   生成的 JSON Schema。
7. 将表达式配置错误与数据校验错误分别返回，不能把前者伪装成用户输入错误。

表达式只能读取本次数据和引擎允许的函数；不得读网络、文件、浏览器/服务器全局状态，
不得修改数据或 Form DSL JSON（8.2 的受控查询函数除外，其边界见 8.2）。

## 4. 条件必填：`x-feel-required`

`x-feel-required` 是 Form DSL JSON 中对象节点上的扩展关键字，与 `required` 同级；它只存在于
Form DSL JSON 里，解析后合并进生成的 JSON Schema 的 `required`，自身不会原样出现在输出中：

```json
{
  "type": "object",
  "required": ["channel"],
  "x-feel-required": {
    "email": "data.channel = \"email\"",
    "phone": "data.channel = \"sms\""
  },
  "properties": {
    "channel": { "type": "string", "enum": ["email", "sms"] },
    "email": { "type": "string", "format": "email" },
    "phone": { "type": "string", "pattern": "^\\+[1-9]\\d{6,14}$" }
  },
  "additionalProperties": false
}
```

处理约定：

- 键为需要动态必填的直接字段名；该字段必须同时出现在当前对象的 `properties` 中。
- 值为返回布尔值的 FEEL 表达式；仅严格等于 `true` 时加入 `required`。
- 静态 `required` 永远保留；动态结果只追加、不删除。
- 语法、未注册函数、运行时 warning、执行异常或结果不是布尔值时，返回 schema 配置错误，终止本次校验。
- 嵌套对象中的动态必填由该嵌套对象自己的 `x-feel-required` 处理；`data`、`value` 的作用域
  与路径规则见 9.2。

## 5. 动态数字关键字

动态边界直接写入字段的标准数字关键字。值为 JSON number 时是静态约束；值为完整的
`{{ ... }}` 字符串时是 FEEL 表达式。花括号以外的任何文本、空表达式以及不是字符串或 number
的值都是 Schema 配置错误。

`{{ }}` 表达式只能是**引用**：求值后直接得到数据中已存在的一个数值。表达式内不得出现算术
运算符或计算型函数调用；需要计算才能得到的边界一律写成 `x-feel-assertions`（见 5.1）。这样
`{{ }}` 这条链路上不存在任何运算，前后端只是把同一个已有数值搬进生成的 JSON Schema，不会
因为计算精度产生分歧。违反此限制在发布期即为 Schema 配置错误。

这使 Form DSL JSON 成为平台的“可动态解析 Schema 定义”，而不是可直接交给通用 validator 的纯
Draft 2020-12 Schema：带表达式的 `minimum` 在解析前确实不通过标准元 Schema。发布期必须先用
平台的 Form DSL JSON 校验器验证 `{{ }}` 形状与 FEEL 语法；每次运行时据其生成的 JSON Schema
则必须是纯 Draft 2020-12 Schema、不含任何 `x-feel-*` 键，并接受标准元校验和数据校验。

```json
{
  "type": "object",
  "properties": {
    "minAmount": { "type": "number" },
    "maxAmount": { "type": "number" },
    "discount": {
      "type": "number",
      "minimum": "{{ data.minAmount }}",
      "maximum": "{{ data.maxAmount }}"
    },
    "strictDiscount": {
      "type": "number",
      "exclusiveMinimum": "{{ data.minAmount }}",
      "exclusiveMaximum": "{{ data.maxAmount }}"
    }
  }
}
```

允许动态化的关键字：

| 关键字 | JSON Schema 含义 | 动态求值后的要求 |
| --- | --- | --- |
| `minimum` | 值大于或等于边界 | 有限数字 |
| `maximum` | 值小于或等于边界 | 有限数字 |
| `exclusiveMinimum` | 值严格大于边界 | 有限数字 |
| `exclusiveMaximum` | 值严格小于边界 | 有限数字 |
| `multipleOf` | 值是该数的倍数 | 大于零的有限数字（v1 不允许动态化） |

一个数字关键字只能有一个值，故静态 number 与动态表达式天然互斥。`minimum` 与
`exclusiveMinimum`、`maximum` 与 `exclusiveMaximum` 也不得同时生效。每个表达式必须求得有限 JSON number；`null`、缺失值、
字符串、NaN 与无穷大表示本次数据无法提供动态边界，属于数据校验错误而非 Schema 配置错误。

动态 `multipleOf` 在 v1 明确禁止。Java 的任意精度十进制与 JavaScript number 的倍数运算
没有天然的一致精度模型；金额等需要精确整除的领域应使用缩放后的整数（例如分）或在 FEEL
关系断言中使用经双方契约测试的十进制函数，不能依赖浮点余数。更完整的数值精度要求见 5.1。

### 5.1 浮点边界校验：避免计算精度误差

数值边界判断不能被浮点运算误差影响。边界按"是否需要计算"分流：

| 边界来源 | 典型场景 | 落地方式 |
| --- | --- | --- |
| 引用已有数值 | 授信剩余额度、当日累计交易额度、余额充足性 | 第 5 节动态数字关键字（`{{ }}`），链路上无运算 |
| 需要计算 | 折扣金额上限、手续费上限、税费、汇率换算金额、利息 | `x-feel-assertions`，写法见下 |

需要计算的边界有两种写法，取舍是"可移植性"与"表达力"：

| 写法 | 示例 | 执行位置 |
| --- | --- | --- |
| `bigDecimalCalc` 扩展函数 | `value.discount <= bigDecimalCalc('value.price * value.discountRate')` | 前后端一致 |
| 裸算术运算符 | `value.discount <= value.price * value.discountRate` | 仅后端（2.1） |

要求：

- `bigDecimalCalc(expr)` 由平台注册，参数是一段算术表达式字符串，函数内部以十进制精确语义
  解析求值：后端用 `BigDecimal`，前端用十进制安全数值库（如 decimal.js/big.js）。表达式内
  可引用 9.2 的上下文变量（`data`/`value`）。两端对同一批数值测试向量必须给出逐位一致的
  结果，纳入 10 节第 4 项的公共 fixture。
- 该参数字符串由 Form DSL 作者书写，不来自请求数据；发布期校验器（10.1）必须一并校验其语法
  与引用路径，运行时不接受来自数据的表达式文本（8.1）。
- 扩展函数负责运算精确；其返回值参与的比较运算（`<`、`<=`、`=` 等）仍由 FEEL 原生运算符完成。
  该组合在 17 位有效数字以内与全程十进制比较结果一致，超出部分不保证——需要更高精度的场景
  应改用缩放整数（例如以分为单位）表达。
- 裸算术运算符只在后端可用：后端 FEEL 引擎以 `BigDecimal` 求值，结果精确；前端 FEEL 引擎使用
  宿主语言原生浮点数，同一表达式可能得出不同结论，因此这类断言前端一律不执行。
- 涉及乘法/换算且结果需要落到某个精度的表达式，必须显式声明舍入模式（`round(value, scale,
  mode)` 的 `mode`：`HALF_UP`/`HALF_EVEN`/`CEILING`/`FLOOR` 等），不依赖语言默认行为。具体
  支持哪些模式、默认值是什么，留待 10 节实施阶段定义。
- 精度表示不同导致的"理论相等"比较失败，用统一十进制语义消除，不用容差（epsilon）掩盖。

## 6. 日期规则

日期字段的基础结构可使用 JSON Schema 表达，例如 `type: "string"` 与静态必填。但 JSON
Schema 没有可引用另一字段或当前日期的原生日期比较关键字。`format: "date"` 的强制行为依赖
具体 validator 配置；即使应用代码可对有效的 `YYYY-MM-DD` 使用词典顺序比较，也不能替代完整的
日期、时区和工作日业务语义。

因此：

- JSON Schema 只负责日期字段的存在性、字符串类型及其他静态结构；
- 日期格式使用 `format: "date"`，并按 9.1 在前后端都启用 format assertion；
- 大于今天、不早于另一日期、最大跨度、工作日等关系，全部写成 FEEL 关系断言（`x-feel-assertions`）；
- 今天、当前时间与业务时区只能读取调用方注入的 `clock`，例如
  `businessDay(clock.today, 1)`；前后端不得各自读取本机时间；
- 工作日计算依赖受限的共享函数契约；节假日/补班日历来源与前后端测试向量仍需定义。

## 7. 错误模型

至少区分以下三类错误：

| 类别 | 触发条件 | 面向对象 |
| --- | --- | --- |
| Schema 配置错误 | 非法扩展结构、FEEL 语法/求值失败、动态数字结果非法、关键字冲突 | schema 作者、系统管理员 |
| 数据校验错误 | JSON Schema 对输入数据校验失败 | 表单用户、调用方 |
| FEEL 关系断言错误 | 日期或字段关系表达式返回不满足结果 | 表单用户、调用方 |
| 上游依赖错误 | 受控查询函数（8.2）超时或数据源不可用 | 调用方/运维，可重试 |

上游依赖错误与前三类的关键区别：请求和数据本身可能是合法的，只是本次没能得到确定结果，
不能计入用户的数据错误、不应提示用户"改一下这个值"，调用方应能安全重试。

字段路径、schema 路径、稳定错误码和聚合结构见 9.4；前端与后端必须共享至少一组同构的
契约测试向量。

## 8. 安全与运行时约束

### 8.1 通用约束

- 仅允许平台预注册、确定性、无副作用的 FEEL 函数；例外见 8.2。
- 不执行 JavaScript、Java、反射、脚本或由请求/schema 临时注册的函数。
- schema 和数据都视为不可信输入；扩展关键字必须先完成形状和类型校验。
- 为表达式求值设置复杂度、递归/调用深度与超时/取消策略，具体限额由下一阶段确定。
- 不记录完整敏感数据或表达式上下文；日志仅保留必要的错误码、路径和安全诊断。

### 8.2 受控查询函数（参照完整性 / 数据源校验）

"字段值是否存在于外部数据源"一类校验（如客户类型、产品编码是否存在于参照数据）无法用静态
`enum` 表达，也不满足 8.1 默认的"无副作用、不访问网络"约束。v1 为此开放一类特例——**受控
查询函数**（如 `existsInReferenceSet`、`lookupMasterData`），仅在满足以下条件时允许使用：

- 函数必须由平台预先注册并审计；Form DSL JSON 与请求数据只能提供查询键（字段值、参照集合的
  逻辑名等），不能提供目标地址、库名、SQL 或任何决定"查询谁"的内容；
- 每个函数在注册时已绑定唯一确定的数据源与查询方式；同一函数名在不同环境指向不同物理
  地址属于部署配置，不是运行时可变的输入；
- 必须有强制的超时、重试上限与降级策略；查询超时或数据源不可用不等价于"值不满足"，需归入
  独立的"上游依赖错误"类别（见 7、9.4），不得伪装成 `assertion.failed`；
- 返回值只能是校验所需的布尔值或受限枚举，不得把数据源中其他字段透传进错误信息或日志；
- 同一次校验内对同一函数、同一查询键的重复调用应去重或缓存，避免重复外部查询放大延迟。

受控查询函数的注册清单、签名规范、超时/重试/降级细节与审计要求留待第 10 节实施阶段确定。

## 9. 建议冻结的 v1 契约

本节将此前影响互操作性的待决项收敛为建议的 v1 基线；实现开始前应以一组共享测试向量
确认所选依赖版本的实际行为。

### 9.1 Dialect 与 validator

- 所有发布的 Form DSL JSON 必须声明 Draft 2020-12 的 `$schema` 和稳定的 `$id`；含 `{{ }}` 的
  Form DSL JSON 是平台扩展格式，只有据其生成的 JSON Schema 才是标准 Draft 2020-12 Schema，
  且不包含任何 `x-feel-*` 键。
- 前端使用 `ajv/dist/2020` 与 `ajv-formats`；AJV 必须启用 strict mode，且只能加载已生成的
  Draft 2020-12 JSON Schema。AJV 不能在同一个实例中混用 Draft 2020-12 和旧 draft。
- Java 使用 networknt `json-schema-validator` 的 Draft 2020-12 dialect，并缓存发布的 Form DSL
  JSON 及其已验证的生成计划；请求不得提供 `$id`、`$ref` URI 或 Schema 内容来影响加载。
- 两端都将 `format` 作为断言，允许的 format 白名单为 `date`、`date-time`、`email`、`uri`。
  未知 format 在发布时即为配置错误。Draft 2020-12 默认仅把 format 当作 annotation，因此此项
  必须由 AJV 配置和 Java 执行配置显式开启。
- Java 端 JSON 反序列化必须开启十进制精确解析（Jackson `USE_BIG_DECIMAL_FOR_FLOATS` 或等价
  配置），使 JSON 数字直接由文本转成十进制类型，不经过 `double` 中转。否则高精度小数在进入
  FEEL 求值之前就已被截断到 double 精度，后端 `BigDecimal` 运算再精确也无法补回。

### 9.2 FEEL 作用域与路径

每次 FEEL 求值只提供下列只读变量，禁止把业务字段摊平为 FEEL 顶层变量：

| 变量 | 值 | 用途 |
| --- | --- | --- |
| `data` | 整个待校验 JSON 数据 | 跨层或根字段读取，如 `data.applicant.age` |
| `value` | 当前承载扩展关键字的对象 | 当前对象字段读取，如 `value.channel` |
| `clock` | 调用方注入的 `{ today, now, timeZone }` | 时间相关规则；禁止直接读机器时钟 |

这避免字段名覆盖 FEEL 内置函数或平台注册函数，也让嵌套对象与数组有唯一语义。`x-feel-required`
和 `x-feel-assertions` 位于 Form DSL JSON 的对象节点时，`value` 是该对象；数组 `items` 内对象同理。
动态边界位于字段节点时，`value` 是该字段的父对象。数组项的当前项不引入额外隐式变量；需要引用
它时，规则应位于 items 对象上并使用 `value`。

扩展的 `targetField` 是**字段路径**，使用 RFC 6901 JSON Pointer，锚定在调用方经 context 传入的
表单数据对象根，例如 `/dueDate`；空字符串表示根对象自身。该根对象与本节 FEEL 上下文里的
`data` 是同一份数据，只是两处入口不同：FEEL 表达式内用 `data`/`value` 读取，字段定位用
`targetField` 的 JSON Pointer。

`targetField` 必须能按该路径解析到 `properties` 中已声明的字段，因此它同时是该字段在本次校验
里的唯一键。`x-feel-assertions` 选择 JSON Pointer 的首要原因，是让断言错误的 `instancePath`
可直接等于 `targetField`，从而与 JSON Schema validator 产出的字段定位使用同一 RFC 6901 记法；
前端无需为 assertion 与 data 两类错误维护不同的定位模型。全文路径统一使用 JSON Pointer：
`targetField`、9.4 的 `instancePath` 指向数据，`schemaPath` 指向 Schema 文档内部位置。

FEEL 本身不读取 JSON Pointer：表达式一律通过 `data`/`value` 的 FEEL 点路径取值。若某个实现
适配器确实需要用 JSONPath 读取 `targetField` 指向的值，必须通过平台路径适配器调用已选定的库完成
解析与转义，而不能由业务代码简单替换字符串。前端适配器使用 `jsonpath-plus` 的 path/pointer 工具；
Java 适配器使用 Jackson `JsonPointer` 解析 RFC 6901 token，并将其交给 Jayway JsonPath 的确定路径
读取。适配器先将 `~1` 解为 `/`、`~0` 解为 `~`，再生成仅含确定属性和数组下标的 JSONPath bracket
notation；属性名中的 `\\` 与 `'` 必须按目标 JSONPath 库的字符串规则转义。例如 `/a~1b/x~0y/0` 转为
`$['a/b']['x~y'][0]`。不得通过简单替换 `/` 为 `.` 生成 JSONPath，也不得将包含 `*`、`..`、slice
或 filter 的 JSONPath 反向写入 `targetField`；一个 JSON Pointer 永远只定位一个确定节点。

v1 不考虑数组项内字段的定位，相关规则见 10 节待办。

### 9.3 FEEL 关系断言：`x-feel-assertions`

Form DSL JSON 的对象节点可以声明不适合转写成标准 JSON Schema 的断言。`x-feel-assertions` 只存在于
Form DSL JSON 中，不会被写入第 3 节生成的 JSON Schema；校验时直接对 Form DSL JSON 里的规则
数组求值，不对生成的 JSON Schema 做二次解析：

```json
{
  "type": "object",
  "x-feel-assertions": [
    {
      "id": "due-date-not-before-tomorrow",
      "assert": "date(value.dueDate) >= businessDay(clock.today, 1)",
      "targetField": "/dueDate",
      "errorMessage": "到期日不能早于下一个工作日"
    }
  ]
}
```

`x-feel-assertions` 每一项是一条数据，固定包含 `id`、`targetField`、`assert`、`errorMessage`
四个字段：

- `id` 在整个发布 Form 版本中唯一，且由 `[A-Za-z][A-Za-z0-9._-]{0,127}` 限制；用于错误对象里
  的 `assertionId`，也是去重和定位的稳定键。
- `targetField` 必填，是该断言指向字段的唯一键，记法与解析规则见 9.2。它既用于定位 UI 字段，
  也可按路径取到该字段的值；但取到的值只允许在前端本地用于展示定位与交互，**不得写入错误
  对象、消息文案或日志**（呼应 9.4「对外不返回原始数据」与 8.1 的日志约束）。
- `assert` 必须严格求值为 `true` 才通过；`false` 产生 FEEL 关系断言错误。warning、非布尔值或
  执行异常都属于 Schema 配置错误。
- `errorMessage` **可选**，是 `assert` 求值为 `false` 时展示给用户的文案，由 Form DSL 作者
  直接书写、不经过消息目录——这是刻意的：断言文案是表单作者针对具体业务语境写的一句话，不追求
  跨表单复用，也不像 `data`/`schema`/`upstream` 那些结构化错误一样有通用模板可抽。如果后续需要
  多语言，需要在 `errorMessage` 之上另行设计（本 v1 不做，`errorMessage` 只有一份文本）。
- 省略 `errorMessage` 时，使用引擎统一提供的默认兜底文案：
  `"{targetField} does not satisfy the business rule"`，其中 `{targetField}` 替换为该断言的
  字段路径（只填路径，不填字段值）。这个默认文案由平台侧统一维护，Form DSL 无法覆盖它
  的措辞，只能通过显式填写 `errorMessage` 来替换；因此发布期校验器（10.1）应对没有 `errorMessage`
  的断言给出警告，提醒作者用户会看到这句不够友好的默认话术。
- 若 `assert` 内调用了 8.2 的受控查询函数，且函数超时/数据源不可用，这不算 `assert` 求值为
  `false`，而是产生 `upstream` 类错误（见 7、9.4）。此时**不使用该断言的 `errorMessage`**，
  而是使用固定的系统级 `messageKey`（见 9.4）——"没查完"和"业务规则不满足"语义不同，不应该
  复用同一句业务文案。
- 标准 JSON Schema 先执行；只有没有 Schema 配置错误且没有数据错误时才执行关系断言。这使关系
  断言只面对已满足基础类型和必填约束的数据，避免把用户的缺失值或错误类型误报为断言配置失败。

### 9.4 统一错误对象

所有路径均以 JSON Pointer 报告：`instancePath` 指向数据中的字段（与 9.2 的 `targetField` 同一
套记法与锚点），`schemaPath` 指向 Schema 文档内部位置（关键字、断言下标）。这与 AJV、networknt
的原生输出一致，两端都不需要额外的路径转换层。

数组顺序与执行顺序一致。对外不返回 FEEL 堆栈、字段值等原始数据，也不返回底层 validator 的
本地化消息。

```json
{
  "category": "data",
  "code": "data.required",
  "instancePath": "/email",
  "schemaPath": "#/required",
  "keyword": "required",
  "assertionId": null,
  "messageKey": "validation.required",
  "message": null,
  "arguments": { "property": "email" }
}
```

```json
{
  "category": "assertion",
  "code": "assertion.failed",
  "instancePath": "/dueDate",
  "schemaPath": "#/x-feel-assertions/0",
  "keyword": null,
  "assertionId": "due-date-not-before-tomorrow",
  "messageKey": null,
  "message": "到期日不能早于下一个工作日",
  "arguments": {}
}
```

`category` 只能为 `schema`（发布或动态解析失败）、`data`（标准 JSON Schema 不满足）、
`assertion`（`x-feel-assertions` 断言为 false）或 `upstream`（8.2 受控查询函数超时/数据源
不可用）。错误码的 v1 最小集合是：
`schema.extensionShape`、`schema.feelSyntax`、`schema.feelEvaluation`、
`schema.feelResultType`、`schema.conflictingBound`、`data.dynamicBound`、
`data.<keyword>`、`assertion.failed`、`upstream.lookupTimeout` 与
`upstream.lookupUnavailable`。`schema` 类错误终止请求并以服务端故障处理；`data` 和
`assertion` 类错误可聚合并返回调用方；`upstream` 类错误应单独标出、允许调用方重试，不与
`data`/`assertion` 错误合并计数或提示用户修改数据。

`messageKey` 与 `message` 互斥，恰好一个非空：`schema`、`data`、`upstream` 三类错误使用
`messageKey`（配合 `arguments`），由调用方按平台消息目录渲染，此时 `message` 固定为 `null`；
`assertion` 类错误使用 `message`，直接取该断言的 `errorMessage`（省略时取 9.3 定义的默认
兜底文案），此时 `messageKey` 固定为 `null`、`arguments` 固定为 `{}`。两者分开的原因是
`x-feel-assertions` 的文案由表单作者直接撰写、不进消息目录，而其余错误的文案是平台侧统一
维护的固定模板，两种来源不能用同一个字段表达。

## 10. 剩余实施事项

1. 为 Form DSL JSON 编写发布期校验器：它必须验证 `{{ }}` 形状与"仅引用"限制（第 5 节）、
   允许使用的动态数字关键字、FEEL 语法、引用的函数与 `targetField` 路径；并推导每条规则的执行
   位置（2.1：是否调用受控查询函数、是否使用裸算术运算符），供前端据以跳过后端专属规则。
   这些都不能等到用户提交数据才发现。
2. 定义日期值模型、`clock` 的 JSON 形状、`businessDay` 的节假日/补班日历来源，并建立前后端
   相同的测试向量。
3. 定义“字段缺失或类型无效时关系断言应显式短路”的标准写法，并实现错误去重策略。
4. 编写包含嵌套对象、数组项、format、动态边界、断言失败和配置失败的公共 JSON fixture；Java
   和 TypeScript 两套测试必须消费同一份 fixture。
5. 基于上述契约设计 Java 后端实现与前端适配层。
6. 定义受控查询函数（8.2）的注册机制、函数签名规范、超时/重试/降级策略、审计日志要求，以及
   `upstream` 错误在网关/前端的重试与提示策略。
7. 补充 `upstream` 错误类别的前后端契约测试向量（超时、数据源不可用、部分失败等场景）。
8. 定义 `bigDecimalCalc` 的参数表达式语法子集（允许的运算符、变量引用形式、是否支持嵌套函数）
   与发布期校验方式，选定前端十进制安全数值库（如 decimal.js/big.js）并与后端 `BigDecimal`
   实现对齐，确定 `round(value, scale, mode)` 支持的舍入模式与默认值（5.1），建立前后端逐位
   一致的数值运算测试向量。
9. 明确前端 FEEL 引擎跳过后端专属规则后的 UI 表现：这些字段在提交前处于"未校验"而非"已通过"
   状态，需要与产品确认交互方式。
10. 定义数组项内字段的定位方式：`targetField` 锚定在数据根，静态声明时写不出运行时下标（9.2），
    需要确定下标占位记法或改为按当前项相对解析，并同步 `instancePath` 的表达方式。
11. 单独分析各数据类型（字符串、数字、日期、布尔、枚举）的校验规则划分：哪些留在 JSON Schema、
    哪些必须走 FEEL，以及 `targetField` 取值后类型不匹配时的处理方式。
