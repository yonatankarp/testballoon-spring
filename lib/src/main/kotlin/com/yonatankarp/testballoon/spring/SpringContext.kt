package com.yonatankarp.testballoon.spring

import io.mockk.clearMocks
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestContextManager
import java.lang.reflect.Method

internal class SpringContext private constructor(
    private val testContextManager: TestContextManager,
    private val carrierInstance: SpringTestConfig,
    private val doubles: List<RegisteredDouble>,
) {
    val applicationContext: ApplicationContext
        get() = testContextManager.testContext.applicationContext

    internal fun beforeTest() {
        testContextManager.beforeTestMethod(carrierInstance, CARRIER_METHOD)
        // A spy's instance only exists once its bean has been requested; eagerly realize
        // any unrealized spy here so every double starts each test in a known, reset state.
        doubles.forEach { it.realizeIn(applicationContext) }
        doubles.forEach { clearMocks(it.instance) }
    }

    internal fun afterTest() {
        testContextManager.afterTestMethod(carrierInstance, CARRIER_METHOD, null)
    }

    internal fun close() {
        testContextManager.afterTestClass()
    }

    internal companion object {
        private val CARRIER_METHOD: Method =
            SpringTestConfig::class.java.getDeclaredMethod("springTestBalloonCarrier")

        fun load(carrier: Class<out SpringTestConfig>, doubles: List<RegisteredDouble>): SpringContext {
            val instance = instantiate(carrier)
            MockBeanRegistry.push(doubles)
            try {
                val testContextManager = TestContextManager(carrier)
                testContextManager.beforeTestClass()
                testContextManager.prepareTestInstance(instance)
                return SpringContext(testContextManager, instance, doubles)
            } finally {
                MockBeanRegistry.pop()
            }
        }

        private fun instantiate(carrier: Class<out SpringTestConfig>): SpringTestConfig = try {
            carrier.getDeclaredConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            throw IllegalArgumentException(
                "Carrier ${carrier.name} must have a no-arg constructor.",
                e,
            )
        }
    }
}
