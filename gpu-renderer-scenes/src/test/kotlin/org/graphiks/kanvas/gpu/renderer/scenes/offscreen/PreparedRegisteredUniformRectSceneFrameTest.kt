package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.payloads.GPURegisteredUniformProgram
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class PreparedRegisteredUniformRectSceneFrameTest {
    @Test
    fun `linear gradient scene lowers clear and gradients into one generic prepared batch`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("linear-gradient-lanes")
        val recorded = assertIs<PreparedRegisteredUniformRectSceneFrameResult.Recorded>(
            PreparedRegisteredUniformRectSceneFrameRecorder().record(
                scene,
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 1L,
                withReadback = true,
            ),
        )

        assertEquals(4, recorded.semantics.size)
        assertEquals(GPURegisteredUniformProgram.SolidColor, recorded.semantics.first().program)
        assertTrue(recorded.semantics.drop(1).all {
            it.program == GPURegisteredUniformProgram.LinearGradient && it.uniformBytes.size == 64
        })
        assertTrue(recorded.diagnostics.contains("registeredUniform:programs=solid-color-v1,linear-gradient-2stop-v1"))
    }

    @Test
    fun `radial and sweep scenes reuse the same registered frame contract`() {
        val cases = listOf(
            "radial-swatch" to GPURegisteredUniformProgram.RadialGradient,
            "sweep-disk" to GPURegisteredUniformProgram.SweepGradient,
        )

        cases.forEachIndexed { index, (sceneId, program) ->
            val recorded = assertIs<PreparedRegisteredUniformRectSceneFrameResult.Recorded>(
                PreparedRegisteredUniformRectSceneFrameRecorder().record(
                    GPURendererSceneRegistry.registry.requireScene(sceneId),
                    capabilities(),
                    GPUDeviceGenerationID(9),
                    frameOrdinal = index + 2L,
                    withReadback = true,
                ),
            )

            assertEquals(4, recorded.semantics.size)
            assertEquals(GPURegisteredUniformProgram.SolidColor, recorded.semantics.first().program)
            assertTrue(recorded.semantics.drop(1).all { it.program == program })
        }
    }

    @Test
    fun `registered runtime effect uses the closed program instead of carrying source`() {
        val recorded = assertIs<PreparedRegisteredUniformRectSceneFrameResult.Recorded>(
            PreparedRegisteredUniformRectSceneFrameRecorder().record(
                GPURendererSceneRegistry.registry.requireScene("runtime-effect-uniform"),
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 4L,
                withReadback = true,
            ),
        )

        assertEquals(GPURegisteredUniformProgram.SolidColor, recorded.semantics.first().program)
        assertTrue(recorded.semantics.drop(1).all {
            it.program == GPURegisteredUniformProgram.SimpleRuntimeEffect &&
                it.payloadRef.resourceBlock == null
        })
        assertContains(
            recorded.diagnostics,
            "registeredUniform:wgslSourceInFramePlan=false",
        )
    }

    @Test
    fun `color matrix scene lowers row-major programs into the generic prepared batch`() {
        val recorded = assertIs<PreparedRegisteredUniformRectSceneFrameResult.Recorded>(
            PreparedRegisteredUniformRectSceneFrameRecorder().record(
                GPURendererSceneRegistry.registry.requireScene("color-matrix-filter"),
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 5L,
                withReadback = true,
            ),
        )

        assertEquals(4, recorded.semantics.size)
        assertEquals(GPURegisteredUniformProgram.SolidColor, recorded.semantics.first().program)
        assertTrue(recorded.semantics.drop(1).all {
            it.program == GPURegisteredUniformProgram.ColorMatrix && it.uniformBytes.size == 96
        })
        assertContains(
            recorded.diagnostics,
            "registeredUniform:programs=solid-color-v1,color-matrix-v1",
        )
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-registered-scene")),
        snapshotId = "capabilities-scene-registered-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
