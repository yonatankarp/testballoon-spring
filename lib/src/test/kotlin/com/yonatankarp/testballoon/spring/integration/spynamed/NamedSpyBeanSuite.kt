package com.yonatankarp.testballoon.spring.integration.spynamed

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

@SpringBootConfiguration
class CounterConfig {
    @Bean
    fun primaryCounter(): Counter = Counter(10)

    @Bean
    fun secondaryCounter(): Counter = Counter(20)
}

@SpringBootTest(classes = [CounterConfig::class])
class NamedSpyContext : SpringTestConfig()

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
            check(bean<Counter>("primaryCounter").next() == 11)
        }

        test("the named spy is the very bean Spring holds under that name") {
            check(bean<Counter>("secondaryCounter") === secondary)
        }
    }
}
