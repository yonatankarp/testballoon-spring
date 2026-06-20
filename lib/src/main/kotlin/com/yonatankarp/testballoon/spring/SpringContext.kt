package com.yonatankarp.testballoon.spring

import io.mockk.clearMocks
import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestContextManager
import java.lang.reflect.Method

class SpringContext private constructor(
    private val testContextManager: TestContextManager,
    private val carrierInstance: SpringTestConfig,
    private val mocks: List<RegisteredMock>,
) {
    val applicationContext: ApplicationContext
        get() = testContextManager.testContext.applicationContext

    fun beforeTest() {
        testContextManager.beforeTestMethod(carrierInstance, CARRIER_METHOD)
        mocks.forEach { clearMocks(it.mock) }
    }

    fun afterTest() {
        testContextManager.afterTestMethod(carrierInstance, CARRIER_METHOD, null)
    }

    fun close() {
        testContextManager.afterTestClass()
    }

    companion object {
        private val CARRIER_METHOD: Method =
            SpringTestConfig::class.java.getDeclaredMethod("springTestBalloonCarrier")

        internal fun load(carrier: Class<out SpringTestConfig>, mocks: List<RegisteredMock>): SpringContext {
            val instance = carrier.getDeclaredConstructor().newInstance()
            MockBeanRegistry.push(mocks)
            try {
                val testContextManager = TestContextManager(carrier)
                testContextManager.beforeTestClass()
                testContextManager.prepareTestInstance(instance)
                return SpringContext(testContextManager, instance, mocks)
            } finally {
                MockBeanRegistry.pop()
            }
        }
    }
}
