package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductFlagsM23Test {

    @Test
    fun `performanceGates defaults to enabled`() {
        val config = GpuProductFlagConfig()
        assertTrue(config.performanceGatesEnabled)
    }

    @Test
    fun `performanceGates flag produces capability fact when enabled`() {
        val config = GpuProductFlagConfig()
        val capabilities = config.buildCapabilities()
        val factsByName = capabilities.facts.associateBy { it.name }
        assertTrue(factsByName.containsKey("first_slice.performance_gates.native"))
    }

    @Test
    fun `disabled performanceGates flag does not produce capability fact`() {
        val config = GpuProductFlagConfig(performanceGatesEnabled = false)
        val capabilities = config.buildCapabilities()
        val factNames = capabilities.facts.map { it.name }
        assertFalse("first_slice.performance_gates.native" in factNames)
    }

    @Test
    fun `disable property overrides performanceGates flag`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { key ->
                when (key) {
                    GpuProductFlagConfig.PerformanceGatesDisableProperty -> "true"
                    else -> null
                }
            },
        )
        assertFalse(config.performanceGatesEnabled)
    }

    @Test
    fun `empty property reader keeps performanceGates enabled`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { null },
        )
        assertTrue(config.performanceGatesEnabled)
    }

    @Test
    fun `performanceGates property constant is defined`() {
        assertEquals(
            "kanvas.gpu.renderer.product.performanceGates",
            GpuProductFlagConfig.PerformanceGatesProperty,
        )
    }

    @Test
    fun `performanceGates disable property constant is defined`() {
        assertEquals(
            "kanvas.gpu.renderer.product.performanceGates.disable",
            GpuProductFlagConfig.PerformanceGatesDisableProperty,
        )
    }
}
