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
 * test) and [CompositeContextCustomizer] (which fills it once the real bean is built).
 */
@PublishedApi
internal class SpyHolder {
    private var captured: Any? = null

    val spy: Any get() = checkNotNull(captured) { "Spy bean has not been created yet; load the context first." }

    fun wrap(realBean: Any): Any = spyk(realBean, recordPrivateCalls = false).also { captured = it }
}

/**
 * Hands the doubles declared in a [springTest] block to the context customizers while
 * the carrier's context is being built.
 *
 * Confined to the loading thread: [SpringContext.load] pushes, triggers the
 * synchronous `TestContextManager` construction (where the factories read), and pops,
 * all on one thread. Parallel suites load on separate threads, each with its own value.
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
        val mocks = doubles.filterIsInstance<RegisteredDouble.Mock>()
        val spies = doubles.filterIsInstance<RegisteredDouble.Spy>()
        return when {
            mocks.isEmpty() && spies.isEmpty() -> null
            else -> CompositeContextCustomizer(mocks, spies)
        }
    }
}

/**
 * Single customizer covering both kinds of double, so their bean-name and equality
 * contributions stay together (Spring keys context caching on customizer equality).
 */
internal class CompositeContextCustomizer(
    private val mocks: List<RegisteredDouble.Mock>,
    private val spies: List<RegisteredDouble.Spy>,
) : ContextCustomizer {
    override fun customizeContext(context: ConfigurableApplicationContext, mergedConfig: MergedContextConfiguration) {
        val beanFactory: ConfigurableListableBeanFactory = context.beanFactory
        val registry = beanFactory as BeanDefinitionRegistry
        if (beanFactory is DefaultListableBeanFactory) {
            beanFactory.isAllowBeanDefinitionOverriding = true
        }

        registerMocks(beanFactory, registry)
        // Spies are wired by a BeanFactoryPostProcessor rather than here: at customize time
        // the config classes have not been parsed yet, so a @Bean-method target bean has no
        // definition to wrap. The post-processor runs after ConfigurationClassPostProcessor,
        // when every target definition exists.
        if (spies.isNotEmpty()) {
            registry.registerBeanDefinition(
                SPY_POST_PROCESSOR_NAME,
                RootBeanDefinition(SpyBeanFactoryPostProcessor::class.java).apply {
                    setInstanceSupplier { SpyBeanFactoryPostProcessor(spies) }
                },
            )
        }
    }

    private fun registerMocks(beanFactory: ConfigurableListableBeanFactory, registry: BeanDefinitionRegistry) {
        // Remove pre-existing real definitions once per mocked type, before registering any mock —
        // otherwise a second mock of the same type would evict the first one we just added.
        mocks.map { it.type }.distinct().forEach { type ->
            beanFactory.getBeanNamesForType(type, true, false).forEach { name ->
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name)
            }
        }

        val mockCountByType = mocks.groupingBy { it.type }.eachCount()
        mocks.forEach { mock ->
            val definition = RootBeanDefinition(mock.type).apply {
                setInstanceSupplier { mock.instance }
                // Only the sole mock of a type can be primary; multiple are resolved by name.
                isPrimary = mockCountByType[mock.type] == 1
            }
            registry.registerBeanDefinition(mock.beanName(), definition)
        }
    }

    // Fully-qualified to avoid collisions between same-simple-name types from different packages.
    private fun RegisteredDouble.Mock.beanName(): String = name ?: "${type.name}#MockBean"

    private companion object {
        const val SPY_POST_PROCESSOR_NAME = "com.yonatankarp.testballoon.spring.SpyBeanFactoryPostProcessor"
    }
}

/**
 * Replaces each spy's target bean definition with a definition that wraps the real bean in
 * a mockk spy. Runs as a [BeanFactoryPostProcessor] (lowest precedence) so config classes
 * are already parsed and every target definition exists to be located and wrapped.
 */
internal class SpyBeanFactoryPostProcessor(private val spies: List<RegisteredDouble.Spy>) :
    BeanFactoryPostProcessor,
    Ordered {
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        val registry = beanFactory as BeanDefinitionRegistry
        spies.forEach { spy ->
            val targetName = resolveTargetName(beanFactory, spy)
            val targetDefinition = registry.getBeanDefinition(targetName)
            val targetWasPrimary = targetDefinition.isPrimary
            registry.removeBeanDefinition(targetName)

            // Re-register the original definition under a hidden name so its instance is
            // still created the normal way (constructor injection, BPPs, etc.); the spy
            // wraps that real instance lazily, the first time the spy bean is requested.
            // Clear primary on the hidden target so only the spy bean (under the original
            // name) is a primary candidate — otherwise spying a @Primary bean yields two.
            targetDefinition.isPrimary = false
            val realName = "$targetName#SpyTarget"
            registry.registerBeanDefinition(realName, targetDefinition)

            val spyDefinition = RootBeanDefinition(spy.type).apply {
                setInstanceSupplier { spy.wrap(beanFactory.getBean(realName)) }
                isPrimary = targetWasPrimary || spy.name == null
            }
            registry.registerBeanDefinition(targetName, spyDefinition)
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
