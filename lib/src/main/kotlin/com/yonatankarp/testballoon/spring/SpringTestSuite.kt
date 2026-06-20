package com.yonatankarp.testballoon.spring

import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import io.mockk.mockk

inline fun <reified C : SpringTestConfig> TestSuite.springTest(
    noinline content: SpringSuiteScope.() -> Unit,
) = springTest(C::class.java, content)

fun TestSuite.springTest(
    carrier: Class<out SpringTestConfig>,
    content: SpringSuiteScope.() -> Unit,
) {
    SpringSuiteScope(this, carrier).content()
}

class SpringSuiteScope(
    @PublishedApi internal val suite: TestSuite,
    @PublishedApi internal val carrier: Class<out SpringTestConfig>,
) {
    @PublishedApi internal val mocks = mutableListOf<RegisteredMock>()

    private val contextFixture: TestFixture<SpringContext> =
        with(suite) {
            testFixture { SpringContext.load(carrier, mocks) } closeWith { close() }
        }

    inline fun <reified T : Any> mockBean(relaxed: Boolean = false): T {
        val mock = mockk<T>(relaxed = relaxed)
        mocks += RegisteredMock(T::class.java, mock)
        return mock
    }

    fun test(name: String, action: suspend SpringTestScope.() -> Unit) {
        with(suite) {
            test(name) {
                val context = contextFixture()
                context.beforeTest()
                try {
                    SpringTestScope(context).action()
                } finally {
                    context.afterTest()
                }
            }
        }
    }
}

class SpringTestScope(@PublishedApi internal val context: SpringContext) {
    val applicationContext get() = context.applicationContext

    inline fun <reified T : Any> bean(): T = context.applicationContext.getBean(T::class.java)
}
