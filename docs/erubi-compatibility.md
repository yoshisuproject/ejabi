# Erubi Compatibility Notes

Ejabi follows Erubi's ERB-style template delimiters, but it is a Kotlin/JVM runtime renderer instead of a Ruby source-code generator. This document tracks the parts that map directly and the parts that intentionally do not.

## Implemented Template Syntax

| Erubi syntax | Ejabi status | Notes |
| --- | --- | --- |
| Plain text | Implemented | Text is copied as-is. |
| `<%= expr %>` | Implemented | HTML-escaped output. Escapes `& < > " '` plus `` ` `` and `=` — an intentional superset of Erubi's escape set (defense-in-depth for unquoted attributes). |
| `<%== expr %>` | Implemented | Raw output. |
| `<% code %>` | Partially implemented | Supports assignment plus Ejabi control flow, not arbitrary Ruby/Kotlin code. |
| `<%- code %>` | Partially implemented | Same executable subset as `<% code %>`, with Erubi-style standalone line trim. |
| `<%# comment %>` | Implemented | Ignored at render time. |
| `<%% ... %>` | Implemented | Emits a literal `<% ... %>` sequence. |
| `-%>` and `=%>` | Implemented | Trims following whitespace/newline for expression output. |
| Standalone code/comment line trimming | Implemented | Whitespace-only indentation and following newline are removed for code/comment lines. Works for both LF and CRLF templates. |
| `<%=== expr %>` | Not implemented | Erubi also explicitly does not support this Erubis debug-output form. |

## Ejabi-Specific Runtime Semantics

Ejabi interprets templates against a `Map<String, Any?>`. It supports expressions for literals, variables, property/index access, arithmetic, comparison, logical operators (`&&`, `||`, `!`), and ternary conditionals. It also supports structured control flow with:

- `<% if condition %> ... <% else %> ... <% end %>`
- `<% for item in items %> ... <% end %>`
- `<% var name = expression %>` or `<% name = expression %>`

Additional runtime behavior:

- Integer operands keep integer arithmetic (`1 + 2` renders `3`, `7 / 2` renders `3`). Integer division or remainder by zero throws `TemplateRenderException`; floating-point division by zero renders `Infinity`, like Ruby's `Float`.
- Numeric `==`/`!=` compare by numeric value across boxed types (`1` equals `1.0`), like Ruby. Integral values (`Long`, `BigInteger`) compare exactly without a floating-point round trip, so values that collapse to the same `Double` (e.g., `9007199254740993` and `9007199254740992`) are not equal.
- Ternaries nest right-associatively: both `a ? b : c ? d : e` and `a ? b ? c : d : e` parse, with the nested ternary belonging to the branch that contains it.
- String literals support `\n`, `\t`, `\r`, `\\`, `\'`, and `\"` escapes, and may contain the `%>` delimiter.
- Comparisons (`<`, `>`, `<=`, `>=`) work on numbers and on `Comparable` values such as strings.
- Block nesting is limited to 500 levels; deeper templates fail with `TemplateParseException` instead of a stack overflow.
- `EjabiEngine(strict = true)` turns missing variables, missing map keys, and missing members into `TemplateRenderException`. It also treats out-of-bounds or non-numeric indexes, indexing a null or non-indexable value, and type mismatches in arithmetic as errors. The default lenient mode renders all of these as empty output.
- Unary minus negates numeric values (`-count`, `-user.age`); negating a non-numeric value renders empty output, or throws in strict mode. Unary plus is accepted and ignored.
- Chained comparisons (`1 < a < 3`) are a syntax error; comparisons are non-associative.
- `for` iterates `Iterable`s, arrays (including primitive arrays such as `IntArray`), maps (over `entries`), and strings (over single-character strings); any other value throws `TemplateRenderException` at render time. The loop variable is restored after the loop.
- Assignments (`<% var x = expr %>` or `<% x = expr %>`) accept the full expression subset, including index access and member access. Anything the expression parser rejects — including method-call syntax such as `f(x)`, which is not part of the expression language — fails at compile time with `ExpressionParseException`.

### Error Message Format

Parse and render errors are prefixed with the 1-based template position and, when provided, the `fileName` passed to `compile`:

```
page.ejb:3:5: Undefined variable 'user'
math.ejb: 1:7: Division by zero
```

Output-tag errors point at the offending token inside the tag; code tags (`if`/`for`/assignments) report the tag position. Erubi achieves the same goal — errors locating back into template source — by preserving line numbers in generated Ruby code; Ejabi interprets templates, so positions travel with the AST instead (the expression language is parsed by its own ANTLR grammar, `EjabiExpr.g4`).

## Engine Options and Erubi Mapping

`EjabiEngine` exposes Erubi's options as constructor parameters (Kotlin callers should use named arguments; Java callers get positional overloads via `@JvmOverloads`):

- `escape` (default `true`): selects the escape matrix for output tags. When true, `<%= %>` escapes and `<%== %>` renders raw; when false the two tags swap roles, matching Erubi's `(indicator == '=') == escape` matrix. Note Erubi's own default is `false`; Ejabi defaults to `true` so escaping is on unless explicitly disabled.
- `trim` (default `true`): when false, standalone `<% %>`/`<%# %>` lines keep their indentation and line breaks, like Erubi's `trim: false`. The `-%>`/`=%>` close-tag trims are unaffected by this option, matching Erubi's tail handling.
- `escapeFunction`: a custom `EscapeFunction` (functional interface, usable from Java lambdas) applied whenever output resolves to escaped output — the equivalent of Erubi's `escapefunc`. Raw output never passes through it. The built-in default escapes `& < > " ' \`` and `=`.
- `compile(template, fileName)` / `compile(reader, fileName)`: annotates compile-time and render-time errors with the template name, equivalent to Erubi's `filename`. Unlike Erubi — one engine instance per template — Ejabi engines are long-lived and shared, so the name is passed per compile instead of per engine.

## Intentional Differences From Ruby Semantics

Ejabi follows Java/Kotlin conventions rather than Ruby's for these expression semantics:

- Truthiness: `null`, `false`, `0`, and `""` are falsy; in Ruby, `0` and `""` are truthy.
- `&&`/`||` return a `Boolean`, not one of the operands.
- String concatenation with `null` renders `"null"` for the null side, matching Kotlin's string templates (Ruby's `nil.to_s` is `""`).

## Not Implemented From Erubi

These Erubi features are tied to Ruby source generation and do not directly map to Ejabi's renderer:

- `bufvar`, `outvar`, `bufval`, `preamble`, `postamble`, and `chain_appends`
- Ruby `ensure` wrapping for restoring output variables
- Custom Ruby scanner regular expressions
- `escapefunc` as raw Ruby source code (Ejabi accepts an `EscapeFunction` instead — see Engine Options)
- `freeze` and `freeze_template_literals`
- `capture_block` and `capture_end` extensions
- Arbitrary Ruby statements inside `<% %>`

## Potential Future Work

- Consider capture helpers if Ejabi grows a block/helper API.
