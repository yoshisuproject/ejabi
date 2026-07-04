package com.yoshisuproject.ejabi.spring.webmvc

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@SpringBootTest(classes = [EjabiViewIntegrationTest.TestApplication::class])
@AutoConfigureMockMvc
class EjabiViewIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `controller view name renders ejabi template`() {
        mockMvc
            .get("/hello")
            .andExpect {
                status { isOk() }
                content {
                    string("Hello World!")
                    contentType("text/html;charset=UTF-8")
                }
            }
    }

    @Test
    fun `template output is html escaped`() {
        mockMvc
            .get("/escaped")
            .andExpect {
                status { isOk() }
                content { string("&lt;b&gt;safe&lt;/b&gt;") }
            }
    }

    @Test
    fun `view name without a matching template falls through the resolver chain`() {
        mockMvc
            .get("/missing-view")
            .andExpect {
                status { isOk() }
                forwardedUrl("no-such-template")
            }
    }

    @Test
    fun `template parse failure surfaces the ejabi exception`() {
        assertThatThrownBy { mockMvc.get("/broken") }
            .isInstanceOf(jakarta.servlet.ServletException::class.java)
            .hasRootCauseInstanceOf(com.yoshisuproject.ejabi.TemplateParseException::class.java)
    }

    @SpringBootApplication
    class TestApplication {
        @Controller
        class PageController {
            @GetMapping("/hello")
            fun hello(model: Model): String {
                model.addAttribute("name", "World")
                return "hello"
            }

            @GetMapping("/escaped")
            fun escaped(model: Model): String {
                model.addAttribute("content", "<b>safe</b>")
                return "escaped"
            }

            @GetMapping("/missing-view")
            fun missingView(): String = "no-such-template"

            @GetMapping("/broken")
            fun broken(): String = "broken"
        }
    }
}
