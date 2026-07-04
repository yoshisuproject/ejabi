package com.yoshisuproject.ejabi

import com.yoshisuproject.ejabi.internal.ExpressionEvaluator
import com.yoshisuproject.ejabi.internal.ExpressionParser
import com.yoshisuproject.ejabi.internal.LiteralExpression
import com.yoshisuproject.ejabi.internal.MemberAccessExpression
import com.yoshisuproject.ejabi.internal.RenderContext
import com.yoshisuproject.ejabi.internal.VariableExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionTest {
    @Test
    fun `test literal parsing`() {
        val expr = ExpressionParser.parse("42")
        assertTrue(expr is LiteralExpression)
        assertEquals(42L, (expr as LiteralExpression).value)
    }

    @Test
    fun `test string literal parsing`() {
        val expr = ExpressionParser.parse("'hello'")
        assertTrue(expr is LiteralExpression)
        assertEquals("hello", (expr as LiteralExpression).value)
    }

    @Test
    fun `test boolean literal parsing`() {
        val expr = ExpressionParser.parse("true")
        assertTrue(expr is LiteralExpression)
        assertEquals(true, (expr as LiteralExpression).value)
    }

    @Test
    fun `test variable parsing`() {
        val expr = ExpressionParser.parse("name")
        assertTrue(expr is VariableExpression)
        assertEquals("name", (expr as VariableExpression).name)
    }

    @Test
    fun `test member access parsing`() {
        val expr = ExpressionParser.parse("user.name")
        assertTrue(expr is MemberAccessExpression)
        val memberExpr = expr as MemberAccessExpression
        assertEquals("name", memberExpr.member)
        assertTrue(memberExpr.target is VariableExpression)
    }

    @Test
    fun `test variable evaluation`() {
        val ctx = RenderContext.fromMap(mapOf("name" to "Alice", "age" to 30))

        assertEquals("Alice", ExpressionEvaluator.evaluate(ExpressionParser.parse("name"), ctx))
        assertEquals(30, ExpressionEvaluator.evaluate(ExpressionParser.parse("age"), ctx))
    }

    data class Person(
        val name: String,
        val age: Int,
    )

    @Test
    fun `test member access evaluation`() {
        val ctx = RenderContext.fromMap(mapOf("person" to Person("Bob", 25)))

        assertEquals("Bob", ExpressionEvaluator.evaluate(ExpressionParser.parse("person.name"), ctx))
        assertEquals(25, ExpressionEvaluator.evaluate(ExpressionParser.parse("person.age"), ctx))
    }

    @Test
    fun `test list index access evaluation`() {
        val ctx = RenderContext.fromMap(mapOf("items" to listOf("a", "b", "c")))

        assertEquals("a", ExpressionEvaluator.evaluate(ExpressionParser.parse("items[0]"), ctx))
        assertEquals("b", ExpressionEvaluator.evaluate(ExpressionParser.parse("items[1]"), ctx))
    }

    @Test
    fun `test out of bounds index evaluates to null`() {
        val ctx = RenderContext.fromMap(mapOf("items" to listOf("a")))

        assertNull(ExpressionEvaluator.evaluate(ExpressionParser.parse("items[3]"), ctx))
        assertNull(ExpressionEvaluator.evaluate(ExpressionParser.parse("items['bad']"), ctx))
    }

    @Test
    fun `test map access evaluation`() {
        val ctx = RenderContext.fromMap(mapOf("data" to mapOf("key1" to "value1", "key2" to 42)))

        assertEquals("value1", ExpressionEvaluator.evaluate(ExpressionParser.parse("data.key1"), ctx))
        assertEquals(42, ExpressionEvaluator.evaluate(ExpressionParser.parse("data.key2"), ctx))
    }

    @Test
    fun `test null handling`() {
        val ctx = RenderContext.fromMap(mapOf("nullValue" to null))

        assertNull(ExpressionEvaluator.evaluate(ExpressionParser.parse("nullValue"), ctx))
    }

    @Test
    fun `test invalid expression throws parse exception`() {
        assertThrows(ExpressionParseException::class.java) {
            ExpressionParser.parse("name.")
        }
        assertThrows(ExpressionParseException::class.java) {
            ExpressionParser.parse("")
        }
    }

    @Test
    fun `test chained arithmetic is left associative`() {
        val ctx = RenderContext()

        assertEquals(6L, ExpressionEvaluator.evaluate(ExpressionParser.parse("1 + 2 + 3"), ctx))
        assertEquals(-4L, ExpressionEvaluator.evaluate(ExpressionParser.parse("1 - 2 - 3"), ctx))
        assertEquals(2L, ExpressionEvaluator.evaluate(ExpressionParser.parse("8 / 2 / 2"), ctx))
    }

    @Test
    fun `test integer arithmetic preserves integral results`() {
        val ctx = RenderContext()

        assertEquals(3L, ExpressionEvaluator.evaluate(ExpressionParser.parse("1 + 2"), ctx))
        assertEquals(6L, ExpressionEvaluator.evaluate(ExpressionParser.parse("2 * 3"), ctx))
        assertEquals(3L, ExpressionEvaluator.evaluate(ExpressionParser.parse("7 / 2"), ctx))
        assertEquals(1L, ExpressionEvaluator.evaluate(ExpressionParser.parse("5 % 2"), ctx))
        assertEquals(2.5, ExpressionEvaluator.evaluate(ExpressionParser.parse("5.0 / 2"), ctx))
        assertEquals(3.0, ExpressionEvaluator.evaluate(ExpressionParser.parse("1 + 2.0"), ctx))
    }

    @Test
    fun `test integer division by zero throws render exception`() {
        val ctx = RenderContext()

        assertThrows(TemplateRenderException::class.java) {
            ExpressionEvaluator.evaluate(ExpressionParser.parse("1 / 0"), ctx)
        }
        assertThrows(TemplateRenderException::class.java) {
            ExpressionEvaluator.evaluate(ExpressionParser.parse("1 % 0"), ctx)
        }
    }

    @Test
    fun `test unary not`() {
        val ctx = RenderContext()

        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("!true"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("!false"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("!0"), ctx))
        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("!(1 == 1)"), ctx))
    }

    @Test
    fun `test string literal escapes`() {
        val ctx = RenderContext()

        assertEquals("a\nb", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a\\nb'"), ctx))
        assertEquals("a\tb", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a\\tb'"), ctx))
        assertEquals("it's", ExpressionEvaluator.evaluate(ExpressionParser.parse("'it\\'s'"), ctx))
        assertEquals("a\\b", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a\\\\b'"), ctx))
    }

    @Test
    fun `test string comparison uses natural ordering`() {
        val ctx = RenderContext()

        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("'a' < 'b'"), ctx))
        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("'a' > 'b'"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("'a' <= 'a'"), ctx))
    }

    @Test
    fun `test string literals can contain operator characters`() {
        val ctx = RenderContext()

        assertEquals("a > b ? yes : no", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a > b ? yes : no'"), ctx))
    }

    @Test
    fun `test numeric equality across boxed numeric types`() {
        val ctx = RenderContext.fromMap(mapOf("x" to 1))

        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("x == 1"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("1 == 1.0"), ctx))
        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("x != 1"), ctx))
    }

    @Test
    fun `test integral equality is exact beyond double precision`() {
        val ctx = RenderContext()

        // These longs differ by one but collapse to the same double.
        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("9007199254740993 == 9007199254740992"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("9007199254740993 == 9007199254740993"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("9007199254740993 > 9007199254740992"), ctx))
    }

    @Test
    fun `test big integer values compare exactly`() {
        val ctx =
            RenderContext.fromMap(
                mapOf(
                    "a" to java.math.BigInteger("9007199254740993"),
                    "b" to java.math.BigInteger("9007199254740992"),
                ),
            )

        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("a == b"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("a > b"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("a == a"), ctx))
    }

    @Test
    fun `test ternary parses nested ternary in true branch`() {
        val ctx =
            RenderContext.fromMap(
                mapOf("a" to true, "b" to true, "c" to "C", "d" to "D", "e" to "E"),
            )

        assertEquals("C", ExpressionEvaluator.evaluate(ExpressionParser.parse("a ? b ? c : d : e"), ctx))
        assertEquals(
            "D",
            ExpressionEvaluator.evaluate(
                ExpressionParser.parse("a ? b ? c : d : e"),
                RenderContext.fromMap(mapOf("a" to true, "b" to false, "c" to "C", "d" to "D", "e" to "E")),
            ),
        )
    }

    @Test
    fun `test ternary false branch is right associative`() {
        val ctx =
            RenderContext.fromMap(
                mapOf("a" to false, "b" to "B", "c" to false, "d" to "D", "e" to "E"),
            )

        assertEquals("E", ExpressionEvaluator.evaluate(ExpressionParser.parse("a ? b : c ? d : e"), ctx))
        assertEquals(
            "D",
            ExpressionEvaluator.evaluate(
                ExpressionParser.parse("a ? b : c ? d : e"),
                RenderContext.fromMap(mapOf("a" to false, "b" to "B", "c" to true, "d" to "D", "e" to "E")),
            ),
        )
    }

    @Test
    fun `test string literal concatenation`() {
        val ctx = RenderContext()

        assertEquals("ab", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a' + 'b'"), ctx))
        assertEquals("a > b", ExpressionEvaluator.evaluate(ExpressionParser.parse("'a > ' + 'b'"), ctx))
    }

    @Test
    fun `test member access resolves per target class`() {
        class ByMethod {
            fun getValue(): String = "fromMethod"
        }

        class ByField {
            @JvmField val value: String = "fromField"
        }

        val ctx =
            RenderContext.fromMap(
                mapOf("a" to ByMethod(), "b" to ByField(), "c" to ByMethod()),
            )

        // Same member name on different classes must resolve independently,
        // even when one class resolves via method and the other via field.
        assertEquals("fromMethod", ExpressionEvaluator.evaluate(ExpressionParser.parse("a.value"), ctx))
        assertEquals("fromField", ExpressionEvaluator.evaluate(ExpressionParser.parse("b.value"), ctx))
        assertEquals("fromMethod", ExpressionEvaluator.evaluate(ExpressionParser.parse("c.value"), ctx))
    }

    @Test
    fun `test boolean operators short circuit`() {
        class Bomb {
            fun getValue(): String {
                error("right side should not be evaluated")
            }
        }

        val ctx = RenderContext.fromMap(mapOf("bomb" to Bomb()))

        assertEquals(false, ExpressionEvaluator.evaluate(ExpressionParser.parse("false && bomb.value"), ctx))
        assertEquals(true, ExpressionEvaluator.evaluate(ExpressionParser.parse("true || bomb.value"), ctx))
    }

    @Test
    fun `test unary minus evaluates operands`() {
        val ctx = RenderContext.fromMap(mapOf("a" to 5, "u" to Person("n", 3)))

        assertEquals(-5L, ExpressionEvaluator.evaluate(ExpressionParser.parse("-a"), ctx))
        assertEquals(-3L, ExpressionEvaluator.evaluate(ExpressionParser.parse("-u.age"), ctx))
        assertEquals(-2.5, ExpressionEvaluator.evaluate(ExpressionParser.parse("-a * 0.5"), ctx))
        assertEquals(-5.0, ExpressionEvaluator.evaluate(ExpressionParser.parse("-5.0"), ctx))
        assertEquals(-5L, ExpressionEvaluator.evaluate(ExpressionParser.parse("+-a"), ctx))
    }

    @Test
    fun `test unary minus strict throws for non numeric operand`() {
        val strictCtx = RenderContext.fromMap(mapOf("flag" to true), strict = true)

        assertThrows(TemplateRenderException::class.java) {
            ExpressionEvaluator.evaluate(ExpressionParser.parse("-flag"), strictCtx)
        }
        assertNull(ExpressionEvaluator.evaluate(ExpressionParser.parse("-flag"), RenderContext.fromMap(mapOf("flag" to true))))
    }

    @Test
    fun `test chained comparison is a positioned syntax error`() {
        val exception =
            assertThrows(ExpressionParseException::class.java) {
                ExpressionParser.parse("1 < a < 3")
            }

        assertTrue(exception.message!!.contains("1:7:"))
    }
}
