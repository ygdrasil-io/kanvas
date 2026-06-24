package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductFlagsM20Test {
    @Test
    fun `textA8 defaults to enabled`() {
        val config = GpuProductFlagConfig()
        assertTrue(config.textA8Enabled)
    }

    @Test
    fun `textSDF defaults to enabled`() {
        val config = GpuProductFlagConfig()
        assertTrue(config.textSDFEnabled)
    }

    @Test
    fun `textA8 capability fact is included when enabled`() {
        val config = GpuProductFlagConfig(textA8Enabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.text_a8_atlas.native" })
    }

    @Test
    fun `textSDF capability fact is included when enabled`() {
        val config = GpuProductFlagConfig(textSDFEnabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.text_sdf_atlas.native" })
    }

    @Test
    fun `textA8 capability fact is excluded when disabled`() {
        val config = GpuProductFlagConfig(textA8Enabled = false)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.none { it.name == "first_slice.text_a8_atlas.native" })
    }

    @Test
    fun `textSDF capability fact is excluded when disabled`() {
        val config = GpuProductFlagConfig(textSDFEnabled = false)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.none { it.name == "first_slice.text_sdf_atlas.native" })
    }

    @Test
    fun `textA8 property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.textA8", GpuProductFlagConfig.TextA8Property)
    }

    @Test
    fun `textSDF property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.textSDF", GpuProductFlagConfig.TextSDFProperty)
    }
}
