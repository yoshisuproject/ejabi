package com.yoshisuproject.ejabi.spring.boot.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.Ordered

/**
 * Configuration properties for the Ejabi template engine.
 */
@ConfigurationProperties(prefix = "ejabi")
class EjabiProperties {
    /**
     * Throw TemplateRenderException (com.yoshisuproject.ejabi) for missing
     * variables, members, and map keys instead of rendering empty output.
     */
    var strict: Boolean = false

    /**
     * Select the Erubi escape matrix for output tags: `<%= %>` escapes and
     * `<%== %>` renders raw when true; the two tags swap roles when false.
     */
    var escape: Boolean = true

    /**
     * Trim whitespace-only indentation around standalone `<% %>` and `<%# %>`
     * lines. When false, standalone lines keep their whitespace.
     */
    var trim: Boolean = true

    /**
     * Prefix prepended to view names when resolving templates.
     */
    var prefix: String = "classpath:/templates/"

    /**
     * Suffix appended to view names when resolving templates.
     */
    var suffix: String = ".ejb"

    /**
     * Cache compiled templates per resolved view path.
     */
    var cache: Boolean = true

    /**
     * Content type written to the response by resolved views.
     */
    var contentType: String = "text/html;charset=UTF-8"

    /**
     * Order of the ejabi view resolver relative to other view resolvers.
     */
    var order: Int = Ordered.LOWEST_PRECEDENCE - 10
}
