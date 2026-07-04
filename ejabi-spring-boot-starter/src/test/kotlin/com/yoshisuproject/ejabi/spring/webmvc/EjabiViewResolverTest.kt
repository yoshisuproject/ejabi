package com.yoshisuproject.ejabi.spring.webmvc

import com.yoshisuproject.ejabi.EjabiEngine
import com.yoshisuproject.ejabi.TemplateParseException
import com.yoshisuproject.ejabi.spring.boot.autoconfigure.EjabiProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

class EjabiViewResolverTest {
    @TempDir
    lateinit var templateDir: Path

    private fun resolver(cache: Boolean): EjabiViewResolver {
        val properties =
            EjabiProperties().apply {
                prefix = templateDir.toUri().toString()
                this.cache = cache
            }
        return EjabiViewResolver(EjabiEngine(), DefaultResourceLoader(), properties)
    }

    private fun render(view: org.springframework.web.servlet.View): String {
        val response = MockHttpServletResponse()
        view.render(emptyMap<String, Any>(), MockHttpServletRequest(), response)
        return response.contentAsString
    }

    @Test
    fun `missing template resolves to null so the next resolver can try`() {
        assertThat(resolver(cache = true).resolveViewName("missing", Locale.ENGLISH)).isNull()
    }

    @Test
    fun `cached resolver keeps serving the compiled template after the file changes`() {
        Files.writeString(templateDir.resolve("page.ejb"), "one")
        val resolver = resolver(cache = true)

        assertThat(render(resolver.resolveViewName("page", Locale.ENGLISH)!!)).isEqualTo("one")

        Files.writeString(templateDir.resolve("page.ejb"), "two")
        assertThat(render(resolver.resolveViewName("page", Locale.ENGLISH)!!)).isEqualTo("one")
    }

    @Test
    fun `uncached resolver recompiles the template when the file changes`() {
        Files.writeString(templateDir.resolve("page.ejb"), "one")
        val resolver = resolver(cache = false)

        assertThat(render(resolver.resolveViewName("page", Locale.ENGLISH)!!)).isEqualTo("one")

        Files.writeString(templateDir.resolve("page.ejb"), "two")
        assertThat(render(resolver.resolveViewName("page", Locale.ENGLISH)!!)).isEqualTo("two")
    }

    @Test
    fun `resolved view uses the configured content type`() {
        Files.writeString(templateDir.resolve("page.ejb"), "hi")
        val view = resolver(cache = true).resolveViewName("page", Locale.ENGLISH)!!

        assertThat(view.contentType).isEqualTo("text/html;charset=UTF-8")
    }

    @Test
    fun `compile errors are annotated with the template location`() {
        Files.writeString(templateDir.resolve("broken.ejb"), "<% if show %>unclosed")

        val exception =
            assertThrows(TemplateParseException::class.java) {
                resolver(cache = true).resolveViewName("broken", Locale.ENGLISH)
            }

        assertThat(exception.message).contains("broken.ejb")
    }
}
