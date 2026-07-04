package com.yoshisuproject.ejabi.internal

import com.yoshisuproject.ejabi.ExpressionParseException
import com.yoshisuproject.ejabi.TemplateRenderException
import com.yoshisuproject.ejabi.internal.parser.EjabiExprBaseVisitor
import com.yoshisuproject.ejabi.internal.parser.EjabiExprLexer
import com.yoshisuproject.ejabi.internal.parser.EjabiExprParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.Token
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.IllegalCallableAccessException
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties

/**
 * Base class for all expression AST nodes.
 */
internal sealed class Expression

/**
 * Literal value expression.
 */
internal data class LiteralExpression(
    val value: Any?,
) : Expression()

/**
 * Variable reference expression.
 */
internal data class VariableExpression(
    val name: String,
    val position: Position? = null,
) : Expression()

/**
 * Member access expression (e.g., user.name).
 */
internal data class MemberAccessExpression(
    val target: Expression,
    val member: String,
    val position: Position? = null,
) : Expression()

/**
 * Index access expression (e.g., items[0]).
 */
internal data class IndexAccessExpression(
    val target: Expression,
    val index: Expression,
    val position: Position? = null,
) : Expression()

/**
 * Binary operation expression.
 */
internal data class BinaryOpExpression(
    val left: Expression,
    val operator: BinaryOperator,
    val right: Expression,
    val position: Position? = null,
) : Expression()

/**
 * Ternary conditional expression.
 */
internal data class TernaryExpression(
    val condition: Expression,
    val trueExpr: Expression,
    val falseExpr: Expression,
) : Expression()

internal enum class BinaryOperator {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    AND,
    OR,
}

/**
 * Unary logical-not expression.
 */
internal data class NotExpression(
    val operand: Expression,
) : Expression()

/**
 * Unary negation expression (e.g., -count).
 */
internal data class UnaryMinusExpression(
    val operand: Expression,
    val position: Position? = null,
) : Expression()

/**
 * ANTLR-backed parser for the Ejabi expression language (see
 * `src/main/antlr/EjabiExpr.g4`). [base] is the template position where the
 * parsed content starts; token positions inside the content are mapped back
 * onto template coordinates so AST nodes and parse errors carry absolute
 * `line:col` positions.
 */
internal class ExpressionParser(
    private val base: Position? = null,
) {
    fun parse(expression: String): Expression {
        if (expression.isBlank()) {
            throw ExpressionParseException(positionPrefix(base) + "Expression cannot be empty")
        }

        val errorListener = ExpressionErrorListener(base)
        val lexer = EjabiExprLexer(CharStreams.fromString(expression))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)

        val parser = EjabiExprParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tree = parser.expression()
        errorListener.throwIfErrors()

        return ExpressionVisitor(base).visit(tree)
    }

    private class ExpressionVisitor(
        private val base: Position?,
    ) : EjabiExprBaseVisitor<Expression>() {
        private fun absolute(token: Token): Position = toAbsolutePosition(base, token.line, token.charPositionInLine)

        override fun visitExpression(ctx: EjabiExprParser.ExpressionContext): Expression = visit(ctx.ternaryExpression())

        override fun visitTernaryExpression(ctx: EjabiExprParser.TernaryExpressionContext): Expression {
            val condition = visit(ctx.orExpression())
            if (ctx.QUESTION() == null) return condition
            return TernaryExpression(condition, visit(ctx.ternaryExpression(0)), visit(ctx.ternaryExpression(1)))
        }

        override fun visitOrExpression(ctx: EjabiExprParser.OrExpressionContext): Expression {
            var result = visit(ctx.andExpression(0))
            for (i in 1 until ctx.andExpression().size) {
                val operator = BinaryOperator.OR
                result = BinaryOpExpression(result, operator, visit(ctx.andExpression(i)), absolute(ctx.OR(i - 1)!!.symbol))
            }
            return result
        }

        override fun visitAndExpression(ctx: EjabiExprParser.AndExpressionContext): Expression {
            var result = visit(ctx.equalityExpression(0))
            for (i in 1 until ctx.equalityExpression().size) {
                val operator = BinaryOperator.AND
                result = BinaryOpExpression(result, operator, visit(ctx.equalityExpression(i)), absolute(ctx.AND(i - 1)!!.symbol))
            }
            return result
        }

        override fun visitEqualityExpression(ctx: EjabiExprParser.EqualityExpressionContext): Expression {
            val left = visit(ctx.comparisonExpression(0))
            val operator =
                when {
                    ctx.EQ() != null -> BinaryOperator.EQ
                    ctx.NE() != null -> BinaryOperator.NE
                    else -> return left
                }
            val token = (ctx.EQ() ?: ctx.NE())!!.symbol
            return BinaryOpExpression(left, operator, visit(ctx.comparisonExpression(1)), absolute(token))
        }

        override fun visitComparisonExpression(ctx: EjabiExprParser.ComparisonExpressionContext): Expression {
            val left = visit(ctx.additiveExpression(0))
            val operator =
                when {
                    ctx.GE() != null -> BinaryOperator.GE
                    ctx.LE() != null -> BinaryOperator.LE
                    ctx.GT() != null -> BinaryOperator.GT
                    ctx.LT() != null -> BinaryOperator.LT
                    else -> return left
                }
            val token =
                (
                    ctx.GE()
                        ?: ctx.LE()
                        ?: ctx.GT()
                        ?: ctx.LT()
                )!!
                    .symbol
            return BinaryOpExpression(left, operator, visit(ctx.additiveExpression(1)), absolute(token))
        }

        override fun visitAdditiveExpression(ctx: EjabiExprParser.AdditiveExpressionContext): Expression {
            var result = visit(ctx.multiplicativeExpression(0))
            for (i in 1 until ctx.multiplicativeExpression().size) {
                val operator = if (ctx.PLUS(i - 1) != null) BinaryOperator.ADD else BinaryOperator.SUB
                val token = (ctx.PLUS(i - 1) ?: ctx.MINUS(i - 1))!!.symbol
                result = BinaryOpExpression(result, operator, visit(ctx.multiplicativeExpression(i)), absolute(token))
            }
            return result
        }

        override fun visitMultiplicativeExpression(ctx: EjabiExprParser.MultiplicativeExpressionContext): Expression {
            var result = visit(ctx.unaryExpression(0))
            for (i in 1 until ctx.unaryExpression().size) {
                val operator =
                    when {
                        ctx.STAR(i - 1) != null -> BinaryOperator.MUL
                        ctx.SLASH(i - 1) != null -> BinaryOperator.DIV
                        else -> BinaryOperator.MOD
                    }
                val token = (ctx.STAR(i - 1) ?: ctx.SLASH(i - 1) ?: ctx.PERCENT(i - 1))!!.symbol
                result = BinaryOpExpression(result, operator, visit(ctx.unaryExpression(i)), absolute(token))
            }
            return result
        }

        override fun visitUnaryExpression(ctx: EjabiExprParser.UnaryExpressionContext): Expression =
            when {
                ctx.NOT() != null -> NotExpression(visit(ctx.unaryExpression()))
                ctx.MINUS() != null -> UnaryMinusExpression(visit(ctx.unaryExpression()), absolute(ctx.MINUS().symbol))
                ctx.PLUS() != null -> visit(ctx.unaryExpression())
                else -> visit(ctx.postfixExpression())
            }

        override fun visitPostfixExpression(ctx: EjabiExprParser.PostfixExpressionContext): Expression {
            var result = visit(ctx.primaryExpression())
            for (part in ctx.postfixPart()) {
                result =
                    if (part.DOT() != null) {
                        MemberAccessExpression(result, part.IDENT().text, absolute(part.IDENT().symbol))
                    } else {
                        IndexAccessExpression(result, visit(part.ternaryExpression()), absolute(part.LBRACKET().symbol))
                    }
            }
            return result
        }

        override fun visitPrimaryExpression(ctx: EjabiExprParser.PrimaryExpressionContext): Expression =
            when {
                ctx.STRING() != null -> {
                    val text = ctx.STRING().text
                    LiteralExpression(unescape(text.substring(1, text.length - 1)))
                }

                ctx.NUMBER() != null -> {
                    numberLiteral(ctx.NUMBER().text)
                }

                ctx.TRUE() != null -> {
                    LiteralExpression(true)
                }

                ctx.FALSE() != null -> {
                    LiteralExpression(false)
                }

                ctx.NULL() != null -> {
                    LiteralExpression(null)
                }

                ctx.IDENT() != null -> {
                    VariableExpression(ctx.IDENT().text, absolute(ctx.IDENT().symbol))
                }

                else -> {
                    visit(ctx.ternaryExpression())
                }
            }

        private fun numberLiteral(text: String): Expression =
            text.toLongOrNull()?.let { LiteralExpression(it) }
                ?: LiteralExpression(text.toDoubleOrNull() ?: throw ExpressionParseException("Invalid number literal: $text"))

        /**
         * Same escape set as before the ANTLR migration: `\n`, `\t`, `\r`,
         * `\\`, `\'`, `\"`; unknown escapes keep the escaped character.
         */
        private fun unescape(value: String): String {
            if ('\\' !in value) return value
            val result = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    val escaped =
                        when (value[i + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            '\\' -> '\\'
                            '\'' -> '\''
                            '"' -> '"'
                            else -> null
                        }
                    if (escaped != null) {
                        result.append(escaped)
                        i += 2
                        continue
                    }
                }
                result.append(c)
                i++
            }
            return result.toString()
        }
    }

    private class ExpressionErrorListener(
        private val base: Position?,
    ) : BaseErrorListener() {
        private val errors = mutableListOf<String>()

        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?,
        ) {
            errors += "${toAbsolutePosition(base, line, charPositionInLine)}: $msg"
        }

        fun throwIfErrors() {
            if (errors.isNotEmpty()) {
                throw ExpressionParseException("Invalid expression: ${errors.joinToString("; ")}")
            }
        }
    }

    companion object {
        /** Parses without a template position; used by tests and simple callers. */
        fun parse(expression: String): Expression = ExpressionParser().parse(expression)
    }
}

/**
 * Maps a line and 0-based column inside a tag content string onto absolute
 * template coordinates using [base], the position where the content starts.
 * Without a base the coordinates stay local to the content.
 */
internal fun toAbsolutePosition(
    base: Position?,
    line: Int,
    charPositionInLine: Int,
): Position =
    when (base) {
        null -> {
            Position(line, charPositionInLine + 1)
        }

        else -> {
            Position(
                base.line + line - 1,
                if (line == 1) base.col + charPositionInLine else charPositionInLine + 1,
            )
        }
    }

/**
 * Evaluator for expression AST.
 */
internal object ExpressionEvaluator {
    fun evaluate(
        expression: Expression,
        context: RenderContext,
    ): Any? =
        when (expression) {
            is LiteralExpression -> {
                expression.value
            }

            is VariableExpression -> {
                if (context.strict && !context.contains(expression.name)) {
                    throw TemplateRenderException(positionPrefix(expression.position) + "Undefined variable '${expression.name}'")
                }
                context.get(expression.name)
            }

            is MemberAccessExpression -> {
                evaluateMemberAccess(expression, context)
            }

            is IndexAccessExpression -> {
                evaluateIndexAccess(expression, context)
            }

            is BinaryOpExpression -> {
                evaluateBinaryOp(expression, context)
            }

            is TernaryExpression -> {
                evaluateTernary(expression, context)
            }

            is NotExpression -> {
                !isTruthy(evaluate(expression.operand, context))
            }

            is UnaryMinusExpression -> {
                evaluateUnaryMinus(expression, context)
            }
        }

    private fun evaluateMemberAccess(
        expression: MemberAccessExpression,
        context: RenderContext,
    ): Any? {
        val target = evaluate(expression.target, context)
        if (target == null) {
            if (context.strict) {
                throw TemplateRenderException(positionPrefix(expression.position) + "Cannot access member '${expression.member}' of null")
            }
            return null
        }

        val resolution =
            memberCache.getOrPut(target::class to expression.member) {
                resolveMember(target::class, expression.member)
            }

        if (resolution.property != null) {
            return invokeMember(expression, target) { resolution.property.get(target) }
        }
        if (resolution.function != null) {
            return invokeMember(expression, target) { resolution.function.call(target) }
        }

        if (target is Map<*, *>) {
            if (context.strict && !target.containsKey(expression.member)) {
                throw TemplateRenderException(positionPrefix(expression.position) + "Missing key '${expression.member}' in map")
            }
            return target[expression.member]
        }

        if (context.strict) {
            throw TemplateRenderException(
                positionPrefix(expression.position) + "No member '${expression.member}' on ${target.javaClass.name}",
            )
        }
        return null
    }

    private fun resolveMember(
        kClass: KClass<*>,
        member: String,
    ): MemberResolution {
        val property = kClass.memberProperties.firstOrNull { it.name == member && it.isAccessibleVisibility() }
        if (property != null) {
            @Suppress("UNCHECKED_CAST")
            return MemberResolution(property as KProperty1<Any, *>, null)
        }

        val getterName = "get${member.replaceFirstChar { it.uppercase() }}"
        val function =
            kClass.memberFunctions.firstOrNull {
                it.name == getterName && it.parameters.size == 1 && it.isAccessibleVisibility()
            }
        return MemberResolution(null, function)
    }

    private fun KCallable<*>.isAccessibleVisibility(): Boolean = visibility == KVisibility.PUBLIC || visibility == KVisibility.INTERNAL

    private inline fun invokeMember(
        expression: MemberAccessExpression,
        target: Any,
        invocation: () -> Any?,
    ): Any? =
        try {
            invocation()
        } catch (e: InvocationTargetException) {
            throw TemplateRenderException(
                positionPrefix(expression.position) + "Member '${expression.member}' of ${target.javaClass.name} threw an exception",
                e.cause,
            )
        } catch (e: IllegalCallableAccessException) {
            throw TemplateRenderException(
                positionPrefix(expression.position) + "Cannot access member '${expression.member}' of ${target.javaClass.name}",
                e,
            )
        }

    private class MemberResolution(
        val property: KProperty1<Any, *>?,
        val function: KFunction<*>?,
    )

    private val memberCache = ConcurrentHashMap<Pair<KClass<*>, String>, MemberResolution>()

    private fun evaluateIndexAccess(
        expression: IndexAccessExpression,
        context: RenderContext,
    ): Any? {
        val target = evaluate(expression.target, context)
        if (target == null) {
            if (context.strict) {
                throw TemplateRenderException(positionPrefix(expression.position) + "Cannot index null value")
            }
            return null
        }
        val index = evaluate(expression.index, context)

        return when (target) {
            is List<*> -> {
                val i = boundedIndex(target.size, index, context, expression.position) ?: return null
                target[i]
            }

            is Array<*> -> {
                val i = boundedIndex(target.size, index, context, expression.position) ?: return null
                target[i]
            }

            is Map<*, *> -> {
                if (context.strict && !target.containsKey(index)) {
                    throw TemplateRenderException(positionPrefix(expression.position) + "Missing key '$index' in map")
                }
                target[index]
            }

            is String -> {
                val i = boundedIndex(target.length, index, context, expression.position) ?: return null
                target[i].toString()
            }

            else -> {
                if (context.strict) {
                    throw TemplateRenderException(
                        positionPrefix(expression.position) + "Value of type ${target::class.qualifiedName} is not indexable",
                    )
                }
                null
            }
        }
    }

    /**
     * Resolves an index expression against a zero-based, bounded collection.
     * Returns null in lenient mode when the index is not a number or falls
     * outside the bounds; strict mode throws [TemplateRenderException] for
     * both cases instead.
     */
    private fun boundedIndex(
        size: Int,
        index: Any?,
        context: RenderContext,
        position: Position?,
    ): Int? {
        val i = index.asInt()
        if (i == null) {
            if (context.strict) {
                throw TemplateRenderException(positionPrefix(position) + "Index must be a number, got ${typeName(index)}")
            }
            return null
        }
        if (i < 0 || i >= size) {
            if (context.strict) {
                throw TemplateRenderException(positionPrefix(position) + "Index $i out of bounds for size $size")
            }
            return null
        }
        return i
    }

    private fun evaluateBinaryOp(
        expression: BinaryOpExpression,
        context: RenderContext,
    ): Any? {
        if (expression.operator == BinaryOperator.AND) {
            val left = evaluate(expression.left, context)
            return isTruthy(left) && isTruthy(evaluate(expression.right, context))
        }
        if (expression.operator == BinaryOperator.OR) {
            val left = evaluate(expression.left, context)
            return isTruthy(left) || isTruthy(evaluate(expression.right, context))
        }

        val left = evaluate(expression.left, context)
        val right = evaluate(expression.right, context)

        val at = positionPrefix(expression.position)

        return when (expression.operator) {
            BinaryOperator.ADD -> {
                when {
                    left is String || right is String -> "$left$right"
                    else -> numericOp(BinaryOperator.ADD, left, right, context, expression.position, { a, b -> a + b }, { a, b -> a + b })
                }
            }

            BinaryOperator.SUB -> {
                numericOp(BinaryOperator.SUB, left, right, context, expression.position, { a, b -> a - b }, { a, b -> a - b })
            }

            BinaryOperator.MUL -> {
                numericOp(BinaryOperator.MUL, left, right, context, expression.position, { a, b -> a * b }, { a, b -> a * b })
            }

            BinaryOperator.DIV -> {
                numericOp(
                    BinaryOperator.DIV,
                    left,
                    right,
                    context,
                    expression.position,
                    { a, b -> if (b == 0L) throw TemplateRenderException(at + "Division by zero") else a / b },
                    { a, b -> a / b },
                )
            }

            BinaryOperator.MOD -> {
                numericOp(
                    BinaryOperator.MOD,
                    left,
                    right,
                    context,
                    expression.position,
                    { a, b -> if (b == 0L) throw TemplateRenderException(at + "Remainder by zero") else a % b },
                    { a, b -> a % b },
                )
            }

            BinaryOperator.EQ -> {
                valuesEqual(left, right)
            }

            BinaryOperator.NE -> {
                !valuesEqual(left, right)
            }

            BinaryOperator.LT -> {
                compareValues(left, right)?.let { it < 0 }
            }

            BinaryOperator.GT -> {
                compareValues(left, right)?.let { it > 0 }
            }

            BinaryOperator.LE -> {
                compareValues(left, right)?.let { it <= 0 }
            }

            BinaryOperator.GE -> {
                compareValues(left, right)?.let { it >= 0 }
            }

            BinaryOperator.AND -> {
                error("AND is evaluated before eager operands")
            }

            BinaryOperator.OR -> {
                error("OR is evaluated before eager operands")
            }
        }
    }

    private fun evaluateTernary(
        expression: TernaryExpression,
        context: RenderContext,
    ): Any? {
        val condition = isTruthy(evaluate(expression.condition, context))
        return if (condition) {
            evaluate(expression.trueExpr, context)
        } else {
            evaluate(expression.falseExpr, context)
        }
    }

    private fun evaluateUnaryMinus(
        expression: UnaryMinusExpression,
        context: RenderContext,
    ): Any? {
        val value = evaluate(expression.operand, context)
        return when (value) {
            is java.math.BigInteger -> {
                value.negate()
            }

            is Number -> {
                if (isIntegral(value)) -value.toLong() else -value.toDouble()
            }

            else -> {
                if (context.strict) {
                    throw TemplateRenderException(positionPrefix(expression.position) + "Cannot negate ${typeName(value)}")
                }
                null
            }
        }
    }

    private fun valuesEqual(
        left: Any?,
        right: Any?,
    ): Boolean {
        if (left is Number && right is Number) {
            return when {
                // BigInteger and Long ranges exceed what Double can represent
                // exactly, so integral values must be compared without a
                // floating-point round trip.
                left is java.math.BigInteger && right is java.math.BigInteger -> left.compareTo(right) == 0

                isIntegral(left) && isIntegral(right) -> left.toLong() == right.toLong()

                else -> left.toDouble() == right.toDouble()
            }
        }
        return left == right
    }

    private fun numericOp(
        operator: BinaryOperator,
        left: Any?,
        right: Any?,
        context: RenderContext,
        position: Position?,
        longOp: (Long, Long) -> Long,
        doubleOp: (Double, Double) -> Double,
    ): Any? {
        if (left !is Number || right !is Number) {
            if (context.strict) {
                throw TemplateRenderException(
                    positionPrefix(position) +
                        "Cannot apply operator '${operatorSymbol(operator)}' to ${typeName(left)} and ${typeName(right)}",
                )
            }
            return null
        }
        return if (isIntegral(left) && isIntegral(right)) {
            longOp(left.toLong(), right.toLong())
        } else {
            doubleOp(left.toDouble(), right.toDouble())
        }
    }

    private fun operatorSymbol(operator: BinaryOperator): String =
        when (operator) {
            BinaryOperator.ADD -> "+"
            BinaryOperator.SUB -> "-"
            BinaryOperator.MUL -> "*"
            BinaryOperator.DIV -> "/"
            BinaryOperator.MOD -> "%"
            BinaryOperator.EQ -> "=="
            BinaryOperator.NE -> "!="
            BinaryOperator.LT -> "<"
            BinaryOperator.GT -> ">"
            BinaryOperator.LE -> "<="
            BinaryOperator.GE -> ">="
            BinaryOperator.AND -> "&&"
            BinaryOperator.OR -> "||"
        }

    private fun typeName(value: Any?): String = value?.let { it::class.simpleName } ?: "null"

    private fun isIntegral(value: Number): Boolean =
        value is Byte || value is Short || value is Int || value is Long || value is java.math.BigInteger

    private fun compareValues(
        left: Any?,
        right: Any?,
    ): Int? {
        if (left is Number && right is Number) {
            return when {
                left is java.math.BigInteger && right is java.math.BigInteger -> left.compareTo(right)
                isIntegral(left) && isIntegral(right) -> left.toLong().compareTo(right.toLong())
                else -> left.toDouble().compareTo(right.toDouble())
            }
        }
        if (left is Comparable<*> && right != null) {
            @Suppress("UNCHECKED_CAST")
            return try {
                (left as Comparable<Any>).compareTo(right)
            } catch (e: ClassCastException) {
                null
            }
        }
        return null
    }

    fun isTruthy(value: Any?): Boolean =
        when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotEmpty()
            null -> false
            else -> true
        }

    private fun Any?.asInt(): Int? = (this as? Number)?.toInt()
}
