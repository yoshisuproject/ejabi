package com.yoshisuproject.ejabi.spring.webmvc

import com.yoshisuproject.ejabi.EjabiEngine
import com.yoshisuproject.ejabi.Template
import com.yoshisuproject.ejabi.spring.boot.autoconfigure.EjabiProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.io.Resource
import org.springframework.core.io.ResourceLoader
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Spring MVC [View] that renders a compiled Ejabi template.
 */
class EjabiView(
    private val template: Template,
    private val contentType: String,
) : View {
    override fun getContentType(): String = contentType

    override fun render(
        model: Map<String, *>?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        response.contentType = contentType
        template.renderTo(response.writer, model ?: emptyMap())
    }
}

/**
 * Spring MVC [ViewResolver] that resolves view names to Ejabi templates.
 *
 * View names are resolved as `<prefix><viewName><suffix>` against the
 * application [ResourceLoader]; unresolved names return `null` so the next
 * view resolver in the chain can try.
 */
class EjabiViewResolver(
    private val engine: EjabiEngine,
    private val resourceLoader: ResourceLoader,
    private val properties: EjabiProperties,
) : ViewResolver,
    Ordered {
    private val templateCache = ConcurrentHashMap<String, Template>()

    override fun getOrder(): Int = properties.order

    override fun resolveViewName(
        viewName: String,
        locale: Locale,
    ): View? {
        val location = properties.prefix + viewName + properties.suffix
        val resource = resourceLoader.getResource(location)
        if (!resource.exists()) {
            return null
        }
        return EjabiView(loadTemplate(location, resource), properties.contentType)
    }

    private fun loadTemplate(
        location: String,
        resource: Resource,
    ): Template {
        if (!properties.cache) {
            return compile(location, resource)
        }
        return templateCache.computeIfAbsent(location) { compile(location, resource) }
    }

    private fun compile(
        location: String,
        resource: Resource,
    ): Template =
        resource.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            engine.compile(reader, location)
        }
}
