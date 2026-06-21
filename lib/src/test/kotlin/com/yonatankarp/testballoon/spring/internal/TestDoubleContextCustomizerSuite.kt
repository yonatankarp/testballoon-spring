package com.yonatankarp.testballoon.spring.internal

import de.infix.testBalloon.framework.core.testSuite
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.MergedContextConfiguration

val TestDoubleContextCustomizerSuite by testSuite {
    test("fails clearly when the context is not backed by a DefaultListableBeanFactory") {
        val context = mockk<ConfigurableApplicationContext>()
        every { context.beanFactory } returns mockk<ConfigurableListableBeanFactory>()
        val customizer = TestDoubleContextCustomizer(
            listOf(RegisteredDouble.Mock(Any::class.java, mockk<Any>(), name = null)),
        )

        val error = runCatching {
            customizer.customizeContext(context, mockk<MergedContextConfiguration>())
        }.exceptionOrNull()

        check(error is IllegalStateException)
        check(error.message!!.contains("DefaultListableBeanFactory"))
    }
}
