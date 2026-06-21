package com.yonatankarp.testballoon.spring.it

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

// Full @SpringBootTest context (not a slice) proves the bridge is slice-agnostic:
// the same springTest/mockBean/bean DSL drives a whole application context.
@SpringBootTest
@AutoConfigureWebTestClient
class GreetingAppContext : SpringTestConfig()

val GreetingAppSuite by testSuite {
    springTest<GreetingAppContext> {
        val greetingService = mockBean<GreetingService>()

        test("full application context serves the mocked greeting") {
            every { greetingService.greet("Linus") } returns "Yo, Linus!"

            bean<WebTestClient>()
                .get()
                .uri("/greet/Linus")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.message").isEqualTo("Yo, Linus!")
        }
    }
}
