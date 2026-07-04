package com.yoshisuproject.ejabi.spring.boot.autoconfigure

import com.yoshisuproject.ejabi.EjabiEngine
import com.yoshisuproject.ejabi.spring.webmvc.EjabiViewResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader

class EjabiAutoConfigurationTest {
    private val contextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EjabiAutoConfiguration::class.java))

    @Test
    fun `provides engine and view resolver beans`() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(EjabiEngine::class.java)
            assertThat(context).hasSingleBean(EjabiViewResolver::class.java)
            assertThat(context).hasSingleBean(EjabiProperties::class.java)
        }
    }

    @Test
    fun `binds ejabi properties`() {
        contextRunner
            .withPropertyValues(
                "ejabi.strict=true",
                "ejabi.escape=false",
                "ejabi.trim=false",
                "ejabi.prefix=classpath:/views/",
                "ejabi.suffix=.html",
                "ejabi.cache=false",
                "ejabi.order=5",
            ).run { context ->
                val properties = context.getBean(EjabiProperties::class.java)
                assertThat(properties.strict).isTrue()
                assertThat(properties.escape).isFalse()
                assertThat(properties.trim).isFalse()
                assertThat(properties.prefix).isEqualTo("classpath:/views/")
                assertThat(properties.suffix).isEqualTo(".html")
                assertThat(properties.cache).isFalse()
                assertThat(properties.order).isEqualTo(5)
            }
    }

    @Test
    fun `escape and trim properties are propagated to the engine`() {
        contextRunner
            .withPropertyValues("ejabi.escape=false", "ejabi.trim=false")
            .run { context ->
                val engine = context.getBean(EjabiEngine::class.java)
                assertThat(engine.render("<%= v %>", mapOf("v" to "<b>"))).isEqualTo("<b>")
                assertThat(engine.render("a\n  <% var x = 1 %>\nb<%= x %>")).isEqualTo("a\n  \nb1")
            }
    }

    @Test
    fun `strict property is propagated to the engine`() {
        contextRunner
            .withPropertyValues("ejabi.strict=true")
            .run { context ->
                val engine = context.getBean(EjabiEngine::class.java)
                val template = engine.compile("<%= missing %>")
                org.assertj.core.api.Assertions
                    .assertThatThrownBy { template.render() }
                    .isInstanceOf(com.yoshisuproject.ejabi.TemplateRenderException::class.java)
            }
    }

    @Test
    fun `backs off when the user defines their own engine`() {
        contextRunner
            .withUserConfiguration(CustomEngineConfiguration::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(EjabiEngine::class.java)
                assertThat(context.getBean(EjabiEngine::class.java)).isSameAs(CustomEngineConfiguration.ENGINE)
            }
    }

    @Test
    fun `backs off when the user defines their own view resolver`() {
        contextRunner
            .withUserConfiguration(CustomViewResolverConfiguration::class.java)
            .run { context ->
                assertThat(context).hasSingleBean(EjabiViewResolver::class.java)
                assertThat(context.getBean(EjabiViewResolver::class.java))
                    .isSameAs(CustomViewResolverConfiguration.RESOLVER)
            }
    }

    @Test
    fun `non-web context gets the engine but no view resolver`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EjabiAutoConfiguration::class.java))
            .run { context ->
                assertThat(context).hasSingleBean(EjabiEngine::class.java)
                assertThat(context).doesNotHaveBean(EjabiViewResolver::class.java)
            }
    }

    @Test
    fun `backs off entirely when the ejabi engine class is absent`() {
        contextRunner
            .withClassLoader(FilteredClassLoader(EjabiEngine::class.java))
            .run { context ->
                assertThat(context).doesNotHaveBean(EjabiEngine::class.java)
                assertThat(context).doesNotHaveBean(EjabiViewResolver::class.java)
            }
    }

    @Test
    fun `provides only the engine when spring webmvc is absent`() {
        contextRunner
            .withClassLoader(FilteredClassLoader("org.springframework.web.servlet"))
            .run { context ->
                assertThat(context).hasSingleBean(EjabiEngine::class.java)
                assertThat(context).doesNotHaveBean(EjabiViewResolver::class.java)
            }
    }

    @Test
    fun `generated configuration metadata describes the ejabi properties`() {
        val metadata =
            requireNotNull(
                javaClass.getResourceAsStream("/META-INF/spring-configuration-metadata.json"),
            ) {
                "spring-configuration-metadata.json not found on classpath (is the configuration processor running?)"
            }.bufferedReader().readText()

        listOf(
            "ejabi.strict",
            "ejabi.escape",
            "ejabi.trim",
            "ejabi.prefix",
            "ejabi.suffix",
            "ejabi.cache",
            "ejabi.content-type",
            "ejabi.order",
        ).forEach { property -> assertThat(metadata).contains("\"$property\"") }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomEngineConfiguration {
        @Bean
        fun ejabiEngine(): EjabiEngine = ENGINE

        companion object {
            val ENGINE: EjabiEngine = EjabiEngine(strict = true)
        }
    }

    @Configuration(proxyBeanMethods = false)
    class CustomViewResolverConfiguration {
        @Bean
        fun ejabiViewResolver(): EjabiViewResolver = RESOLVER

        companion object {
            val RESOLVER: EjabiViewResolver =
                EjabiViewResolver(
                    EjabiEngine(),
                    DefaultResourceLoader(),
                    EjabiProperties(),
                )
        }
    }
}
