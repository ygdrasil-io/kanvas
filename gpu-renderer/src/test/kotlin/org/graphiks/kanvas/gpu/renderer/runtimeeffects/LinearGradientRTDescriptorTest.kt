package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinearGradientRTDescriptorTest {

    @Test
    fun `LinearGradientRTDescriptor has expected effect ID`() {
        assertEquals(GPURuntimeEffectID("runtime.linear_gradient_rt"), LinearGradientRTDescriptor.effectId)
    }

    @Test
    fun `LinearGradientRTDescriptor has executable version 2`() {
        assertEquals(GPURuntimeEffectDescriptorVersion(2), LinearGradientRTDescriptor.descriptorVersion)
    }

    @Test
    fun `LinearGradientRTDescriptor uniform schema has gradient fields`() {
        assertEquals("schema:linear_gradient_rt:v2", LinearGradientRTDescriptor.uniformSchema.schemaHash)
        assertEquals(
            listOf(
                "start:vec4<f32>@0:16",
                "end:vec4<f32>@16:16",
                "startColor:vec4<f32>@32:16",
                "endColor:vec4<f32>@48:16",
            ),
            LinearGradientRTDescriptor.uniformSchema.fields,
        )
    }

    @Test
    fun `LinearGradientRTDescriptor block size is 64 bytes`() {
        assertEquals(64L, LinearGradientRTDescriptor.uniformBlockPlan.blockSizeBytes)
    }

    @Test
    fun `LinearGradientRTDescriptor uses its registered material source`() {
        assertEquals("linear_gradient_rt_source", LinearGradientRTDescriptor.wgslPlan.entryPoint)
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

    @Test
    fun `linear gradient CPU oracle interpolates the registered packed two stop payload`() {
        val bytes = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
            listOf(
                0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                1f, 0f, 0f, 1f,
                0f, 0f, 1f, 1f,
            ).forEach(::putFloat)
        }.array()

        val color = assertIs<GPURuntimeEffectMaterialEvaluationResult.Color>(
            LinearGradientRTCPUOracle.evaluateMaterial(
                GPURuntimeEffectMaterialEvaluationInput(bytes, 0.5f, 0.25f),
            ),
        )

        assertEquals(0.75f, color.r)
        assertEquals(0f, color.g)
        assertEquals(0.25f, color.b)
        assertEquals(1f, color.a)
    }
}
