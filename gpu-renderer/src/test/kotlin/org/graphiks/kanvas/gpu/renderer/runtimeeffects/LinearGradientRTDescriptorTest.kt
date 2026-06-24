package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinearGradientRTDescriptorTest {

    @Test
    fun `LinearGradientRTDescriptor has expected effect ID`() {
        assertEquals(GPURuntimeEffectID("runtime.linear_gradient_rt"), LinearGradientRTDescriptor.effectId)
    }

    @Test
    fun `LinearGradientRTDescriptor has version 1`() {
        assertEquals(GPURuntimeEffectDescriptorVersion(1), LinearGradientRTDescriptor.descriptorVersion)
    }

    @Test
    fun `LinearGradientRTDescriptor uniform schema has gradient fields`() {
        assertEquals("schema:linear_gradient_rt:v1", LinearGradientRTDescriptor.uniformSchema.schemaHash)
        assertTrue(LinearGradientRTDescriptor.uniformSchema.fields.any { it.startsWith("startEnd") })
        assertTrue(LinearGradientRTDescriptor.uniformSchema.fields.any { it.startsWith("colors") })
        assertTrue(LinearGradientRTDescriptor.uniformSchema.fields.any { it.startsWith("count") })
    }

    @Test
    fun `LinearGradientRTDescriptor block size is 64 bytes`() {
        assertEquals(64L, LinearGradientRTDescriptor.uniformBlockPlan.blockSizeBytes)
    }

    @Test
    fun `LinearGradientRTDescriptor wgsl entry point reuses LinearGradientSnippet`() {
        assertEquals("linear_gradient_clamp", LinearGradientRTDescriptor.wgslPlan.entryPoint)
    }

    @Test
    fun `LinearGradientRTDescriptor createDescriptor produces valid descriptor`() {
        val descriptor = LinearGradientRTDescriptor.createDescriptor()
        assertEquals(LinearGradientRTDescriptor.effectId, descriptor.id)
        assertEquals(LinearGradientRTDescriptor.uniformSchema, descriptor.uniformSchema)
        assertTrue(descriptor.childSlots.isEmpty())
    }

    @Test
    fun `LinearGradientRTDescriptor route contract accepts MaterialSource`() {
        assertTrue(LinearGradientRTDescriptor.routeContract.nativeSupported)
        assertFalse(LinearGradientRTDescriptor.routeContract.cpuOracleOnly)
        assertTrue(GPURuntimeEffectRoutePlacement.MaterialSource in LinearGradientRTDescriptor.routeContract.acceptedPlacements)
    }
}
