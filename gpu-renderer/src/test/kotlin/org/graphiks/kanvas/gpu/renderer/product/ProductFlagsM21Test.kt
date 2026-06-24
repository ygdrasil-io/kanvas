package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductFlagsM21Test {

    @Test
    fun `default config has runtime effects enabled`() {
        val config = GpuProductFlagConfig()
        assertTrue(config.runtimeEffectsEnabled)
    }

    @Test
    fun `runtime effects flag produces capability fact when enabled`() {
        val config = GpuProductFlagConfig()
        val capabilities = config.buildCapabilities()
        val factsByName = capabilities.facts.associateBy { it.name }
        assertTrue(factsByName.containsKey("first_slice.runtime_effects.native"))
    }

    @Test
    fun `disabled runtime effects flag does not produce capability fact`() {
        val config = GpuProductFlagConfig(runtimeEffectsEnabled = false)
        val capabilities = config.buildCapabilities()
        val factNames = capabilities.facts.map { it.name }
        assertFalse("first_slice.runtime_effects.native" in factNames)
    }

    @Test
    fun `disable property overrides runtime effects flag`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { key ->
                when (key) {
                    GpuProductFlagConfig.RuntimeEffectsDisableProperty -> "true"
                    else -> null
                }
            },
        )
        assertFalse(config.runtimeEffectsEnabled)
    }

    @Test
    fun `empty property reader keeps runtime effects enabled`() {
        val config = GpuProductFlagConfig.fromSystemProperties(
            propertyReader = { null },
        )
        assertTrue(config.runtimeEffectsEnabled)
    }
}
