package com.yoshisuproject.ejabi.internal

import com.yoshisuproject.ejabi.TemplateParseException
import com.yoshisuproject.ejabi.internal.parser.EjabiLexer
import com.yoshisuproject.ejabi.internal.parser.EjabiParser
import com.yoshisuproject.ejabi.internal.parser.EjabiParserBaseVisitor
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.TerminalNode
import java.io.Reader

/**
 * ANTLR-backed parser for Ejabi templates.
 */
internal class TemplateParser {
    /**
     * Parses the template into executable statements.
     *
     * [trim] controls Erubi-style standalone-line trimming: when false,
     * whitespace around standalone `<% %>` and `<%# %>` lines is preserved.
     * The `-%>`/`=%>` close-tag trims are applied regardless of [trim],
     * matching Erubi's tail handling.
     */
    fun parse(
        reader: Reader,
        trim: Boolean,
    ): List<Statement> {
        val errorListener = TemplateErrorListener()
        val lexer = EjabiLexer(CharStreams.fromReader(reader))
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)

        val parser = EjabiParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)

        val tree = parser.template()
        errorListener.throwIfErrors()

        return StatementCompiler().compile(StatementVisitor(trim).visit(tree))
    }

    private sealed class TemplateElement {
        data class TextElement(
            val text: String,
        ) : TemplateElement()

        data class ExpressionElement(
            val expression: Expression,
            val escapedTag: Boolean,
            val trimTrailingWhitespace: Boolean,
            val position: Position,
        ) : TemplateElement()

        data class CodeElement(
            val code: String,
            val position: Position,
        ) : TemplateElement()

        data object CommentElement : TemplateElement()
    }

    private class StatementVisitor(
        private val trim: Boolean,
    ) : EjabiParserBaseVisitor<List<TemplateElement>>() {
        override fun visitTemplate(ctx: EjabiParser.TemplateContext): List<TemplateElement> =
            trimElements(ctx.element().flatMap { visit(it) }, trim)

        override fun visitElement(ctx: EjabiParser.ElementContext): List<TemplateElement> {
            ctx.TEXT()?.let { return listOf(TemplateElement.TextElement(it.text)) }
            ctx.LT()?.let { return listOf(TemplateElement.TextElement("<")) }
            ctx.LITERAL_OPEN()?.let { return listOf(TemplateElement.TextElement("<%")) }
            ctx.escapedExpression()?.let {
                return listOf(
                    TemplateElement.ExpressionElement(
                        ExpressionParser(expressionBase(it, it.TAG_CONTENT())).parse(it.TAG_CONTENT()?.text ?: ""),
                        escapedTag = true,
                        trimTrailingWhitespace = it.closeTag().trimsTrailingWhitespace(),
                        position = positionOf(it),
                    ),
                )
            }
            ctx.rawExpression()?.let {
                return listOf(
                    TemplateElement.ExpressionElement(
                        ExpressionParser(expressionBase(it, it.TAG_CONTENT())).parse(it.TAG_CONTENT()?.text ?: ""),
                        escapedTag = false,
                        trimTrailingWhitespace = it.closeTag().trimsTrailingWhitespace(),
                        position = positionOf(it),
                    ),
                )
            }
            ctx.codeBlock()?.let {
                return listOf(
                    TemplateElement.CodeElement(
                        contentOf(it.TAG_CONTENT()).trim(),
                        position = positionOf(it),
                    ),
                )
            }
            ctx.commentBlock()?.let {
                return listOf(TemplateElement.CommentElement)
            }
            return emptyList()
        }

        /** 1-based line/column of the first token of a tag context. */
        private fun positionOf(ctx: org.antlr.v4.runtime.ParserRuleContext): Position =
            Position(ctx.start.line, ctx.start.charPositionInLine + 1)

        /**
         * Base position for expression parsing: the start of the tag content
         * when present so expression tokens map onto absolute template
         * coordinates, otherwise the tag start.
         */
        private fun expressionBase(
            tag: org.antlr.v4.runtime.ParserRuleContext,
            content: TerminalNode?,
        ): Position {
            val symbol = content?.symbol
            return if (symbol != null) Position(symbol.line, symbol.charPositionInLine + 1) else positionOf(tag)
        }

        private fun contentOf(node: TerminalNode?): String = node?.text?.trim() ?: ""

        private fun EjabiParser.CloseTagContext.trimsTrailingWhitespace(): Boolean = CLOSE_TRIM() != null || CLOSE_EQ_TRIM() != null

        private fun trimElements(
            elements: List<TemplateElement>,
            trim: Boolean,
        ): List<TemplateElement> {
            val result = elements.toMutableList()
            for (index in result.indices) {
                when (val element = result[index]) {
                    is TemplateElement.CodeElement,
                    is TemplateElement.CommentElement,
                    -> {
                        if (trim) {
                            trimControlLine(result, index)
                        }
                    }

                    is TemplateElement.ExpressionElement -> {
                        if (element.trimTrailingWhitespace) {
                            trimFollowingWhitespace(result, index + 1)
                        }
                    }

                    is TemplateElement.TextElement -> {
                        Unit
                    }
                }
            }
            return result.filterNot { it is TemplateElement.TextElement && it.text.isEmpty() }
        }

        private fun trimControlLine(
            elements: MutableList<TemplateElement>,
            index: Int,
        ) {
            val previousTextIndex = (index - 1).takeIf { it >= 0 && elements[it] is TemplateElement.TextElement }
            val nextTextIndex = (index + 1).takeIf { it < elements.size && elements[it] is TemplateElement.TextElement }
            val previousTrim =
                previousTextIndex?.let {
                    trimmableLineSuffix((elements[it] as TemplateElement.TextElement).text)
                }
            val nextTrim =
                nextTextIndex?.let {
                    trimmableLinePrefix((elements[it] as TemplateElement.TextElement).text)
                }

            if (previousTrim != null && nextTrim != null) {
                val previous = elements[previousTextIndex] as TemplateElement.TextElement
                elements[previousTextIndex] = previous.copy(text = previous.text.dropLast(previousTrim))

                val next = elements[nextTextIndex] as TemplateElement.TextElement
                elements[nextTextIndex] = next.copy(text = next.text.drop(nextTrim))
            }
        }

        private fun trimFollowingWhitespace(
            elements: MutableList<TemplateElement>,
            startIndex: Int,
        ) {
            val textIndex = startIndex.takeIf { it < elements.size && elements[it] is TemplateElement.TextElement } ?: return
            val text = elements[textIndex] as TemplateElement.TextElement
            val trimLength = trimmableLinePrefix(text.text) ?: return
            elements[textIndex] = text.copy(text = text.text.drop(trimLength))
        }

        private fun trimmableLineSuffix(text: String): Int? {
            val lineStart = text.lastIndexOf('\n') + 1
            val suffix = text.substring(lineStart)
            return if (suffix.all { it == ' ' || it == '\t' }) suffix.length else null
        }

        private fun trimmableLinePrefix(text: String): Int? {
            val newlineIndex =
                when {
                    text.startsWith("\r\n") -> 1
                    else -> text.indexOf('\n')
                }
            if (newlineIndex == -1) return null
            val prefix = text.substring(0, newlineIndex)
            return if (prefix.all { it == ' ' || it == '\t' || it == '\r' }) newlineIndex + 1 else null
        }
    }

    private class StatementCompiler {
        private val ifPattern = Regex("""^if\s+(.+)$""")
        private val forPattern = Regex("""^for\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\s+in\s+(.+)$""")

        fun compile(elements: List<TemplateElement>): List<Statement> {
            val result = parseBlock(elements, 0, emptySet(), 0)
            if (result.nextIndex != elements.size) {
                throw TemplateParseException("Unexpected parser state at element ${result.nextIndex}")
            }
            return result.statements
        }

        private fun parseBlock(
            elements: List<TemplateElement>,
            startIndex: Int,
            stopCodes: Set<String>,
            depth: Int,
        ): ParseResult {
            if (depth > MAX_NESTING_DEPTH) {
                throw TemplateParseException("Template nesting exceeds the maximum depth of $MAX_NESTING_DEPTH")
            }
            val statements = mutableListOf<Statement>()
            var index = startIndex

            while (index < elements.size) {
                when (val element = elements[index]) {
                    is TemplateElement.TextElement -> {
                        statements += TextStatement(element.text)
                        index++
                    }

                    is TemplateElement.ExpressionElement -> {
                        statements += ExpressionStatement(element.expression, element.escapedTag)
                        index++
                    }

                    is TemplateElement.CodeElement -> {
                        val code = element.code
                        if (code in stopCodes) {
                            return ParseResult(statements, index, code)
                        }
                        val ifMatch = ifPattern.matchEntire(code)
                        val forMatch = forPattern.matchEntire(code)
                        when {
                            ifMatch != null -> {
                                val condition = ifMatch.groupValues[1]
                                val thenBlock = parseBlock(elements, index + 1, setOf("else", "end"), depth + 1)
                                val elseBlock =
                                    if (thenBlock.stopCode == "else") {
                                        parseBlock(elements, thenBlock.nextIndex + 1, setOf("end"), depth + 1)
                                    } else {
                                        ParseResult(emptyList(), thenBlock.nextIndex, thenBlock.stopCode)
                                    }
                                if (elseBlock.stopCode != "end") {
                                    throw TemplateParseException(positionPrefix(element.position) + "Missing <% end %> for if statement")
                                }
                                statements +=
                                    IfStatement(
                                        ExpressionParser(element.position).parse(condition),
                                        thenBlock.statements,
                                        elseBlock.statements,
                                    )
                                index = elseBlock.nextIndex + 1
                            }

                            forMatch != null -> {
                                val body = parseBlock(elements, index + 1, setOf("end"), depth + 1)
                                if (body.stopCode != "end") {
                                    throw TemplateParseException(positionPrefix(element.position) + "Missing <% end %> for for statement")
                                }
                                statements +=
                                    ForStatement(
                                        forMatch.groupValues[1],
                                        ExpressionParser(element.position).parse(forMatch.groupValues[2]),
                                        body.statements,
                                        element.position,
                                    )
                                index = body.nextIndex + 1
                            }

                            code == "else" -> {
                                throw TemplateParseException(positionPrefix(element.position) + "Unexpected <% else %>")
                            }

                            code == "end" -> {
                                throw TemplateParseException(positionPrefix(element.position) + "Unexpected <% end %>")
                            }

                            else -> {
                                statements += CodeStatement(code, element.position)
                                index++
                            }
                        }
                    }

                    is TemplateElement.CommentElement -> {
                        index++
                    }
                }
            }

            return ParseResult(statements, index, null)
        }

        private data class ParseResult(
            val statements: List<Statement>,
            val nextIndex: Int,
            val stopCode: String?,
        )

        private companion object {
            const val MAX_NESTING_DEPTH = 500
        }
    }

    private class TemplateErrorListener : BaseErrorListener() {
        private val errors = mutableListOf<String>()

        override fun syntaxError(
            recognizer: Recognizer<*, *>?,
            offendingSymbol: Any?,
            line: Int,
            charPositionInLine: Int,
            msg: String,
            e: RecognitionException?,
        ) {
            errors += "$line:${charPositionInLine + 1}: $msg"
        }

        fun throwIfErrors() {
            if (errors.isNotEmpty()) {
                throw TemplateParseException("Template parse error: ${errors.joinToString("; ")}")
            }
        }
    }
}
