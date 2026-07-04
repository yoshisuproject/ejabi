package com.yoshisuproject.ejabi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ErubiReferenceCompatibilityJavaTest {
    private final EjabiEngine engine = new EjabiEngine();

    @Test
    void rendersTemplatesWithNoTags() {
        assertEquals("a\n", engine.render("a\n"));
    }

    @Test
    void rendersErubiStyleTableWithEscapedAndRawOutput() {
        String template = """
                <table>
                 <tbody>
                  <% for item in items %>
                  <tr>
                   <td><%= item.number %></td>
                   <td><%== item.value %></td>
                  </tr>
                 <% end %>
                 </tbody>
                </table>
                """.stripIndent().trim();

        String result = engine.render(template, Map.of(
                "items", List.of(new Item(1, "&'<>\"2"))));

        assertEquals("""
                <table>
                 <tbody>
                  <tr>
                   <td>1</td>
                   <td>&'<>"2</td>
                  </tr>
                 </tbody>
                </table>
                """.stripIndent().trim(), result);
    }

    @Test
    void escapedOutputEscapesApostrophesAndTextKeepsSlashesAndQuotes() {
        String template = """
                <table>
                 <tbody>' ' \\\\ \\\\
                  <tr>
                   <td><%= value -%>
                </td>
                  </tr>
                </table>
                """.stripIndent().trim();

        String result = engine.render(template, Map.of("value", "&'<>\"`=2"));

        assertEquals("""
                <table>
                 <tbody>' ' \\\\ \\\\
                  <tr>
                   <td>&amp;&#39;&lt;&gt;&quot;&#x60;&#x3D;2</td>
                  </tr>
                </table>
                """.stripIndent().trim(), result);
    }

    @Test
    void literalAndCommentTagsMatchErubiDelimiterBehavior() {
        String template = """
                <table>
                <%% for item in items %>
                  <tr>
                    <td><%# item.number %></td>
                    <td><%# item.value %></td>
                  </tr>
                  <%% end %>
                </table>
                """.stripIndent().trim();

        assertEquals("""
                <table>
                <% for item in items %>
                  <tr>
                    <td></td>
                    <td></td>
                  </tr>
                  <% end %>
                </table>
                """.stripIndent().trim(), engine.render(template));
    }

    @Test
    void codeAndCommentTagsTrimOnlyWhitespaceOnlyLineSides() {
        String template = "  <% var a = 'A' %>  \n"
                + "a\n"
                + "  <%- var b = 'B' %>  \n"
                + "b\n"
                + "  <%# hidden %>  \n"
                + "c\n"
                + " /<% var a = 'X' %>  \n"
                + "a\n"
                + "  <% var a = 'Y' %> /\n"
                + "a";

        assertEquals("a\nb\nc\n /  \na\n   /\na", engine.render(template));
    }

    @Test
    void expressionTrimMarkersRemoveTrailingWhitespaceAndNewline() {
        assertEquals("1</td>", engine.render("<%= 1 -%>\n</td>"));
        assertEquals("1</td>", engine.render("<%= 1 =%>\n</td>"));
        assertEquals("<b>1</b></td>", engine.render("<%== '<b>1</b>' =%>\n</td>"));
    }

    @Test
    void defaultEscapingMatchesEjabiEscapeMode() {
        String value = "&'<>\"`=2";

        assertEquals("&amp;&#39;&lt;&gt;&quot;&#x60;&#x3D;2", engine.render("<%= value %>", Map.of("value", value)));
        assertEquals("&'<>\"`=2", engine.render("<%== value %>", Map.of("value", value)));
    }

    @Test
    void erubiDebugOutputTagIsNotSupported() {
        assertThrows(ExpressionParseException.class, () -> engine.compile("<%=== value %>"));
    }

    @Test
    void rubySourceGenerationStatementsAreOutsideEjabiRuntimeScope() {
        assertThrows(TemplateParseException.class, () ->
                engine.compile("<% items.each do |item| %><%= item %><% end %>"));
    }

    @Test
    void ejabiStructuredLoopIsTheSupportedReplacementForRubyEachBlocks() {
        String template = "<% for item in items %><%= item %>,<% end %>";

        assertEquals("1,2,3,", engine.render(template, Map.of("items", List.of(1, 2, 3))));
    }

    public static final class Item {
        private final int number;
        private final String value;

        Item(int number, String value) {
            this.number = number;
            this.value = value;
        }

        public int getNumber() {
            return number;
        }

        public String getValue() {
            return value;
        }
    }
}
