package com.yoshisuproject.ejabi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EjabiJavaApiTest {
    @Test
    void rendersTemplatesFromJava() {
        EjabiEngine engine = new EjabiEngine();

        assertEquals("Hello", engine.render("Hello"));
        assertEquals("Hello Java", engine.render("Hello <%= name %>", Map.of("name", "Java")));
    }

    @Test
    void rendersCompiledTemplatesFromJava() {
        EjabiEngine engine = new EjabiEngine();
        Template template = engine.compile("Hi <%= name %>");

        assertEquals("Hi Ada", template.render(Map.of("name", "Ada")));

        StringBuilder output = new StringBuilder();
        template.renderTo(output, Map.of("name", "Grace"));
        assertEquals("Hi Grace", output.toString());
    }

    @Test
    void doesNotExposePublicTemplateConstructorsToJava() {
        assertFalse(Arrays.stream(Template.class.getConstructors())
                .anyMatch(constructor -> constructor.getParameterCount() == 1));
    }

    @Test
    void exposesExceptionHierarchyToJava() {
        EjabiEngine engine = new EjabiEngine();

        assertEquals("broken", new TemplateParseException("broken").getMessage());
        assertEquals("bad expression", new ExpressionParseException("bad expression").getMessage());
        assertEquals("bad render", new TemplateRenderException("bad render").getMessage());

        EjabiException exception = assertThrows(EjabiException.class, () -> engine.compile("<%= %>"));
        assertEquals(ExpressionParseException.class, exception.getClass());
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    void supportsCustomEscapeFunctionLambdaFromJava() {
        EjabiEngine engine = new EjabiEngine(false, true, true, value -> "[" + value + "]");

        assertEquals("[<b>]", engine.render("<%= content %>", Map.of("content", "<b>")));
        assertEquals("<b>", engine.render("<%== content %>", Map.of("content", "<b>")));
    }

    @Test
    void annotatesErrorsWithFileNameFromJava() {
        EjabiEngine engine = new EjabiEngine();

        TemplateParseException exception =
                assertThrows(TemplateParseException.class, () -> engine.compile("<% if show %>", "page.ejb"));

        assertTrue(exception.getMessage().startsWith("page.ejb: "));
    }
}
