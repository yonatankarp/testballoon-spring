package com.yonatankarp.testballoon.spring.coroutine

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.coEvery
import io.mockk.coVerify
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.test.web.reactive.server.WebTestClient

@WebFluxTest(SuspendGreetingController::class)
class SuspendContext : SpringTestConfig()

// Test bodies are suspend (testBalloon), so suspend mocks work without a runBlocking wrapper.
val CoroutineGreetingSuite by testSuite {
    springTest<SuspendContext> {
        val greeter = mockBean<SuspendGreeter>()

        test("suspend collaborators stub and verify with coEvery/coVerify") {
            coEvery { greeter.greet("Rob") } returns "Hi, Rob!"

            bean<WebTestClient>()
                .get()
                .uri("/suspend-greet/Rob")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.message").isEqualTo("Hi, Rob!")

            coVerify { greeter.greet("Rob") }
        }
    }
}
