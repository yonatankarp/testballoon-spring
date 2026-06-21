package com.yonatankarp.testballoon.spring.internal

import de.infix.testBalloon.framework.core.testSuite
import io.mockk.mockk
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition

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

val TestDoubleBeanFactoryPostProcessorSuite by testSuite {
    test("a sole unnamed mock replaces the single real bean and re-uses its name") {
        val factory = beanFactory("realCalc" to RealCalc::class.java)
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        check(factory.getBeanNamesForType(Calc::class.java).toList() == listOf("realCalc"))
        check(factory.getBean("realCalc") == mock)
        check(factory.getBeanDefinition("realCalc").isPrimary)
    }

    test("a named mock registers under that name without removing siblings") {
        val factory = beanFactory()
        val a = mockk<Calc>()
        val b = mockk<Calc>()

        processorFor(
            RegisteredDouble.Mock(Calc::class.java, a, name = "a"),
            RegisteredDouble.Mock(Calc::class.java, b, name = "b"),
        ).postProcessBeanFactory(factory)

        check(factory.getBean("a") == a)
        check(factory.getBean("b") == b)
        check(!factory.getBeanDefinition("a").isPrimary)
    }

    test("an unnamed mock with no existing bean registers under a generated name") {
        val factory = beanFactory()
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        check(factory.getBean(Calc::class.java) == mock)
    }

    test("spyBean fails clearly when no bean of the type exists") {
        val factory = beanFactory()

        val error = runCatching {
            processorFor(RegisteredDouble.Spy(Calc::class.java, name = null, holder = SpyHolder()))
                .postProcessBeanFactory(factory)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        check(error.message!!.contains("found no bean"))
    }

    test("spyBean fails clearly when several beans of the type exist without a name") {
        val factory = beanFactory("a" to RealCalc::class.java, "b" to RealCalc::class.java)

        val error = runCatching {
            processorFor(RegisteredDouble.Spy(Calc::class.java, name = null, holder = SpyHolder()))
                .postProcessBeanFactory(factory)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        check(error.message!!.contains("pass a name"))
    }

    test("a sole unnamed mock skips a same-type bean exposed only as a singleton") {
        val factory = beanFactory()
        factory.registerSingleton("singletonCalc", RealCalc())
        val mock = mockk<Calc>()

        processorFor(RegisteredDouble.Mock(Calc::class.java, mock, name = null)).postProcessBeanFactory(factory)

        check(factory.getBeanNamesForType(Calc::class.java).any { factory.getBean(it) === mock })
    }
}
