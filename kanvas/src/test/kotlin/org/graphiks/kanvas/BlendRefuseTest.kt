package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Hermetic (no-GPU) coverage of the non-SRC_OVER blend refusal
 * (KGPU-M32-019, refused sub-case).
 *
 * Only `SrcOver` is dispatched; every other blend mode is REFUSED with
 * `unsupported_blend:<mode>` before fill dispatch, so it is never silently
 * composited. The GPU-gated end-to-end evidence is the existing bridge test
 * `non-srcover blend emits refuse diagnostic` (KanvasSkiaBridgeTest, assumeTrue
 * WebGPU); this asserts the same dispatch-guard headlessly.
 */
class BlendRefuseTest {
    private fun target(): GPUTargetFacts =
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

    private val solid = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f)

    private fun rectCmd(blend: GPUBlendFacts): NormalizedDrawCommand.FillRect =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rect = GPURect(0f, 0f, 10f, 10f),
            target = target(),
            material = solid,
            blend = blend,
        )

    @Test
    fun `multiply-blend fill command refuses with unsupported_blend`() {
        assertEquals(
            "unsupported_blend:multiply",
            rectCmd(GPUBlendFacts.unsupported("multiply")).fillGuardRefusalReasonOrNull(),
        )
    }

    @Test
    fun `srcover-blend fill command does not refuse`() {
        assertNull(rectCmd(GPUBlendFacts.srcOver()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `drawRect with multiply blend lowers to unsupported blend and refuses`() {
        val surface = Surface(width = 64, height = 64)
        Canvas(surface).drawRect(Rect(0f, 0f, 10f, 10f), Paint().color(1f, 0f, 0f, 1f).blendMode(BlendMode.MULTIPLY))
        val cmd = surface.recorder.recordedCommands().single() as NormalizedDrawCommand.FillRect
        assertEquals(GPUBlendKind.Unsupported, cmd.blend.kind)
        assertEquals("unsupported_blend:multiply", cmd.fillGuardRefusalReasonOrNull())
    }
}
