package com.yoshisuproject.ejabi

import com.yoshisuproject.ejabi.internal.HtmlEscapeFunction
import com.yoshisuproject.ejabi.internal.RenderContext
import com.yoshisuproject.ejabi.internal.Statement
import com.yoshisuproject.ejabi.internal.TemplateParser
import java.io.Reader
import java.io.StringReader

/**
 * Ejabi template engine.
 * Inspired by Erubi (Ruby) but implemented in Kotlin.
 *
 * Kotlin callers should construct the engine with named arguments; the
 * Java overloads generated for the defaulted parameters are positional.
 *
 * @param strict when true, rendering throws [TemplateRenderException] for
 *   missing variables and members instead of silently rendering empty output.
 * @param escape selects the Erubi escape matrix for output tags: when true
 *   (the default), `<%= %>` escapes and `<%== %>` renders raw; when false the
 *   two tags swap roles, matching Erubi's own `escape: false` default.
 * @param trim when true (the default), whitespace-only indentation around
 *   standalone `<% %>` and `<%# %>` lines is removed. When false, standalone
 *   lines keep their whitespace; the `-%>` and `=%>` close-tag trims are
 *   unaffected by this option.
 * @param escapeFunction escaping applied whenever an output tag resolves to
 *   escaped output. Defaults to the built-in HTML escape, which escapes
 *   `& < > " ' \` and `=`.
 */
class EjabiEngine
    @JvmOverloads
    constructor(
        private val strict: Boolean = false,
        private val escape: Boolean = true,
        private val trim: Boolean = true,
        private val escapeFunction: EscapeFunction = HtmlEscapeFunction,
    ) {
        private val parser = TemplateParser()

        /**
         * Compile a template from string.
         */
        fun compile(template: String): Template = compile(StringReader(template))

        /**
         * Compile a template from string, annotating parse and render errors
         * with [fileName].
         */
        fun compile(
            template: String,
            fileName: String?,
        ): Template = compile(StringReader(template), fileName)

        /**
         * Compile a template from reader.
         *
         * The reader is fully consumed and closed by this method
         * (ANTLR's `CharStreams.fromReader` closes it).
         */
        fun compile(reader: Reader): Template = compile(reader, null)

        /**
         * Compile a template from reader, annotating parse and render errors
         * with [fileName].
         *
         * The reader is fully consumed and closed by this method
         * (ANTLR's `CharStreams.fromReader` closes it).
         */
        fun compile(
            reader: Reader,
            fileName: String?,
        ): Template {
            val statements =
                try {
                    parser.parse(reader, trim)
                } catch (e: TemplateParseException) {
                    throw TemplateParseException(annotateFileName(e.message, fileName), e.cause)
                } catch (e: ExpressionParseException) {
                    throw ExpressionParseException(annotateFileName(e.message, fileName), e.cause)
                }
            return Template.from(statements, strict, escape, escapeFunction, fileName)
        }

        /**
         * Render a template directly with given context.
         */
        @JvmOverloads
        fun render(
            template: String,
            context: Map<String, Any?> = emptyMap(),
        ): String = compile(template).render(context)
    }

/**
 * Compiled template that can be rendered multiple times.
 */
class Template private constructor(
    private val statements: List<Statement>,
    private val strict: Boolean,
    private val escape: Boolean,
    private val escapeFunction: EscapeFunction,
    private val fileName: String?,
) {
    internal companion object {
        fun from(
            statements: List<Statement>,
            strict: Boolean = false,
            escape: Boolean = true,
            escapeFunction: EscapeFunction = HtmlEscapeFunction,
            fileName: String? = null,
        ): Template = Template(statements, strict, escape, escapeFunction, fileName)
    }

    /**
     * Render template with given context.
     */
    @JvmOverloads
    fun render(context: Map<String, Any?> = emptyMap()): String {
        val output = StringBuilder()
        renderTo(output, context)
        return output.toString()
    }

    /**
     * Render template to appendable output.
     */
    fun renderTo(out: Appendable) {
        renderTo(out, emptyMap())
    }

    /**
     * Render template to appendable output with the given context.
     */
    fun renderTo(
        out: Appendable,
        context: Map<String, Any?>,
    ) {
        val ctx = RenderContext.fromMap(context, strict, escape, escapeFunction)

        try {
            for (statement in statements) {
                statement.execute(ctx, out)
            }
        } catch (e: TemplateRenderException) {
            throw TemplateRenderException(annotateFileName(e.message, fileName), e.cause)
        }
    }

    /**
     * Render template to appendable output with the given context.
     */
    @Deprecated(
        "Use renderTo(out, context) instead",
        ReplaceWith("renderTo(out, context)"),
    )
    fun renderTo(
        context: Map<String, Any?> = emptyMap(),
        out: Appendable,
    ) {
        renderTo(out, context)
    }
}

private fun annotateFileName(
    message: String?,
    fileName: String?,
): String =
    when {
        fileName == null -> message ?: "Template failure"
        message == null -> fileName
        else -> "$fileName: $message"
    }
