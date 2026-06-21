package com.yonatankarp.testballoon.spring

import io.mockk.mockk
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private interface Calc {
    fun add(a: Int, b: Int): Int
}

private class RealCalc : Calc {
    override fun add(a: Int, b: Int) = a + b
}

private fun beanFactory(vararg defs: Pair<String, Class<*>>) = DefaultListableBeanFactory().apply {
    defs.forEach { (name, type) -> registerBeanDefinition(name, RootBeanDefinition(type)) }
}

private fun processorFor(vararg doubles: RegisteredDouble) = TestDoubleBeanFactoryPostProcessor(doubles.toList())

class TestDoubleBeanFactoryPostProcessorTest {
    @Test
    fun `a sole unnamed mock replaces the single real bean and re-uses its name`() {
        val factory = beanFactory("realCalc" to RealCalc::class.java)
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        // The real definition is gone, replaced under its own name by the mock instance.
        assertEquals(listOf("realCalc"), factory.getBeanNamesForType(Calc::class.java).toList())
        assertEquals(mock, factory.getBean("realCalc"))
        assertTrue(factory.getBeanDefinition("realCalc").isPrimary)
    }

    @Test
    fun `a named mock registers under that name without removing siblings`() {
        val factory = beanFactory()
        val a = mockk<Calc>()
        val b = mockk<Calc>()

        processorFor(
            RegisteredDouble.Mock(Calc::class.java, a, name = "a"),
            RegisteredDouble.Mock(Calc::class.java, b, name = "b"),
        ).postProcessBeanFactory(factory)

        assertEquals(a, factory.getBean("a"))
        assertEquals(b, factory.getBean("b"))
        // Neither is primary when several mocks share a type.
        assertFalse(factory.getBeanDefinition("a").isPrimary)
    }

    @Test
    fun `an unnamed mock with no existing bean registers under a generated name`() {
        val factory = beanFactory()
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        assertEquals(mock, factory.getBean(Calc::class.java))
    }

    @Test
    fun `spyBean fails clearly when no bean of the type exists`() {
        val factory = beanFactory()

        val error = assertFailsWith<IllegalStateException> {
            processorFor(RegisteredDouble.Spy(Calc::class.java, name = null, holder = SpyHolder()))
                .postProcessBeanFactory(factory)
        }

        assertContains(error.message!!, "found no bean")
    }

    @Test
    fun `spyBean fails clearly when several beans of the type exist without a name`() {
        val factory = beanFactory("a" to RealCalc::class.java, "b" to RealCalc::class.java)

        val error = assertFailsWith<IllegalStateException> {
            processorFor(RegisteredDouble.Spy(Calc::class.java, name = null, holder = SpyHolder()))
                .postProcessBeanFactory(factory)
        }

        assertContains(error.message!!, "pass a name")
    }

    @Test
    fun `a sole unnamed mock skips a same-type bean exposed only as a singleton`() {
        // getBeanNamesForType also returns manually-registered singletons that have no
        // bean definition; the removal loop must skip those rather than fail.
        val factory = beanFactory()
        factory.registerSingleton("singletonCalc", RealCalc())
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        assertTrue(factory.getBeanNamesForType(Calc::class.java).any { factory.getBean(it) === mock })
    }
}
