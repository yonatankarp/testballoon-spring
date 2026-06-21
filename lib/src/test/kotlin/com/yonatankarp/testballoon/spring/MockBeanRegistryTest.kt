package com.yonatankarp.testballoon.spring

import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MockBeanRegistryTest {
    @BeforeTest
    @AfterTest
    fun reset() = MockBeanRegistry.pop()

    @Test
    fun `current is empty when nothing has been pushed`() {
        assertTrue(MockBeanRegistry.current().isEmpty())
    }

    @Test
    fun `push then current returns the doubles, pop clears them`() {
        val doubles = listOf<RegisteredDouble>(RegisteredDouble.Mock(Any::class.java, mockk<Any>(), name = null))

        MockBeanRegistry.push(doubles)
        assertEquals(doubles, MockBeanRegistry.current())

        MockBeanRegistry.pop()
        assertTrue(MockBeanRegistry.current().isEmpty())
    }

    @Test
    fun `a nested push on the same thread is rejected`() {
        MockBeanRegistry.push(emptyList())

        assertFailsWith<IllegalStateException> { MockBeanRegistry.push(emptyList()) }
    }
}
