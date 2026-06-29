package org.graphiks.kanvas.gpu.renderer.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities

class ProductFlagsM18Test {

    @Test
    fun `saveLayer defaults to enabled`() {
        val config = GPUProductFlagConfig()
        assertTrue(config.saveLayerEnabled)
    }

    @Test
    fun `dstRead defaults to enabled`() {
        val config = GPUProductFlagConfig()
        assertTrue(config.dstReadEnabled)
    }

    @Test
    fun `saveLayer capability fact is included when enabled`() {
        val config = GPUProductFlagConfig(saveLayerEnabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.savelayer.native" })
    }

    @Test
    fun `dstRead capability fact is included when enabled`() {
        val config = GPUProductFlagConfig(dstReadEnabled = true)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.any { it.name == "first_slice.dst_read.native" })
    }

    @Test
    fun `saveLayer capability fact is excluded when disabled`() {
        val config = GPUProductFlagConfig(saveLayerEnabled = false)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.none { it.name == "first_slice.savelayer.native" })
    }

    @Test
    fun `dstRead capability fact is excluded when disabled`() {
        val config = GPUProductFlagConfig(dstReadEnabled = false)
        val caps = config.buildCapabilities()
        assertTrue(caps.facts.none { it.name == "first_slice.dst_read.native" })
    }

    @Test
    fun `saveLayer property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.saveLayer", GPUProductFlagConfig.SaveLayerProperty)
    }

    @Test
    fun `dstRead property constant is defined`() {
        assertEquals("kanvas.gpu.renderer.product.dstRead", GPUProductFlagConfig.DstReadProperty)
    }
}
