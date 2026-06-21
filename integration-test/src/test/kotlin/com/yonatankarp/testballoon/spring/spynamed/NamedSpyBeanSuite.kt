package com.yonatankarp.testballoon.spring.spynamed

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import io.mockk.verify
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean

class Counter(private val base: Int) {
    fun next(): Int = base + 1
}

// Two beans of the same type: spyBean<Counter>() with no name could not disambiguate,
// so the spy must be targeted by bean name.
@SpringBootConfiguration
class CounterConfig {
    @Bean
    fun primaryCounter(): Counter = Counter(10)

    @Bean
    fun secondaryCounter(): Counter = Counter(20)
}

@SpringBootTest(classes = [CounterConfig::class])
class NamedSpyContext : SpringTestConfig()

// spyBean(name = ...) wraps a specific named bean when several beans of the type exist,
// leaving the other real. Exercises the name-targeted spy path (resolveTargetName by name,
// the spy re-registered under its original name, and the named eager-realize branch).
val NamedSpyBeanSuite by testSuite {
    springTest<NamedSpyContext> {
        val secondary by spyBean<Counter>(name = "secondaryCounter")

        test("the named bean is wrapped and its real implementation runs by default") {
            check(secondary.next() == 21)

            verify(exactly = 1) { secondary.next() }
        }

        test("the named spy can be stubbed while the unwrapped bean stays real") {
            every { secondary.next() } returns 999

            check(secondary.next() == 999)
            // The other bean of the same type was never wrapped: still the real Counter(10).
            check(bean<Counter>("primaryCounter").next() == 11)
        }

        test("the named spy is the very bean Spring holds under that name") {
            check(bean<Counter>("secondaryCounter") === secondary)
        }
    }
}
