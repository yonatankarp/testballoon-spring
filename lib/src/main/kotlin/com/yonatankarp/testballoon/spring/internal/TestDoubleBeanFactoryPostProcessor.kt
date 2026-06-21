package com.yonatankarp.testballoon.spring.internal

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.test.context.ContextConfigurationAttributes
import org.springframework.test.context.ContextCustomizer
import org.springframework.test.context.ContextCustomizerFactory
import org.springframework.test.context.MergedContextConfiguration

public class MockBeanContextCustomizerFactory : ContextCustomizerFactory {
    override fun createContextCustomizer(
        testClass: Class<*>,
        configAttributes: List<ContextConfigurationAttributes>,
    ): ContextCustomizer? {
        val doubles = MockBeanRegistry.current()
        return if (doubles.isEmpty()) null else TestDoubleContextCustomizer(doubles)
    }
}

/**
 * Registers a [TestDoubleBeanFactoryPostProcessor] for the suite's doubles. Equality is
 * left as identity so each suite keys a distinct context (it owns its own double instances).
 */
internal class TestDoubleContextCustomizer(private val doubles: List<RegisteredDouble>) : ContextCustomizer {
    override fun customizeContext(context: ConfigurableApplicationContext, mergedConfig: MergedContextConfiguration) {
        val beanFactory = context.beanFactory as DefaultListableBeanFactory
        beanFactory.isAllowBeanDefinitionOverriding = true
        beanFactory.registerBeanDefinition(
            POST_PROCESSOR_NAME,
            RootBeanDefinition(TestDoubleBeanFactoryPostProcessor::class.java).apply {
                setInstanceSupplier { TestDoubleBeanFactoryPostProcessor(doubles) }
            },
        )
    }

    private companion object {
        const val POST_PROCESSOR_NAME =
            "com.yonatankarp.testballoon.spring.internal.TestDoubleBeanFactoryPostProcessor"
    }
}

/**
 * Installs the test doubles into the bean factory. Runs as a lowest-precedence
 * [BeanFactoryPostProcessor] so it executes after `ConfigurationClassPostProcessor`, when
 * every real bean definition (including `@Bean` methods) exists to be replaced or wrapped.
 */
internal class TestDoubleBeanFactoryPostProcessor(doubles: List<RegisteredDouble>) :
    BeanFactoryPostProcessor,
    Ordered {
    private val mocks = doubles.filterIsInstance<RegisteredDouble.Mock>()
    private val spies = doubles.filterIsInstance<RegisteredDouble.Spy>()

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val registry = beanFactory as BeanDefinitionRegistry
        registerMocks(beanFactory, registry)
        registerSpies(beanFactory, registry)
    }

    private fun registerMocks(beanFactory: ConfigurableListableBeanFactory, registry: BeanDefinitionRegistry) {
        val countByType = mocks.groupingBy { it.type }.eachCount()
        val realNamesByType = mocks.map { it.type }.distinct().associateWith { type ->
            beanFactory.getBeanNamesForType(type, true, false).toList()
        }
        realNamesByType.values.flatten().toSet().forEach { name ->
            if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name)
        }
        mocks.forEach { mock ->
            val sole = countByType.getValue(mock.type) == 1
            val beanName = mock.name
                ?: realNamesByType.getValue(mock.type).singleOrNull()?.takeIf { sole }
                ?: "${mock.type.name}#MockBean"
            registry.registerBeanDefinition(
                beanName,
                RootBeanDefinition(mock.type).apply {
                    setInstanceSupplier { mock.instance }
                    isPrimary = sole
                },
            )
        }
    }

    private fun registerSpies(beanFactory: ConfigurableListableBeanFactory, registry: BeanDefinitionRegistry) {
        spies.forEach { spy ->
            val targetName = resolveTargetName(beanFactory, spy)
            val targetDefinition = registry.getBeanDefinition(targetName)
            val targetWasPrimary = targetDefinition.isPrimary
            registry.removeBeanDefinition(targetName)

            targetDefinition.isPrimary = false
            val realName = "$targetName#SpyTarget"
            registry.registerBeanDefinition(realName, targetDefinition)

            registry.registerBeanDefinition(
                targetName,
                RootBeanDefinition(spy.type).apply {
                    setInstanceSupplier { spy.wrap(beanFactory.getBean(realName)) }
                    isPrimary = targetWasPrimary || spy.name == null
                },
            )
        }
    }

    private fun resolveTargetName(beanFactory: ConfigurableListableBeanFactory, spy: RegisteredDouble.Spy): String {
        if (spy.name != null) return spy.name
        val candidates = beanFactory.getBeanNamesForType(spy.type, true, false)
        return when (candidates.size) {
            1 -> candidates.single()
            0 -> error("spyBean<${spy.type.name}> found no bean of that type to wrap.")
            else -> error(
                "spyBean<${spy.type.name}> found ${candidates.size} beans of that type " +
                    "(${candidates.joinToString()}); pass a name to disambiguate.",
            )
        }
    }
}
