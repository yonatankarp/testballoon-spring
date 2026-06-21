package com.yonatankarp.testballoon.spring.internal

/**
 * Hands the doubles declared in a `springTest` block to the context customizer while the
 * carrier's context is being built.
 *
 * Confined to the loading thread: `SpringContext.load` pushes, triggers the synchronous
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
