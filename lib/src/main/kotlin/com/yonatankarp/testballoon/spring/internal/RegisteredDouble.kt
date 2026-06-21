package com.yonatankarp.testballoon.spring.internal

import io.mockk.spyk
import org.springframework.context.ApplicationContext

/**
 * A test double declared in a `springTest` block, queued for registration into the
 * context while it is built. A [Mock] is a freestanding mockk mock; a [Spy] wraps the
 * real bean of the same type once the context has created it.
 */
@PublishedApi
internal sealed interface RegisteredDouble {
    val type: Class<*>
    val name: String?

    /** The double instance handed back to the test, reset before each test. */
    val instance: Any

    /** Forces the double's bean to exist in [context] so [instance] is populated. */
    fun realizeIn(context: ApplicationContext)

    data class Mock(override val type: Class<*>, override val instance: Any, override val name: String?) :
        RegisteredDouble {
        override fun realizeIn(context: ApplicationContext) = Unit
    }

    data class Spy(override val type: Class<*>, override val name: String?, private val holder: SpyHolder) :
        RegisteredDouble {
        override val instance: Any get() = holder.spy

        fun wrap(realBean: Any): Any = holder.wrap(realBean)

        override fun realizeIn(context: ApplicationContext) {
            if (name != null) context.getBean(name, type) else context.getBean(type)
        }
    }
}

/**
 * Mutable cell shared between the `SpringSuiteScope` (which hands the spy back to the
 * test) and the post-processor (which fills it once the real bean is built). Read only
 * after the context has loaded, by which point [wrap] has populated it.
 */
@PublishedApi
internal class SpyHolder {
    private lateinit var captured: Any

    val spy: Any get() = captured

    fun wrap(realBean: Any): Any = spyk(realBean, recordPrivateCalls = false).also { captured = it }
}
