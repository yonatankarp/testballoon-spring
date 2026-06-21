package com.yonatankarp.testballoon.spring

import io.mockk.spyk
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.test.context.ContextConfigurationAttributes
import org.springframework.test.context.ContextCustomizer
import org.springframework.test.context.ContextCustomizerFactory
import org.springframework.test.context.MergedContextConfiguration

/**
 * A test double declared in a [springTest] block, queued for registration into the
 * context while it is built. A [mock] is a free-standing mockk mock; a [spy] wraps the
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

    data class Spy(
        override val type: Class<*>,
        override val name: String?,
        // The spy is created lazily: spyk needs the real bean, which only exists once
        // the context's bean factory has instantiated it. Captured here on first read.
        private val holder: SpyHolder,
    ) : RegisteredDouble {
        override val instance: Any get() = holder.spy

        fun wrap(realBean: Any): Any = holder.wrap(realBean)

        override fun realizeIn(context: ApplicationContext) {
            if (name != null) context.getBean(name, type) else context.getBean(type)
        }
    }
}

/**
 * Mutable cell shared between the [SpringSuiteScope] (which hands the spy back to the
 * test) and the post-processor (which fills it once the real bean is built). Read only
 * after the context has loaded, by which point [wrap] has populated it.
 */
@PublishedApi
internal class SpyHolder {
    private lateinit var captured: Any

    val spy: Any get() = captured

    fun wrap(realBean: Any): Any = spyk(realBean, recordPrivateCalls = false).also { captured = it }
}

/**
 * Hands the doubles declared in a [springTest] block to the context customizer while the
 * carrier's context is being built.
 *
 * Confined to the loading thread: [SpringContext.load] pushes, triggers the synchronous
 * `TestContextManager` construction (where the factory reads), and pops, all on one
 * thread. Parallel suites load on separate threads, each with its own value.
 */
internal object MockBeanRegistry {
    private val current = ThreadLocal<List<RegisteredDouble>>()

    fun push(doubles: List<RegisteredDouble>) {
        check(current.get() == null) {
            "A double registry is already active on this thread; nested springTest loads are not supported."
        }
        current.set(doubles)
    }

    fun pop() = current.remove()

    fun current(): List<RegisteredDouble> = current.get() ?: emptyList()
}

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
        const val POST_PROCESSOR_NAME = "com.yonatankarp.testballoon.spring.TestDoubleBeanFactoryPostProcessor"
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
        // The real bean names of each mocked type, captured before any removal so that a
        // sole unnamed mock can re-use the replaced bean's name (keeping by-name injection
        // working) and so two mocks of one type don't evict each other.
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

            // Re-register the original definition under a hidden name so its instance is still
            // created the normal way (constructor injection, BPPs, etc.); clear its primary flag
            // so only the spy bean (under the original name) is a primary candidate of the type.
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
