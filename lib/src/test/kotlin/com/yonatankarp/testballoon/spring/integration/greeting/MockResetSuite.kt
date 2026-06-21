package com.yonatankarp.testballoon.spring.integration.greeting

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest

@WebFluxTest(GreetingController::class)
class ResetContext : SpringTestConfig()

// Tests run in declaration order; the second test of each pair proves the reset
// that ran before it (clearMocks wipes both stubs and recorded calls).
val MockResetSuite by testSuite {
    springTest<ResetContext> {
        val greetingService = mockBean<GreetingService>(relaxed = true)

        test("stubs configured in a test are visible within it") {
            every { greetingService.greet("k") } returns "stubbed"

            check(greetingService.greet("k") == "stubbed")
        }

        test("stubs from the previous test are cleared (relaxed default returned)") {
            check(greetingService.greet("k") == "")
        }

        test("a call is recorded within its test") {
            greetingService.greet("z")

            verify(exactly = 1) { greetingService.greet("z") }
        }

        test("recorded calls from the previous test are cleared") {
            verify(exactly = 0) { greetingService.greet("z") }
        }
    }
}
