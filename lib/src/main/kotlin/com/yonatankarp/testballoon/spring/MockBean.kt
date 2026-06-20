package com.yonatankarp.testballoon.spring

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfigurationAttributes
import org.springframework.test.context.ContextCustomizer
import org.springframework.test.context.ContextCustomizerFactory
import org.springframework.test.context.MergedContextConfiguration

@PublishedApi
internal class RegisteredMock @PublishedApi internal constructor(
    val type: Class<*>,
    val mock: Any,
)

internal object MockBeanRegistry {
    private val current = ThreadLocal<List<RegisteredMock>>()

    fun push(mocks: List<RegisteredMock>) = current.set(mocks)

    fun pop() = current.remove()

    fun current(): List<RegisteredMock> = current.get() ?: emptyList()
}

class MockBeanContextCustomizerFactory : ContextCustomizerFactory {
    override fun createContextCustomizer(
        testClass: Class<*>,
        configAttributes: List<ContextConfigurationAttributes>,
    ): ContextCustomizer? {
        val mocks = MockBeanRegistry.current()
        return if (mocks.isEmpty()) null else MockBeanContextCustomizer(mocks)
    }
}

internal class MockBeanContextCustomizer(
    private val mocks: List<RegisteredMock>,
) : ContextCustomizer {
    override fun customizeContext(
        context: ConfigurableApplicationContext,
        mergedConfig: MergedContextConfiguration,
    ) {
        val beanFactory: ConfigurableListableBeanFactory = context.beanFactory
        val registry = beanFactory as BeanDefinitionRegistry
        if (beanFactory is DefaultListableBeanFactory) {
            beanFactory.isAllowBeanDefinitionOverriding = true
        }
        mocks.forEach { registered ->
            beanFactory.getBeanNamesForType(registered.type, true, false).forEach { name ->
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name)
            }
            val definition = RootBeanDefinition(registered.type)
            definition.setInstanceSupplier { registered.mock }
            definition.isPrimary = true
            registry.registerBeanDefinition("${registered.type.simpleName}#MockBean", definition)
        }
    }
}
