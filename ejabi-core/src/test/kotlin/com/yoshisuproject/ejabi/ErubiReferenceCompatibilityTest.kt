package com.yoshisuproject.ejabi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ErubiReferenceCompatibilityTest {
    private val engine = EjabiEngine()

    @Test
    fun `renders templates with no tags`() {
        assertEquals("a\n", engine.render("a\n"))
    }

    @Test
    fun `renders erubi style table with escaped and raw output`() {
        data class Item(
            val number: Int,
            val value: String,
        )

        val template =
            """
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
            """.trimIndent()

        val result =
            engine.render(
                template,
                mapOf("items" to listOf(Item(1, "&'<>" + '"' + "2"))),
            )

        assertEquals(
            """
            <table>
             <tbody>
              <tr>
               <td>1</td>
               <td>&'<>"2</td>
              </tr>
             </tbody>
            </table>
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `escaped output escapes apostrophes and text keeps slashes and quotes`() {
        val template =
            """
            <table>
             <tbody>' ' \\ \\
              <tr>
               <td><%= value -%>
            </td>
              </tr>
            </table>
            """.trimIndent()

        val result = engine.render(template, mapOf("value" to "&'<>\"`=2"))

        assertEquals(
            """
            <table>
             <tbody>' ' \\ \\
              <tr>
               <td>&amp;&#39;&lt;&gt;&quot;&#x60;&#x3D;2</td>
              </tr>
            </table>
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun `literal and comment tags match erubi delimiter behavior`() {
        val template =
            """
            <table>
            <%% for item in items %>
              <tr>
                <td><%# item.number %></td>
                <td><%# item.value %></td>
              </tr>
              <%% end %>
            </table>
            """.trimIndent()

        assertEquals(
            """
            <table>
            <% for item in items %>
              <tr>
                <td></td>
                <td></td>
              </tr>
              <% end %>
            </table>
            """.trimIndent(),
            engine.render(template),
        )
    }

    @Test
    fun `code and comment tags trim only whitespace-only line sides`() {
        val template =
            """
              <% var a = 'A' %>  
            a
              <%- var b = 'B' %>  
            b
              <%# hidden %>  
            c
             /<% var a = 'X' %>  
            a
              <% var a = 'Y' %> /
            a
            """.trimIndent()

        assertEquals(
            """
            a
            b
            c
             /  
            a
               /
            a
            """.trimIndent(),
            engine.render(template),
        )
    }

    @Test
    fun `expression trim markers remove trailing whitespace and newline`() {
        assertEquals("1</td>", engine.render("<%= 1 -%>\n</td>"))
        assertEquals("1</td>", engine.render("<%= 1 =%>\n</td>"))
        assertEquals("<b>1</b></td>", engine.render("<%== '<b>1</b>' =%>\n</td>"))
    }

    @Test
    fun `default escaping matches ejabi escape mode`() {
        val value = "&'<>\"`=2"

        assertEquals("&amp;&#39;&lt;&gt;&quot;&#x60;&#x3D;2", engine.render("<%= value %>", mapOf("value" to value)))
        assertEquals("&'<>\"`=2", engine.render("<%== value %>", mapOf("value" to value)))
    }

    @Test
    fun `erubi debug output tag is not supported`() {
        assertThrows(ExpressionParseException::class.java) {
            engine.compile("<%=== value %>")
        }
    }

    @Test
    fun `ruby source generation statements are outside ejabi runtime scope`() {
        // A ruby each block is not a supported code statement; it fails at render time.
        val template = engine.compile("<% items.each do |item| %>")
        assertThrows(TemplateRenderException::class.java) {
            template.render(mapOf("items" to listOf(1)))
        }

        // Its <% end %> has no matching ejabi block opener and fails at compile time.
        assertThrows(TemplateParseException::class.java) {
            engine.compile("<% items.each do |item| %><%= item %><% end %>")
        }
    }

    @Test
    fun `ejabi structured loop is the supported replacement for ruby each blocks`() {
        val template = "<% for item in items %><%= item %>,<% end %>"

        assertEquals("1,2,3,", engine.render(template, mapOf("items" to listOf(1, 2, 3))))
    }
}
