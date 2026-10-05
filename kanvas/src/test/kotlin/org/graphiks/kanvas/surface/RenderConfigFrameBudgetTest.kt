package org.graphiks.kanvas.surface

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RenderConfigFrameBudgetTest {
    @Test
    fun `default frame local budget is one gibibyte`() {
        assertEquals(1L shl 30, RenderConfig.DEFAULT.frameLocalBudgetBytes)
    }

    @Test
    fun `environment property supplies a positive frame local budget`() {
        val name = "kanvas.render.frameLocalBudgetBytes"
        val previous = System.getProperty(name)
        try {
            System.setProperty(name, "4096")
            assertEquals(4096L, RenderConfig.fromEnvironment().frameLocalBudgetBytes)
        } finally {
            if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
        }
    }

    @Test
    fun `unparseable environment budget keeps the default`() {
        val name = "kanvas.render.frameLocalBudgetBytes"
        val previous = System.getProperty(name)
        try {
            System.setProperty(name, "not-a-number")
            assertEquals(RenderConfig.DEFAULT.frameLocalBudgetBytes, RenderConfig.fromEnvironment().frameLocalBudgetBytes)
        } finally {
            if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
        }
    }

    @Test
    fun `frame local budget must be positive`() {
        assertFailsWith<IllegalArgumentException> { RenderConfig(frameLocalBudgetBytes = 0) }
        assertFailsWith<IllegalArgumentException> { RenderConfig(frameLocalBudgetBytes = -1) }
    }
}
