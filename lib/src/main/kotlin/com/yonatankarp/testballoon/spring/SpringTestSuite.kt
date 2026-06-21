package com.yonatankarp.testballoon.spring

import de.infix.testBalloon.framework.core.TestFixture
import de.infix.testBalloon.framework.core.TestSuite
import io.mockk.mockk
import org.springframework.context.ApplicationContext
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

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
    @PublishedApi internal val doubles: MutableList<RegisteredDouble> = mutableListOf()

    private val contextFixture: TestFixture<SpringContext> =
        with(suite) {
            testFixture { SpringContext.load(carrier, doubles) } closeWith { close() }
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
        doubles += RegisteredDouble.Mock(T::class.java, mock, name)
        return mock
    }

    /**
     * Registers a mockk [spy][io.mockk.spyk] that wraps the **real** bean of type [T]
     * (Spring `@SpyBean` parity): unstubbed calls run the real implementation while calls
     * are recorded and may be selectively overridden. The spy is reset before each test.
     *
     * The real bean only exists once the context is built, so the spy cannot be returned
     * directly; use a property delegate, which resolves the live spy on each access:
     *
     * ```
     * val service by spyBean<GreetingService>()
     * test("...") { every { service.greet("Ada") } returns "stub"; /* other calls run for real */ }
     * ```
     *
     * @param name bean name to wrap; defaults to the unique bean of type [T] in the context.
     *   Provide it when several beans of [T] exist (e.g. `@Qualifier` injection points).
     */
    public inline fun <reified T : Any> spyBean(name: String? = null): SpyBeanDelegate<T> {
        val holder = SpyHolder()
        doubles += RegisteredDouble.Spy(T::class.java, name, holder)
        return SpyBeanDelegate(holder)
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

/**
 * Property delegate returned by [SpringSuiteScope.spyBean]. Reading the property yields
 * the live spy for the current test (it is created when the context loads and reset before
 * each test), so it must be read inside a test body, not at declaration time.
 */
public class SpyBeanDelegate<T : Any> @PublishedApi internal constructor(private val holder: SpyHolder) :
    ReadOnlyProperty<Any?, T> {
    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T = holder.spy as T
}

/** Receiver of a [SpringSuiteScope.test] body: looks up beans from the live context. */
public class SpringTestScope internal constructor(public val applicationContext: ApplicationContext) {
    /** Returns the unique bean of type [T] from the context (a real bean or a [SpringSuiteScope.mockBean]). */
    public inline fun <reified T : Any> bean(): T = applicationContext.getBean(T::class.java)

    /** Returns the bean named [name] of type [T] from the context. */
    public inline fun <reified T : Any> bean(name: String): T = applicationContext.getBean(name, T::class.java)
}
