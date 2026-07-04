package com.yoshisuproject.ejabi

/**
 * Base exception for Ejabi template compilation and rendering failures.
 */
open class EjabiException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)

/**
 * Raised when template structure cannot be parsed.
 */
class TemplateParseException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : EjabiException(message, cause)

/**
 * Raised when an expression cannot be parsed.
 */
class ExpressionParseException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : EjabiException(message, cause)

/**
 * Raised when a template statement cannot be executed.
 */
class TemplateRenderException
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : EjabiException(message, cause)
