package com.yoshisuproject.ejabi.internal

/**
 * A 1-based line and column position within a template, used to prefix parse
 * and render error messages (e.g., `3:12: Undefined variable 'user'`) so
 * failures locate back into the template source. Erubi preserves template
 * line numbers in its generated code for the same reason; Ejabi interprets
 * templates, so positions travel with the AST instead.
 */
internal data class Position(
    val line: Int,
    val col: Int,
) {
    override fun toString(): String = "$line:$col"
}

/**
 * Renders the `line:col: ` prefix for error messages, or the empty string
 * when no position is available.
 */
internal fun positionPrefix(position: Position?): String = if (position == null) "" else "$position: "
