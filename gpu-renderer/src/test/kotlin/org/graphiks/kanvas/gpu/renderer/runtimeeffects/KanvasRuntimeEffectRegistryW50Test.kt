package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.nio.ByteBuffer
import java.nio.ByteOrder

class KanvasRuntimeEffectRegistryW50Test {
    private val registry = KanvasRuntimeEffectRegistry()

    @Test
    fun `snapshot exposes representative registered descriptors and immutable metadata`() {
        val snapshot = registry.snapshot()
        assertEquals("runtime-registry-v1", snapshot.registryVersion)
        assertEquals(1L, snapshot.generation)
        assertTrue(snapshot.descriptorSummary.contains("runtime.simple_rt@1"))
        assertTrue(snapshot.descriptorSummary.contains("runtime.spiral_rt@1"))
        assertTrue(snapshot.descriptorSummary.contains("runtime.intrinsics_matrix@1"))
        snapshot.descriptors.filter { it.id.value in setOf("runtime.simple_rt", "runtime.spiral_rt", "runtime.intrinsics_matrix") }
            .forEach {
                assertNotNull(it.kind)
                assertNotNull(it.cpuOracle)
                assertNotNull(it.wgslSource)
                assertTrue(it.uniformSchema.fields.isNotEmpty())
            }
    }

    @Test
    fun `lookup is pinned by id and version and unknown values refuse`() {
        val id = GPURuntimeEffectID("runtime.simple_rt")
        assertNotNull(registry.lookup(id))
        assertNotNull(registry.lookup(id, GPURuntimeEffectDescriptorVersion(1)))
        assertNull(registry.lookup(id, GPURuntimeEffectDescriptorVersion(99)))
        assertNull(registry.lookup(GPURuntimeEffectID("runtime.unknown")))
        assertEquals(GPURuntimeEffectKind.Material, registry.kind(id))
    }

    @Test
    fun `registered WGSL modules are parser validated while unknown routes cannot provide source`() {
        registry.snapshot().descriptors
            .filter { it.id.value in setOf("runtime.simple_rt", "runtime.spiral_rt", "runtime.intrinsics_matrix") }
            .forEach { descriptor ->
                assertTrue(registry.validateWgsl(descriptor.id).accepted)
            }
        assertEquals(false, registry.validateWgsl(GPURuntimeEffectID("runtime.unknown")).accepted)
    }

    @Test
    fun `representative descriptors expose concrete CPU material semantics`() {
        val color = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0.1f).putFloat(0.2f).putFloat(0.3f).putFloat(1f).array()
        assertTrue(SimpleRTCPUOracle.evaluateMaterial(GPURuntimeEffectMaterialEvaluationInput(color, 0f, 0f)) is GPURuntimeEffectMaterialEvaluationResult.Color)

        val spiral = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f)
            .putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f)
            .putFloat(0f).putFloat(1f).putFloat(0f).putFloat(1f)
            .putFloat(1f).putFloat(0f).putFloat(0f).putFloat(0f).array()
        assertTrue(SpiralRTCPUOracle.evaluateMaterial(GPURuntimeEffectMaterialEvaluationInput(spiral, 0.5f, 0.5f)) is GPURuntimeEffectMaterialEvaluationResult.Color)

        val matrix = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(2).putInt(0).putInt(0).putInt(0)
            .putFloat(1f).putFloat(0f).putFloat(0f).putFloat(0f)
            .putFloat(0f).putFloat(1f).putFloat(0f).putFloat(0f)
            .putFloat(0f).putFloat(0f).putFloat(1f).putFloat(0f)
            .putFloat(0f).putFloat(0f).putFloat(0f).putFloat(1f)
            .putFloat(2f).putFloat(3f).putFloat(4f).putFloat(1f).array()
        assertTrue(IntrinsicsMatrixCPUOracle.evaluateMaterial(GPURuntimeEffectMaterialEvaluationInput(matrix, 0f, 0f)) is GPURuntimeEffectMaterialEvaluationResult.Color)
    }
}
