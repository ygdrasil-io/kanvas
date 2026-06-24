package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpiralRTDescriptorTest {

    @Test
    fun `SpiralRTDescriptor has expected effect ID`() {
        assertEquals(GPURuntimeEffectID("runtime.spiral_rt"), SpiralRTDescriptor.effectId)
    }

    @Test
    fun `SpiralRTDescriptor has version 1`() {
        assertEquals(GPURuntimeEffectDescriptorVersion(1), SpiralRTDescriptor.descriptorVersion)
    }

    @Test
    fun `SpiralRTDescriptor uniform schema has center color1 color2 params fields`() {
        assertEquals("schema:spiral_rt:v1", SpiralRTDescriptor.uniformSchema.schemaHash)
        assertTrue(SpiralRTDescriptor.uniformSchema.fields.any { it.startsWith("center") })
        assertTrue(SpiralRTDescriptor.uniformSchema.fields.any { it.startsWith("color1") })
        assertTrue(SpiralRTDescriptor.uniformSchema.fields.any { it.startsWith("color2") })
        assertTrue(SpiralRTDescriptor.uniformSchema.fields.any { it.startsWith("params") })
    }

    @Test
    fun `SpiralRTDescriptor block size is 64 bytes`() {
        assertEquals(64L, SpiralRTDescriptor.uniformBlockPlan.blockSizeBytes)
    }

    @Test
    fun `SpiralRTDescriptor wgsl entry point is spiral_rt_source`() {
        assertEquals("spiral_rt_source", SpiralRTDescriptor.wgslPlan.entryPoint)
    }

    @Test
    fun `SpiralRTDescriptor createDescriptor produces valid descriptor`() {
        val descriptor = SpiralRTDescriptor.createDescriptor()
        assertEquals(SpiralRTDescriptor.effectId, descriptor.id)
        assertEquals(SpiralRTDescriptor.uniformSchema, descriptor.uniformSchema)
        assertTrue(descriptor.childSlots.isEmpty())
    }

    @Test
    fun `SpiralRTDescriptor route contract accepts MaterialSource`() {
        assertTrue(SpiralRTDescriptor.routeContract.nativeSupported)
        assertFalse(SpiralRTDescriptor.routeContract.cpuOracleOnly)
        assertTrue(GPURuntimeEffectRoutePlacement.MaterialSource in SpiralRTDescriptor.routeContract.acceptedPlacements)
    }
}
