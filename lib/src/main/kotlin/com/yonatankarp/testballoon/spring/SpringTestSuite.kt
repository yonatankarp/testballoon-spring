package com.yonatankarp.testballoon.spring

import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import io.mockk.mockk
import org.springframework.context.ApplicationContext

/**
 * Runs the [content] block against the Spring context described by carrier type [C].
 *
 * Must be called inside a `testSuite { }` body, because testBalloon's compiler plugin
 * only registers suites declared directly as `val X by testSuite { }`:
 *
 * ```
 * val MySuite by testSuite {
 *     springTest<MyWebContext> {
 *         val service = mockBean<MyService>()
 *         test("...") { every { service.x() } returns ...; bean<WebTestClient>()... }
 *     }
 * }
 * ```
 *
 * The context is built once per [springTest] block and shared by its tests; mocks are
 * reset before each test.
 */
public inline fun <reified C : SpringTestConfig> TestSuite.springTest(
    // noinline: the lambda is forwarded to the non-inline overload below.
    noinline content: SpringSuiteScope.() -> Unit,
): Unit = springTest(C::class.java, content)

public fun TestSuite.springTest(carrier: Class<out SpringTestConfig>, content: SpringSuiteScope.() -> Unit) {
    SpringSuiteScope(this, carrier).content()
}

/** Receiver of a [springTest] block: declares mocks and tests for one Spring context. */
public class SpringSuiteScope internal constructor(
    private val suite: TestSuite,
    private val carrier: Class<out SpringTestConfig>,
) {
    @PublishedApi internal val mocks: MutableList<RegisteredMock> = mutableListOf()

    private val contextFixture: TestFixture<SpringContext> =
        with(suite) {
            testFixture { SpringContext.load(carrier, mocks) } closeWith { close() }
        }

    /**
     * Registers a [mockk] mock as a primary bean in the context, overriding any real
     * bean of the same type. The mock is reset before each test.
     *
     * @param name bean name to register under; defaults to a generated, type-derived name.
     *   Provide it to satisfy a `@Qualifier`/named injection point.
     * @param relaxed see mockk's relaxed mocks (unstubbed calls return defaults).
     */
    public inline fun <reified T : Any> mockBean(name: String? = null, relaxed: Boolean = false): T {
        val mock = mockk<T>(relaxed = relaxed)
        mocks += RegisteredMock(T::class.java, mock, name)
        return mock
    }

    /** Declares a test that runs against the loaded context. */
    public fun test(name: String, action: suspend SpringTestScope.() -> Unit) {
        with(suite) {
            test(name) {
                val context = contextFixture()
                context.beforeTest()
                try {
                    SpringTestScope(context.applicationContext).action()
                } finally {
                    context.afterTest()
                }
            }
        }
    }
}

/** Receiver of a [SpringSuiteScope.test] body: looks up beans from the live context. */
public class SpringTestScope internal constructor(public val applicationContext: ApplicationContext) {
    /** Returns the unique bean of type [T] from the context (a real bean or a [SpringSuiteScope.mockBean]). */
    public inline fun <reified T : Any> bean(): T = applicationContext.getBean(T::class.java)

    /** Returns the bean named [name] of type [T] from the context. */
    public inline fun <reified T : Any> bean(name: String): T = applicationContext.getBean(name, T::class.java)
}
