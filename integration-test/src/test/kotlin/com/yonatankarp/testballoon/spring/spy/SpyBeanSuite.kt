package com.yonatankarp.testballoon.spring.spy

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean

class Calculator {
    fun square(n: Int): Int = n * n
}

@SpringBootConfiguration
class CalculatorConfig {
    @Bean
    fun calculator(): Calculator = Calculator()
}

@SpringBootTest(classes = [CalculatorConfig::class])
class SpyContext : SpringTestConfig()

// spyBean wraps the REAL bean: unstubbed calls run the real implementation, while calls
// are recorded and individual calls can be overridden — Spring @SpyBean parity.
val SpyBeanSuite by testSuite {
    springTest<SpyContext> {
        val calculator by spyBean<Calculator>()

        test("unstubbed calls run the real implementation and are recorded") {
            check(calculator.square(3) == 9)

            verify(exactly = 1) { calculator.square(3) }
        }

        test("a stub overrides the real behaviour for a specific argument") {
            every { calculator.square(3) } returns 1000

            check(calculator.square(3) == 1000)
            // Other arguments still run the real implementation.
            check(calculator.square(4) == 16)
        }

        test("the spy is reset before each test (previous stub and calls cleared)") {
            // The stub from the previous test is gone, so the real implementation runs.
            check(calculator.square(3) == 9)

            verify(exactly = 0) { calculator.square(4) }
        }

        test("the spy is the bean Spring injected, not a detached copy") {
            check(bean<Calculator>() === calculator)
        }
    }
}
