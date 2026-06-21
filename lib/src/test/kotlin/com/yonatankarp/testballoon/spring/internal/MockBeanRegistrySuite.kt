package com.yonatankarp.testballoon.spring.internal

import de.infix.testBalloon.framework.core.TestConfig
import de.infix.testBalloon.framework.core.aroundEachTest
import de.infix.testBalloon.framework.core.testSuite
import io.mockk.mockk

val MockBeanRegistrySuite by testSuite(
    testConfig = TestConfig.aroundEachTest { action ->
        MockBeanRegistry.pop()
        try {
            action()
        } finally {
            MockBeanRegistry.pop()
        }
    },
) {
    test("current is empty when nothing has been pushed") {
        check(MockBeanRegistry.current().isEmpty())
    }

    test("push then current returns the doubles, pop clears them") {
        val doubles = listOf<RegisteredDouble>(RegisteredDouble.Mock(Any::class.java, mockk<Any>(), name = null))

        MockBeanRegistry.push(doubles)
        check(MockBeanRegistry.current() == doubles)

        MockBeanRegistry.pop()
        check(MockBeanRegistry.current().isEmpty())
    }

    test("a nested push on the same thread is rejected") {
        MockBeanRegistry.push(emptyList())

        val error = runCatching { MockBeanRegistry.push(emptyList()) }.exceptionOrNull()
        check(error is IllegalStateException)
    }
}
