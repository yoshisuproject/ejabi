# Ejabi

[![CI](https://github.com/yoshisuproject/ejabi/actions/workflows/ci.yml/badge.svg)](https://github.com/yoshisuproject/ejabi/actions/workflows/ci.yml)

A Kotlin template engine inspired by [Erubi](https://github.com/jeremyevans/erubi) (Ruby), powered by ANTLR4 template parsing.

## Features

- **Erubi-style syntax**: Uses familiar `<% %>` tags for text templates
- **ANTLR4-powered parsing**: Grammar-based parsing for templates and expressions
- **Expression evaluation**: Supports literals, variables, property/index access, arithmetic, comparison, logical operators, and ternary expressions
- **HTML escaping**: Automatic XSS protection with `<%= %>` and raw output with `<%== %>`
- **Kotlin-native**: Built for Kotlin with full static type safety

## Syntax

| Tag | Description |
|-----|-------------|
| `<%= expr %>` | Output expression (HTML escaped) |
| `<%== expr %>` | Output expression (raw/unescaped) |
| `<% var name = expr %>` / `<% name = expr %>` | Assign a template-local variable |
| `<% if expr %>` / `<% else %>` / `<% end %>` | Conditional rendering |
| `<% for item in items %>` / `<% end %>` | Iterate over lists, arrays (including primitives), maps, strings, and iterables |
| `<%# comment %>` | Comment (ignored) |
| `<%%` | Literal `<%` |
| `<%-` / `-%>` / `=%>` | Erubi-style trim markers (`<%-` behaves like `<%`; `-%>` / `=%>` also trim the newline after output tags) |

## Quick Start

Gradle:

```kotlin
dependencies {
    implementation("com.yoshisuproject:ejabi:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>com.yoshisuproject</groupId>
  <artifactId>ejabi</artifactId>
  <version>0.1.0</version>
</dependency>
```

```kotlin
import com.yoshisuproject.ejabi.EjabiEngine

val engine = EjabiEngine()

// Simple variable substitution
val result = engine.render(
    "Hello <%= name %>!",
    mapOf("name" to "World")
)
// Result: "Hello World!"

// HTML escaping
val escaped = engine.render(
    "<%= content %>",
    mapOf("content" to "<script>alert('xss')</script>")
)
// Result: "&lt;script&gt;..."

// Pre-compile templates
val template = engine.compile("Hello <%= name %>!")
val result1 = template.render(mapOf("name" to "Alice"))
val result2 = template.render(mapOf("name" to "Bob"))
```

Java:

```java
import com.yoshisuproject.ejabi.EjabiEngine;
import com.yoshisuproject.ejabi.Template;
import java.util.Map;

EjabiEngine engine = new EjabiEngine();
String rendered = engine.render("Hello <%= name %>!", Map.of("name", "Java"));

Template template = engine.compile("Hello <%= name %>!");
StringBuilder out = new StringBuilder();
template.renderTo(out, Map.of("name", "Java"));
```

### Engine options

All parameters are optional; Kotlin callers should use named arguments.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `strict` | `false` | Throw on missing variables/members/indexes or arithmetic type mismatches instead of rendering empty output |
| `escape` | `true` | `<%= %>` escapes and `<%== %>` renders raw; with `false` the two tags swap roles |
| `trim` | `true` | Trim whitespace around standalone `<% %>` / `<%# %>` lines |
| `escapeFunction` | built-in HTML escape | Custom `EscapeFunction` applied whenever output is escaped |

```kotlin
val engine = EjabiEngine(strict = true, trim = false)
```

## Spring Boot Starter

`ejabi-spring-boot-starter` auto-configures an `EjabiEngine` bean and a Spring MVC view resolver (Spring Boot 4, servlet web apps):

```kotlin
dependencies {
    implementation("com.yoshisuproject:ejabi-spring-boot-starter:0.1.0")
}
```

With the starter on the classpath, a controller can return view names resolved from `classpath:/templates/` with the `.ejb` suffix:

```kotlin
@Controller
class PageController {
    @GetMapping("/hello")
    fun hello(model: Model): String {
        model.addAttribute("name", "World")
        return "hello" // renders classpath:/templates/hello.ejb
    }
}
```

Configuration properties (prefix `ejabi`):

| Property | Default | Description |
|----------|---------|-------------|
| `ejabi.strict` | `false` | Throw on missing variables/members/indexes or arithmetic type mismatches instead of rendering empty output |
| `ejabi.escape` | `true` | `<%= %>` escapes and `<%== %>` renders raw; with `false` the two tags swap roles |
| `ejabi.trim` | `true` | Trim whitespace around standalone `<% %>` / `<%# %>` lines |
| `ejabi.prefix` | `classpath:/templates/` | Template location prefix |
| `ejabi.suffix` | `.ejb` | Template file suffix |
| `ejabi.cache` | `true` | Cache compiled templates; set to `false` in development to pick up template file changes without restart |
| `ejabi.content-type` | `text/html;charset=UTF-8` | Response content type (`ejabi.contentType` also binds) |
| `ejabi.order` | `LOWEST_PRECEDENCE - 10` | View resolver order |

## Expressions

Ejabi supports rich expressions:

```erb
<%= user.name %>              <!-- Property access -->
<%= items[0] %>               <!-- Index access -->
<%= a + b %>                  <!-- Arithmetic -->
<%= -count %>                 <!-- Unary minus -->
<%= age > 18 ? 'Adult' : 'Minor' %>  <!-- Ternary -->
<%= a && b || c %>            <!-- Logical operators -->
```

Logical operators short-circuit. Missing variables and null expression results render as empty strings; construct the engine with `EjabiEngine(strict = true)` to turn them into `TemplateRenderException` instead. Invalid template syntax fails during compilation. Comparisons are non-associative, so chained comparisons like `1 < a < 3` are a syntax error.

## Control Flow

```erb
<% if user.active %>
  <%= user.name %>
<% else %>
  inactive
<% end %>

<% for item in items %>
  <%= item %>
<% end %>
```

For loops bind the loop variable only inside the loop body and restore any previous variable with the same name after rendering.

## Error Handling

Ejabi exceptions inherit from `EjabiException`:

- `TemplateParseException`: invalid template structure, such as an unclosed tag.
- `ExpressionParseException`: invalid or empty expressions.
- `TemplateRenderException`: unsupported executable statements or rendering failures.

Unsupported code such as `<% while true %>` currently fails during rendering.

Parse and render errors carry a 1-based `line:col` template position, prefixed with the `fileName` passed to `compile(...)` when provided:

```
page.ejb:3:5: Undefined variable 'user'
```

## Prerequisites

* Java 17
* Kotlin 2.2.21

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

## Publishing

Build and publish to the local Maven cache:

```bash
./gradlew clean build publishToMavenLocal
```

The published artifact includes the library jar, sources jar, javadoc jar, and Maven POM metadata. Signing is optional for local builds and enabled when Gradle properties such as `signingKey`, `signingPassword`, and optionally `signingKeyId` are provided, for example through `ORG_GRADLE_PROJECT_signingKey`.

External repository credentials are intentionally not committed. Set `releaseRepositoryUrl`, `releaseRepositoryUsername`, and `releaseRepositoryPassword` when publishing to a remote Maven repository. See [RELEASE.md](RELEASE.md) for the release checklist.

## License

MIT License - see [LICENSE](LICENSE) file.
