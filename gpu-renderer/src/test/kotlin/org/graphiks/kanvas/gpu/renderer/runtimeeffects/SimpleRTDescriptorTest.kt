package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleRTDescriptorTest {

    @Test
    fun `SimpleRTDescriptor has expected effect ID`() {
        assertEquals(GPURuntimeEffectID("runtime.simple_rt"), SimpleRTDescriptor.effectId)
    }

    @Test
    fun `SimpleRTDescriptor has version 1`() {
        assertEquals(GPURuntimeEffectDescriptorVersion(1), SimpleRTDescriptor.descriptorVersion)
    }

    @Test
    fun `SimpleRTDescriptor uniform schema has gColor field`() {
        assertEquals("schema:simple_rt:v1", SimpleRTDescriptor.uniformSchema.schemaHash)
        assertTrue(SimpleRTDescriptor.uniformSchema.fields.any { it.startsWith("gColor") })
        assertEquals("std140", SimpleRTDescriptor.uniformSchema.packingPolicy)
    }

    @Test
    fun `SimpleRTDescriptor block size is 16 bytes`() {
        assertEquals(16L, SimpleRTDescriptor.uniformBlockPlan.blockSizeBytes)
    }

    @Test
    fun `SimpleRTDescriptor wgsl entry point is simple_rt_source`() {
        assertEquals("simple_rt_source", SimpleRTDescriptor.wgslPlan.entryPoint)
    }

    @Test
    fun `SimpleRTDescriptor createDescriptor produces valid descriptor`() {
        val descriptor = SimpleRTDescriptor.createDescriptor()
        assertEquals(SimpleRTDescriptor.effectId, descriptor.id)
        assertEquals(SimpleRTDescriptor.descriptorVersion, descriptor.version)
        assertEquals(SimpleRTDescriptor.uniformSchema, descriptor.uniformSchema)
        assertEquals(SimpleRTDescriptor.uniformBlockPlan, descriptor.uniformBlockPlan)
        assertEquals(SimpleRTDescriptor.resources, descriptor.resources)
        assertEquals(SimpleRTDescriptor.wgslPlan, descriptor.wgslPlan)
        assertEquals(SimpleRTDescriptor.routeContract, descriptor.routeContract)
        assertEquals(SimpleRTDescriptor.liveEditPlan, descriptor.liveEditPlan)
        assertTrue(descriptor.childSlots.isEmpty())
    }

    @Test
    fun `SimpleRTDescriptor route contract accepts MaterialSource placement`() {
        assertTrue(SimpleRTDescriptor.routeContract.nativeSupported)
        assertFalse(SimpleRTDescriptor.routeContract.cpuOracleOnly)
        assertTrue(GPURuntimeEffectRoutePlacement.MaterialSource in SimpleRTDescriptor.routeContract.acceptedPlacements)
    }
}
