package com.yoshisuproject.ejabi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EjabiEngineTest {
    private val engine = EjabiEngine()

    @Test
    fun `test plain text`() {
        val template = "Hello, World!"
        val result = engine.render(template)
        assertEquals("Hello, World!", result)
    }

    @Test
    fun `test variable output`() {
        val template = "Hello <%= name %>!"
        val result = engine.render(template, mapOf("name" to "World"))
        assertEquals("Hello World!", result)
    }

    @Test
    fun `test escaped output`() {
        val template = "<%= content %>"
        val result = engine.render(template, mapOf("content" to "<script data-x=`1`>alert('xss')</script>"))
        assertEquals("&lt;script data-x&#x3D;&#x60;1&#x60;&gt;alert(&#39;xss&#39;)&lt;/script&gt;", result)
    }

    @Test
    fun `test unescaped output`() {
        val template = "<%== content %>"
        val result = engine.render(template, mapOf("content" to "<b>Bold</b>"))
        assertEquals("<b>Bold</b>", result)
    }

    @Test
    fun `test comment`() {
        val template = "Hello <%# this is a comment %>World"
        val result = engine.render(template)
        assertEquals("Hello World", result)
    }

    @Test
    fun `test literal tag`() {
        val template = "Use <%% to start a tag"
        val result = engine.render(template)
        assertEquals("Use <% to start a tag", result)
    }

    @Test
    fun `test literal tag with body`() {
        val template = "<%%= name %>"
        val result = engine.render(template, mapOf("name" to "World"))
        assertEquals("<%= name %>", result)
    }

    @Test
    fun `test variable assignment`() {
        val template = "<% var greeting = 'Hello' %><%= greeting %> World"
        val result = engine.render(template)
        assertEquals("Hello World", result)
    }

    @Test
    fun `test member access`() {
        data class User(
            val name: String,
            val age: Int,
        )

        val template = "<%= user.name %> is <%= user.age %> years old"
        val result = engine.render(template, mapOf("user" to User("Alice", 30)))
        assertEquals("Alice is 30 years old", result)
    }

    @Test
    fun `test list index access`() {
        val template = "<%= items[0] %> and <%= items[1] %>"
        val result = engine.render(template, mapOf("items" to listOf("Apple", "Banana")))
        assertEquals("Apple and Banana", result)
    }

    @Test
    fun `test map access`() {
        val template = "<%= data.key %>"
        val result = engine.render(template, mapOf("data" to mapOf("key" to "value")))
        assertEquals("value", result)
    }

    @Test
    fun `test chained index and member access`() {
        data class User(
            val name: String,
        )

        val template = "<%= users[1].name %>"
        val result = engine.render(template, mapOf("users" to listOf(User("Alice"), User("Bob"))))
        assertEquals("Bob", result)
    }

    @Test
    fun `test arithmetic expression`() {
        val template = "<%= a + b %>"
        val result = engine.render(template, mapOf("a" to 10, "b" to 20))
        assertEquals("30", result)
    }

    @Test
    fun `test string concatenation`() {
        val template = "<%= greeting + ' ' + name %>"
        val result = engine.render(template, mapOf("greeting" to "Hello", "name" to "World"))
        assertEquals("Hello World", result)
    }

    @Test
    fun `test comparison expression`() {
        val template = "<%= age > 18 %>"
        val result = engine.render(template, mapOf("age" to 25))
        assertEquals("true", result)
    }

    @Test
    fun `test ternary expression`() {
        val template = "<%= age > 18 ? 'Adult' : 'Minor' %>"
        val result = engine.render(template, mapOf("age" to 25))
        assertEquals("Adult", result)
    }

    @Test
    fun `test boolean logic`() {
        val template = "<%= a && b %>"
        val result = engine.render(template, mapOf("a" to true, "b" to false))
        assertEquals("false", result)
    }

    @Test
    fun `test null handling`() {
        val template = "<%= nullValue %>"
        val result = engine.render(template, mapOf("nullValue" to null))
        assertEquals("", result)
    }

    @Test
    fun `test complex template`() {
        val template =
            """
            <table>
            <% var items = products %>
            <% for item in items %>
              <tr>
                <td><%= item.name %></td>
                <td>$<%= item.price %></td>
              </tr>
            <% end %>
            </table>
            """.trimIndent()

        data class Product(
            val name: String,
            val price: Double,
        )

        val products =
            listOf(
                Product("Apple", 1.50),
                Product("Banana", 0.75),
            )

        val result = engine.render(template, mapOf("products" to products))
        assertTrue(result.contains("Apple"))
        assertTrue(result.contains("Banana"))
    }

    @Test
    fun `test if else control flow`() {
        val template = "<% if loggedIn %>Hello <%= name %><% else %>Sign in<% end %>"

        assertEquals("Hello Alice", engine.render(template, mapOf("loggedIn" to true, "name" to "Alice")))
        assertEquals("Sign in", engine.render(template, mapOf("loggedIn" to false, "name" to "Alice")))
    }

    @Test
    fun `test nested for and if control flow`() {
        data class User(
            val name: String,
            val active: Boolean,
        )

        val template = "<% for user in users %><% if user.active %><%= user.name %>,<% end %><% end %>"
        val users = listOf(User("Alice", true), User("Bob", false), User("Carol", true))

        assertEquals("Alice,Carol,", engine.render(template, mapOf("users" to users)))
    }

    @Test
    fun `test for loop iterates primitive arrays`() {
        assertEquals("123", engine.render("<% for i in items %><%= i %><% end %>", mapOf("items" to intArrayOf(1, 2, 3))))
        assertEquals("3.52.5", engine.render("<% for d in ds %><%= d %><% end %>", mapOf("ds" to doubleArrayOf(3.5, 2.5))))
        assertEquals("ab", engine.render("<% for c in cs %><%= c %><% end %>", mapOf("cs" to charArrayOf('a', 'b'))))
        assertEquals("truefalse", engine.render("<% for b in bs %><%= b %><% end %>", mapOf("bs" to booleanArrayOf(true, false))))
    }

    @Test
    fun `test reusing compiled template`() {
        val template = engine.compile("Hello <%= name %>!")

        val result1 = template.render(mapOf("name" to "Alice"))
        val result2 = template.render(mapOf("name" to "Bob"))

        assertEquals("Hello Alice!", result1)
        assertEquals("Hello Bob!", result2)
    }

    @Test
    fun `test render to appendable`() {
        val template = engine.compile("Hello <%= name %>!")
        val output = StringBuilder()

        template.renderTo(output, mapOf("name" to "Alice"))

        assertEquals("Hello Alice!", output.toString())
    }

    @Test
    fun `test render to appendable with legacy argument order`() {
        val template = engine.compile("Hello <%= name %>!")
        val output = StringBuilder()

        template.renderTo(mapOf("name" to "Alice"), output)

        assertEquals("Hello Alice!", output.toString())
    }

    @Test
    fun `test special characters in text`() {
        val template = "<div class=\"test\">Content</div>"
        val result = engine.render(template)
        assertEquals("<div class=\"test\">Content</div>", result)
    }

    @Test
    fun `test quotes and less-than in text`() {
        val template = "<p title='single'>1 < 2</p>"
        val result = engine.render(template)
        assertEquals("<p title='single'>1 < 2</p>", result)
    }

    @Test
    fun `test percent in expression content`() {
        val template = "<%= a % b %>"
        val result = engine.render(template, mapOf("a" to 5, "b" to 2))
        assertEquals("1", result)
    }

    @Test
    fun `test operators inside string literal`() {
        val template = "<%= 'a > b ? yes : no' %>"
        val result = engine.render(template)
        assertEquals("a &gt; b ? yes : no", result)
    }

    @Test
    fun `test trimmed close delimiter parses`() {
        val template = "<%= name -%>"
        val result = engine.render(template, mapOf("name" to "Alice"))
        assertEquals("Alice", result)
    }

    @Test
    fun `test expression close trim removes following newline`() {
        val dashTrimmed = engine.render("<%= name -%>\n!", mapOf("name" to "Alice"))
        val equalsTrimmed = engine.render("<%= name =%>\n!", mapOf("name" to "Alice"))
        val rawEqualsTrimmed = engine.render("<%== name =%>\n!", mapOf("name" to "<b>Alice</b>"))

        assertEquals("Alice!", dashTrimmed)
        assertEquals("Alice!", equalsTrimmed)
        assertEquals("<b>Alice</b>!", rawEqualsTrimmed)
    }

    @Test
    fun `test code line trim removes standalone code line`() {
        val template =
            """
            before
              <% var name = 'Alice' %>
            after <%= name %>
            """.trimIndent()

        assertEquals("before\nafter Alice", engine.render(template))
    }

    @Test
    fun `test dashed code tag trims standalone code line`() {
        val template =
            """
            before
              <%- var name = 'Alice' %>
            after <%= name %>
            """.trimIndent()

        assertEquals("before\nafter Alice", engine.render(template))
    }

    @Test
    fun `test comment line trim removes standalone comment line`() {
        val template =
            """
            before
              <%# hidden %>
            after
            """.trimIndent()

        assertEquals("before\nafter", engine.render(template))
    }

    @Test
    fun `test code line trim only removes whitespace only sides`() {
        val textBeforeTag = "/<% var name = 'Alice' %>  \nafter <%= name %>"
        val textAfterTag = "  <% var name = 'Alice' %>/ \nafter <%= name %>"

        assertEquals("/  \nafter Alice", engine.render(textBeforeTag))
        assertEquals("  / \nafter Alice", engine.render(textAfterTag))
    }

    @Test
    fun `test inline code keeps surrounding whitespace`() {
        val template = "before <% var name = 'Alice' %> after <%= name %>"

        assertEquals("before  after Alice", engine.render(template))
    }

    @Test
    fun `test invalid template syntax throws parse exception`() {
        assertThrows(TemplateParseException::class.java) {
            engine.compile("Hello <% name")
        }
    }

    @Test
    fun `test empty output expression throws parse exception`() {
        assertThrows(ExpressionParseException::class.java) {
            engine.compile("<%= %>")
        }
    }

    @Test
    fun `test unsupported code statement throws render exception`() {
        val template = engine.compile("<% while true %>")

        assertThrows(TemplateRenderException::class.java) {
            template.render()
        }
    }

    @Test
    fun `test unclosed control block throws parse exception`() {
        assertThrows(TemplateParseException::class.java) {
            engine.compile("<% if show %>Visible")
        }
    }

    @Test
    fun `test unexpected end throws parse exception`() {
        assertThrows(TemplateParseException::class.java) {
            engine.compile("Text <% end %>")
        }
    }

    @Test
    fun `test simple assignment without var keyword`() {
        val result = engine.render("<% x = 5 %><%= x %>")
        assertEquals("5", result)
    }

    @Test
    fun `test numeric equality across numeric types`() {
        assertEquals("true", engine.render("<%= x == 1 %>", mapOf("x" to 1)))
        assertEquals("true", engine.render("<%= 1 == 1.0 %>"))
        assertEquals("false", engine.render("<%= x != 1 %>", mapOf("x" to 1)))
    }

    @Test
    fun `test string literal concatenation`() {
        assertEquals("ab", engine.render("<%= 'a' + 'b' %>"))
        assertEquals("ab", engine.render("<%= \"a\" + \"b\" %>"))
    }

    @Test
    fun `test text ending with less-than sign`() {
        assertEquals("hello <", engine.render("hello <"))
        assertEquals("a < b", engine.render("a < b"))
    }

    @Test
    fun `test closing delimiter inside string literal`() {
        assertEquals("100%&gt; done", engine.render("<%= '100%> done' %>"))
    }

    @Test
    fun `test standalone line trim with CRLF`() {
        val result = engine.render("before\r\n  <% var x = 'A' %>\r\nafter <%= x %>")
        assertEquals("before\r\nafter A", result)
    }

    @Test
    fun `test integer division by zero throws render exception`() {
        assertThrows(TemplateRenderException::class.java) {
            engine.render("<%= 1 / 0 %>")
        }
    }

    @Test
    fun `test unary not in template`() {
        assertEquals("true", engine.render("<%= !flag %>", mapOf("flag" to false)))
        assertEquals("false", engine.render("<%= !flag %>", mapOf("flag" to true)))
    }

    class FailingGetter {
        fun getValue(): String = throw IllegalStateException("boom")
    }

    @Test
    fun `test getter exception is wrapped in render exception`() {
        val exception =
            assertThrows(TemplateRenderException::class.java) {
                engine.render("<%= broken.value %>", mapOf("broken" to FailingGetter()))
            }
        assertTrue(exception.message!!.contains("value"))
        assertEquals("boom", exception.cause?.message)
    }

    @Test
    fun `test strict mode throws for missing variable`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= missing %>")
        }
        assertEquals("", engine.render("<%= missing %>"))
    }

    @Test
    fun `test strict mode throws for missing member`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= data.nope %>", mapOf("data" to mapOf("a" to 1)))
        }
        assertEquals("", engine.render("<%= data.nope %>", mapOf("data" to mapOf("a" to 1))))
    }

    @Test
    fun `test invalid assignment expression fails at compile time`() {
        assertThrows(ExpressionParseException::class.java) {
            engine.compile("<% x = a. %>")
        }
    }

    @Test
    fun `test strict mode throws for missing map index key`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= data['nope'] %>", mapOf("data" to mapOf("a" to 1)))
        }
        assertEquals("", engine.render("<%= data['nope'] %>", mapOf("data" to mapOf("a" to 1))))
    }

    @Test
    fun `test strict mode throws for out of bounds index`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= items[5] %>", mapOf("items" to listOf(1, 2, 3)))
        }
        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= items[-1] %>", mapOf("items" to listOf(1, 2, 3)))
        }
        assertEquals("", engine.render("<%= items[5] %>", mapOf("items" to listOf(1, 2, 3))))
    }

    @Test
    fun `test strict mode throws for non numeric index`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= items['x'] %>", mapOf("items" to listOf(1, 2, 3)))
        }
        assertEquals("", engine.render("<%= items['x'] %>", mapOf("items" to listOf(1, 2, 3))))
    }

    @Test
    fun `test strict mode throws for non indexable value`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= count[0] %>", mapOf("count" to 3))
        }
        assertEquals("", engine.render("<%= count[0] %>", mapOf("count" to 3)))
    }

    @Test
    fun `test strict mode throws for indexing null`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= nothing[0] %>", mapOf("nothing" to null))
        }
        assertEquals("", engine.render("<%= nothing[0] %>", mapOf("nothing" to null)))
    }

    @Test
    fun `test strict mode throws for type mismatch in arithmetic`() {
        val strictEngine = EjabiEngine(strict = true)

        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= 1 - x %>", mapOf("x" to "str"))
        }
        assertThrows(TemplateRenderException::class.java) {
            strictEngine.render("<%= 1 * flag %>", mapOf("flag" to true))
        }
        assertEquals("", engine.render("<%= 1 - x %>", mapOf("x" to "str")))
    }

    @Test
    fun `test assignment accepts full expression subset`() {
        assertEquals(
            "7",
            engine.render("<% x = items[0] + items[1] %><%= x %>", mapOf("items" to listOf(3, 4))),
        )
        assertEquals(
            "Alice",
            engine.render("<% name = user.name %><%= name %>", mapOf("user" to mapOf("name" to "Alice"))),
        )
        assertEquals(
            "2",
            engine.render("<% x = n > 1 ? n : 0 %><%= x %>", mapOf("n" to 2)),
        )
    }

    @Test
    fun `test invalid assignment rhs fails at compile time`() {
        assertThrows(ExpressionParseException::class.java) {
            engine.compile("<% x = items[ %>")
        }
        assertThrows(ExpressionParseException::class.java) {
            engine.compile("<% x = name.uppercase() %>")
        }
    }

    @Test
    fun `test nested ternary in template`() {
        assertEquals(
            "C",
            engine.render(
                "<%= a ? b ? c : d : e %>",
                mapOf("a" to true, "b" to true, "c" to "C", "d" to "D", "e" to "E"),
            ),
        )
    }

    @Test
    fun `test escape false swaps the escape matrix like erubi`() {
        val rawEngine = EjabiEngine(escape = false)

        assertEquals("<b>", rawEngine.render("<%= content %>", mapOf("content" to "<b>")))
        assertEquals("&lt;b&gt;", rawEngine.render("<%== content %>", mapOf("content" to "<b>")))
        assertEquals("&lt;b&gt;", engine.render("<%= content %>", mapOf("content" to "<b>")))
        assertEquals("<b>", engine.render("<%== content %>", mapOf("content" to "<b>")))
    }

    @Test
    fun `test trim false preserves standalone code and comment lines`() {
        val template =
            """
            before
              <% var name = 'Alice' %>
              <%# hidden %>
            after <%= name %>
            """.trimIndent()

        assertEquals("before\n  \n  \nafter Alice", EjabiEngine(trim = false).render(template))
        assertEquals("before\nafter Alice", engine.render(template))
    }

    @Test
    fun `test trim false keeps close tag trims`() {
        val trimOff = EjabiEngine(trim = false)

        assertEquals("Alice!", trimOff.render("<%= name -%>\n!", mapOf("name" to "Alice")))
        assertEquals("Alice!", trimOff.render("<%= name =%>\n!", mapOf("name" to "Alice")))
    }

    @Test
    fun `test custom escape function applies only to escaped output`() {
        val bracketEngine =
            EjabiEngine(
                escapeFunction = EscapeFunction { value -> value.replace("<", "[") },
            )

        assertEquals("[b>", bracketEngine.render("<%= content %>", mapOf("content" to "<b>")))
        assertEquals("<b>", bracketEngine.render("<%== content %>", mapOf("content" to "<b>")))
    }

    @Test
    fun `test fileName annotates compile errors`() {
        val parseError =
            assertThrows(TemplateParseException::class.java) {
                engine.compile("<% if show %>Visible", "page.ejb")
            }

        assertTrue(parseError.message!!.startsWith("page.ejb: "))

        val expressionError =
            assertThrows(ExpressionParseException::class.java) {
                engine.compile("<%= %>", "bad.ejb")
            }

        assertTrue(expressionError.message!!.startsWith("bad.ejb: "))
    }

    @Test
    fun `test fileName annotates render errors`() {
        val template = engine.compile("<%= 1 / 0 %>", "math.ejb")

        val exception =
            assertThrows(TemplateRenderException::class.java) {
                template.render()
            }

        assertTrue(exception.message!!.startsWith("math.ejb: "))
        assertTrue(exception.message!!.contains("Division by zero"))
    }

    @Test
    fun `test parse errors include template position`() {
        val missingEnd =
            assertThrows(TemplateParseException::class.java) {
                engine.compile("a\n<% if show %>x")
            }
        assertTrue(missingEnd.message!!.contains("2:1: Missing <% end %> for if statement"))

        val unexpectedElse =
            assertThrows(TemplateParseException::class.java) {
                engine.compile("a\n<% else %>")
            }
        assertTrue(unexpectedElse.message!!.contains("2:1: Unexpected <% else %>"))
    }

    @Test
    fun `test render errors include template position`() {
        val undefinedVariable =
            assertThrows(TemplateRenderException::class.java) {
                EjabiEngine(strict = true).compile("a\nb\n<%= missing %>").render()
            }
        assertTrue(undefinedVariable.message!!.contains("3:5: Undefined variable 'missing'"))

        val division =
            assertThrows(TemplateRenderException::class.java) {
                engine.compile("a\n<%= 1 / 0 %>").render()
            }
        assertTrue(division.message!!.startsWith("2:7: Division by zero"))

        val notIterable =
            assertThrows(TemplateRenderException::class.java) {
                engine.compile("a\n<% for item in items %><% end %>").render(mapOf("items" to 42))
            }
        assertTrue(notIterable.message!!.contains("2:1: Value is not iterable"))

        val unsupported =
            assertThrows(TemplateRenderException::class.java) {
                engine.compile("a\n<% while true %>").render()
            }
        assertTrue(unsupported.message!!.contains("2:1: Unsupported code statement"))
    }

    @Test
    fun `test fileName and position compose in error messages`() {
        val template = engine.compile("<%= 1 / 0 %>", "math.ejb")

        val exception =
            assertThrows(TemplateRenderException::class.java) {
                template.render()
            }

        assertEquals("math.ejb: 1:7: Division by zero", exception.message)
    }

    @Test
    fun `test unary minus in template`() {
        assertEquals("-5", engine.render("<%= -a %>", mapOf("a" to 5)))
        assertEquals("-3", engine.render("<%= -user.age %>", mapOf("user" to mapOf("age" to 3))))
        assertEquals("-7", engine.render("<%= -a - -b %>", mapOf("a" to 5, "b" to -2)))
    }

    @Test
    fun `test invalid expression syntax error includes position`() {
        val exception =
            assertThrows(ExpressionParseException::class.java) {
                engine.compile("ok\n<%= 1 < x < 3 %>")
            }

        assertTrue(exception.message!!.startsWith("Invalid expression: 2:11:"))
    }

    @Test
    fun `test floating point division by zero renders infinity`() {
        assertEquals("Infinity", engine.render("<%= 1 / 0.0 %>"))
    }

    @Test
    fun `test nesting at the depth limit still compiles`() {
        val template = "<% if true %>".repeat(500) + "x" + "<% end %>".repeat(500)
        assertEquals("x", engine.render(template))
    }

    @Test
    fun `test string escapes render through the full template pipeline`() {
        assertEquals("a\nb", engine.render("<%== 'a\\nb' %>"))
        assertEquals("it's", engine.render("<%== 'it\\'s' %>"))
    }

    @Test
    fun `test deeply nested blocks throw parse exception instead of stack overflow`() {
        val depth = 2000
        val template = "<% if true %>".repeat(depth) + "x" + "<% end %>".repeat(depth)

        assertThrows(TemplateParseException::class.java) {
            engine.compile(template)
        }
    }

    @Test
    fun `test compiled template renders concurrently`() {
        val template = engine.compile("<%= name %>-<% for i in items %><%= i %><% end %>")
        val executor =
            java.util.concurrent.Executors
                .newFixedThreadPool(8)

        try {
            val futures =
                (1..50).map { n ->
                    executor.submit<String> {
                        template.render(mapOf("name" to "n$n", "items" to listOf(1, 2, 3)))
                    }
                }
            futures.forEachIndexed { index, future ->
                assertEquals("n${index + 1}-123", future.get())
            }
        } finally {
            executor.shutdown()
        }
    }
}
