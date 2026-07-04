package com.yoshisuproject.ejabi.spring.boot.autoconfigure

import com.yoshisuproject.ejabi.EjabiEngine
import com.yoshisuproject.ejabi.spring.webmvc.EjabiViewResolver
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ResourceLoader
import org.springframework.web.servlet.ViewResolver

/**
 * Auto-configuration for the Ejabi template engine and its Spring MVC view resolver.
 */
@AutoConfiguration
@ConditionalOnClass(EjabiEngine::class)
@EnableConfigurationProperties(EjabiProperties::class)
class EjabiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun ejabiEngine(properties: EjabiProperties): EjabiEngine =
        EjabiEngine(
            strict = properties.strict,
            escape = properties.escape,
            trim = properties.trim,
        )

    /**
     * View resolver registration, separated so the webmvc types referenced
     * here are only loaded when Spring MVC is actually on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ViewResolver::class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    class EjabiViewResolverConfiguration {
        @Bean
        @ConditionalOnMissingBean
        fun ejabiViewResolver(
            engine: EjabiEngine,
            resourceLoader: ResourceLoader,
            properties: EjabiProperties,
        ): EjabiViewResolver = EjabiViewResolver(engine, resourceLoader, properties)
    }
}
