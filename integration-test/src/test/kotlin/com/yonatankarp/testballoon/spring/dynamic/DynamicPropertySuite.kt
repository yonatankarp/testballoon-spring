package com.yonatankarp.testballoon.spring.dynamic

import com.yonatankarp.testballoon.spring.SpringTestConfig
import com.yonatankarp.testballoon.spring.springTest
import de.infix.testBalloon.framework.core.testSuite
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootConfiguration
class DynamicConfig

@SpringBootTest(classes = [DynamicConfig::class])
class DynamicContext : SpringTestConfig() {
    // @DynamicPropertySource is read off a static method of the carrier class, the same
    // way Spring reads it off a JUnit test class — the driven TestContextManager picks it up.
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("app.dynamic") { "computed-at-runtime" }
        }
    }
}

val DynamicPropertySuite by testSuite {
    springTest<DynamicContext> {
        test("a @DynamicPropertySource on the carrier registers a property") {
            check(bean<Environment>().getProperty("app.dynamic") == "computed-at-runtime")
        }
    }
}
