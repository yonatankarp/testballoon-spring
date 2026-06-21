package com.yonatankarp.testballoon.spring.spyprimary

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.it.GreetingService
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
class PrimaryGreetingConfig {
    @Bean
    @Primary
    fun primaryGreetingService(): GreetingService = object : GreetingService {
        override fun greet(name: String) = "REAL:$name"
    }
}

@SpringBootTest(classes = [PrimaryGreetingConfig::class])
class PrimarySpyContext : SpringTestConfig()

// Regression: spying a @Primary bean must not leave two primary candidates of the type.
val PrimarySpyBeanSuite by testSuite {
    springTest<PrimarySpyContext> {
        val greeter by spyBean<GreetingService>()

        test("a @Primary bean can be spied and still resolves uniquely by type") {
            // Resolving by type must work (no NoUniqueBeanDefinitionException) and run the real impl.
            check(bean<GreetingService>().greet("x") == "REAL:x")

            every { greeter.greet("x") } returns "SPY"

            check(bean<GreetingService>().greet("x") == "SPY")
        }
    }
}
