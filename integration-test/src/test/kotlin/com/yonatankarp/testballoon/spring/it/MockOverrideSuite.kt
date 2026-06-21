package com.yonatankarp.testballoon.spring.it

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RealGreetingConfig {
    @Bean
    fun greetingService(): GreetingService = object : GreetingService {
        override fun greet(name: String) = "REAL:$name"
    }
}

@SpringBootTest(classes = [RealGreetingConfig::class])
class OverrideContext : SpringTestConfig()

val MockOverrideSuite by testSuite {
    springTest<OverrideContext> {
        val greetingService = mockBean<GreetingService>()

        test("a mock replaces an existing real bean of the same type") {
            every { greetingService.greet("x") } returns "MOCK"

            // Without the override the real bean would answer "REAL:x".
            check(bean<GreetingService>().greet("x") == "MOCK")
        }
    }
}
