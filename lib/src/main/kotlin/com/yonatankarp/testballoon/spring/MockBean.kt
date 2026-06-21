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
internal data class RegisteredMock(val type: Class<*>, val mock: Any, val name: String?)

/**
 * Hands the mocks declared in a [springTest] block to [MockBeanContextCustomizer]
 * while the carrier's context is being built.
 *
 * Confined to the loading thread: [SpringContext.load] pushes, triggers the
 * synchronous `TestContextManager` construction (where the factory reads), and pops,
 * all on one thread. Parallel suites load on separate threads, each with its own value.
 */
internal object MockBeanRegistry {
    private val current = ThreadLocal<List<RegisteredMock>>()

    fun push(mocks: List<RegisteredMock>) {
        check(current.get() == null) {
            "A mockBean registry is already active on this thread; nested springTest loads are not supported."
        }
        current.set(mocks)
    }

    fun pop() = current.remove()

    fun current(): List<RegisteredMock> = current.get() ?: emptyList()
}

public class MockBeanContextCustomizerFactory : ContextCustomizerFactory {
    override fun createContextCustomizer(
        testClass: Class<*>,
        configAttributes: List<ContextConfigurationAttributes>,
    ): ContextCustomizer? {
        val mocks = MockBeanRegistry.current()
        return if (mocks.isEmpty()) null else MockBeanContextCustomizer(mocks)
    }
}

internal class MockBeanContextCustomizer(private val mocks: List<RegisteredMock>) : ContextCustomizer {
    override fun customizeContext(context: ConfigurableApplicationContext, mergedConfig: MergedContextConfiguration) {
        val beanFactory: ConfigurableListableBeanFactory = context.beanFactory
        val registry = beanFactory as BeanDefinitionRegistry
        if (beanFactory is DefaultListableBeanFactory) {
            beanFactory.isAllowBeanDefinitionOverriding = true
        }

        // Remove pre-existing real definitions once per mocked type, before registering any mock —
        // otherwise a second mock of the same type would evict the first one we just added.
        mocks.map { it.type }.distinct().forEach { type ->
            beanFactory.getBeanNamesForType(type, true, false).forEach { name ->
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name)
            }
        }

        val mockCountByType = mocks.groupingBy { it.type }.eachCount()
        mocks.forEach { registered ->
            val definition = RootBeanDefinition(registered.type).apply {
                setInstanceSupplier { registered.mock }
                // Only the sole mock of a type can be primary; multiple are resolved by name.
                isPrimary = mockCountByType[registered.type] == 1
            }
            registry.registerBeanDefinition(registered.beanName(), definition)
        }
    }

    // Fully-qualified to avoid collisions between same-simple-name types from different packages.
    private fun RegisteredMock.beanName(): String = name ?: "${type.name}#MockBean"
}
