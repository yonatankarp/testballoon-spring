package com.yonatankarp.testballoon.spring.integration.greeting

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(GreetingController::class)
class GreetingWebContext : SpringTestConfig()

val GreetingControllerSuite by testSuite {
    springTest<GreetingWebContext> {
        val greetingService = mockBean<GreetingService>()

        test("GET /greet/{name} returns the greeting from the mocked service") {
            every { greetingService.greet("Ada") } returns "Hello, Ada!"

            bean<WebTestClient>()
                .get()
                .uri("/greet/Ada")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.message").isEqualTo("Hello, Ada!")
        }

        test("each test gets a freshly reset mock") {
            every { greetingService.greet("Grace") } returns "Hi, Grace!"

            bean<WebTestClient>()
                .get()
                .uri("/greet/Grace")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.message").isEqualTo("Hi, Grace!")
        }
    }
}
