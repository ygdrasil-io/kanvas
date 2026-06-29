package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities

class ProductFlagsM19Test {

    @Test
    fun `blurFilter defaults to enabled`() {
        val config = GPUProductFlagConfig()
        assertTrue(config.blurFilterEnabled)
    }

    @Test
    fun `colorMatrixFilter defaults to enabled`() {
        val config = GPUProductFlagConfig()
        assertTrue(config.colorMatrixFilterEnabled)
    }

    @Test
    fun `blurFilter capability fact is included when enabled`() {
        val config = GPUProductFlagConfig(blurFilterEnabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.blur_filter.native" })
    }

    @Test
    fun `colorMatrixFilter capability fact is included when enabled`() {
        val config = GPUProductFlagConfig(colorMatrixFilterEnabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.color_matrix_filter.native" })
    }

    @Test
    fun `blurFilter property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.blurFilter", GPUProductFlagConfig.BlurFilterProperty)
    }

    @Test
    fun `colorMatrixFilter property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.colorMatrixFilter", GPUProductFlagConfig.ColorMatrixFilterProperty)
    }

    @Test
    fun `blurFilter disable property is defined`() {
        assertEquals("kanvas.gpu.renderer.product.blurFilter.disable", GPUProductFlagConfig.BlurFilterDisableProperty)
    }

    @Test
    fun `colorMatrixFilter disable property is defined`() {
        assertEquals(
            "kanvas.gpu.renderer.product.colorMatrixFilter.disable",
            GPUProductFlagConfig.ColorMatrixFilterDisableProperty,
        )
    }
}
