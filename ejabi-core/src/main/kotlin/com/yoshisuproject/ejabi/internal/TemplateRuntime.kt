package com.yoshisuproject.ejabi.internal

import com.yoshisuproject.ejabi.EscapeFunction
import com.yoshisuproject.ejabi.TemplateRenderException

/**
 * Rendering context that holds variables and state during template execution.
 */
internal class RenderContext private constructor(
    private val variables: MutableMap<String, Any?>,
    val strict: Boolean,
    val escape: Boolean,
    val escapeFunction: EscapeFunction,
) {
    companion object {
        fun fromMap(
            context: Map<String, Any?>,
            strict: Boolean = false,
            escape: Boolean = true,
            escapeFunction: EscapeFunction = HtmlEscapeFunction,
        ): RenderContext = RenderContext(context.toMutableMap(), strict, escape, escapeFunction)
    }

    constructor() : this(mutableMapOf(), false, true, HtmlEscapeFunction)

    fun get(name: String): Any? = variables[name]

    fun set(
        name: String,
        value: Any?,
    ) {
        variables[name] = value
    }

    fun remove(name: String) {
        variables.remove(name)
    }

    fun contains(name: String): Boolean = variables.containsKey(name)
}

/**
 * Base interface for all template statements.
 */
internal interface Statement {
    fun execute(
        context: RenderContext,
        output: Appendable,
    )
}

/**
 * Statement for raw text output.
 */
internal class TextStatement(
    private val text: String,
) : Statement {
    override fun execute(
        context: RenderContext,
        output: Appendable,
    ) {
        output.append(text)
    }
}

/**
 * Statement for code execution (variable assignment).
 * The assignment expression is parsed once at compile time.
 */
internal class CodeStatement(
    code: String,
    private val position: Position? = null,
) : Statement {
    private val trimmedCode = code.trim()
    private val assignment: Assignment?

    init {
        val varMatch = assignmentPattern.matchEntire(trimmedCode)
        val simpleMatch = simpleAssignmentPattern.matchEntire(trimmedCode)
        assignment =
            when {
                varMatch != null -> {
                    Assignment(varMatch.groupValues[1], ExpressionParser(position).parse(varMatch.groupValues[2]))
                }

                simpleMatch != null -> {
                    Assignment(simpleMatch.groupValues[1], ExpressionParser(position).parse(simpleMatch.groupValues[2]))
                }

                else -> {
                    null
                }
            }
    }

    override fun execute(
        context: RenderContext,
        output: Appendable,
    ) {
        val currentAssignment = assignment
        when {
            currentAssignment != null -> {
                val value = ExpressionEvaluator.evaluate(currentAssignment.expression, context)
                context.set(currentAssignment.name, value)
            }

            trimmedCode.isNotEmpty() -> {
                throw TemplateRenderException(positionPrefix(position) + "Unsupported code statement: $trimmedCode")
            }
        }
    }

    private data class Assignment(
        val name: String,
        val expression: Expression,
    )

    companion object {
        private val assignmentPattern = Regex("""^\s*var\s+(\w+)\s*=\s*(.+)""")
        private val simpleAssignmentPattern = Regex("""^\s*(\w+)\s*=\s*(.+)""")
    }
}

/**
 * Statement for conditional template rendering.
 */
internal class IfStatement(
    private val condition: Expression,
    private val thenStatements: List<Statement>,
    private val elseStatements: List<Statement>,
) : Statement {
    override fun execute(
        context: RenderContext,
        output: Appendable,
    ) {
        val branch =
            if (ExpressionEvaluator.isTruthy(ExpressionEvaluator.evaluate(condition, context))) {
                thenStatements
            } else {
                elseStatements
            }
        for (statement in branch) {
            statement.execute(context, output)
        }
    }
}

/**
 * Statement for iterating over lists, arrays, maps, strings, and other iterables.
 */
internal class ForStatement(
    private val variableName: String,
    private val iterableExpression: Expression,
    private val body: List<Statement>,
    private val position: Position? = null,
) : Statement {
    override fun execute(
        context: RenderContext,
        output: Appendable,
    ) {
        val iterable = toIterable(ExpressionEvaluator.evaluate(iterableExpression, context)) ?: return
        val previousValue = if (context.contains(variableName)) context.get(variableName) else MissingValue

        try {
            for (item in iterable) {
                context.set(variableName, item)
                for (statement in body) {
                    statement.execute(context, output)
                }
            }
        } finally {
            if (previousValue === MissingValue) {
                context.remove(variableName)
            } else {
                context.set(variableName, previousValue)
            }
        }
    }

    private fun toIterable(value: Any?): Iterable<Any?>? =
        when (value) {
            null -> null
            is Iterable<*> -> value
            is Array<*> -> value.asIterable()
            is BooleanArray -> value.toList()
            is ByteArray -> value.toList()
            is CharArray -> value.toList()
            is ShortArray -> value.toList()
            is IntArray -> value.toList()
            is LongArray -> value.toList()
            is FloatArray -> value.toList()
            is DoubleArray -> value.toList()
            is Map<*, *> -> value.entries
            is String -> value.map { it.toString() }
            else -> throw TemplateRenderException(positionPrefix(position) + "Value is not iterable: ${value::class.qualifiedName}")
        }

    private object MissingValue
}

/**
 * Statement for expression output. [escapedTag] records whether the tag was
 * written in the escaped style (`<%=`); whether escaping actually applies is
 * resolved at render time against the engine's escape option, following
 * Erubi's `(tag == escaped style) == escape` matrix.
 */
internal class ExpressionStatement(
    private val expression: Expression,
    private val escapedTag: Boolean,
) : Statement {
    override fun execute(
        context: RenderContext,
        output: Appendable,
    ) {
        val value = ExpressionEvaluator.evaluate(expression, context)
        val stringValue = value?.toString() ?: ""

        if (escapedTag == context.escape) {
            output.append(context.escapeFunction.escape(stringValue))
        } else {
            output.append(stringValue)
        }
    }
}

/**
 * HTML/XML escape utility.
 */
internal object HtmlEscape {
    private val ESCAPE_MAP =
        mapOf(
            '&' to "&amp;",
            '<' to "&lt;",
            '>' to "&gt;",
            '"' to "&quot;",
            '\'' to "&#39;",
            '`' to "&#x60;",
            '=' to "&#x3D;",
        )

    fun escape(text: String?): String {
        if (text == null) return ""
        return buildString {
            for (char in text) {
                append(ESCAPE_MAP[char] ?: char)
            }
        }
    }

    fun escape(obj: Any?): String = escape(obj?.toString())
}

/**
 * The built-in [EscapeFunction] backed by [HtmlEscape], escaping
 * `& < > " ' \`` and `=`.
 */
internal object HtmlEscapeFunction : com.yoshisuproject.ejabi.EscapeFunction {
    override fun escape(value: String): String = HtmlEscape.escape(value)
}
