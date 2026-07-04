package com.yoshisuproject.ejabi

/**
 * Custom escaping applied to escaped output tags (`<%= %>`) and, when
 * [EjabiEngine] is constructed with `escape = false`, to `<%== %>` as well.
 *
 * Provided as a functional interface so both Kotlin lambdas and Java lambdas
 * can implement it. The default implementation used by [EjabiEngine] escapes
 * `& < > " ' \` and `=`; raw output tags (`<%== %>` with `escape = true`)
 * never pass through the escape function.
 */
fun interface EscapeFunction {
    /**
     * Returns the escaped form of [value]. The value is never null; null
     * expression results are rendered as empty strings before escaping.
     */
    fun escape(value: String): String
}
