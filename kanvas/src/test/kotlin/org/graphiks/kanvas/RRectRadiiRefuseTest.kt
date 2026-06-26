package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Hermetic (no-GPU) coverage of the rounded-rect non-uniform radii refusal
 * (KGPU-M32-012, refused sub-case).
 *
 * The uniform-rrect route fills with a single corner radius; non-uniform radii
 * are REFUSED with `non_uniform_radii` instead of being approximated. The check
 * runs after the shared fill guard (material/blend/clip) inside the rrect
 * dispatcher, so this asserts the dedicated geometry guard directly.
 */
class RRectRadiiRefuseTest {
    private fun target(): GPUTargetFacts =
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

    private val solid = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 1f)

    private fun rrectCmd(rrect: GPURRect): NormalizedDrawCommand.FillRRect =
        GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rrect = rrect,
            target = target(),
            material = solid,
        )

    @Test
    fun `non-uniform rrect command refuses with non_uniform_radii`() {
        val rrect = GPURRect(
            rect = GPURect(0f, 0f, 20f, 20f),
            topLeft = GPURRectCornerRadii(4f, 4f),
            topRight = GPURRectCornerRadii(8f, 8f),
            bottomRight = GPURRectCornerRadii(4f, 4f),
            bottomLeft = GPURRectCornerRadii(4f, 4f),
        )
        assertEquals("non_uniform_radii", rrectCmd(rrect).nonUniformRadiiRefusalReasonOrNull())
    }

    @Test
    fun `uniform rrect command does not refuse for radii`() {
        val rrect = GPURRect(GPURect(0f, 0f, 20f, 20f), radiusX = 4f, radiusY = 4f)
        assertNull(rrectCmd(rrect).nonUniformRadiiRefusalReasonOrNull())
    }

    @Test
    fun `drawRRect with non-uniform radii preserves non-uniform radii so dispatch refuses`() {
        val surface = Surface(width = 64, height = 64)
        Canvas(surface).drawRRect(
            RRect(
                rect = Rect(0f, 0f, 20f, 20f),
                topLeft = RRectCornerRadii(4f, 4f),
                topRight = RRectCornerRadii(8f, 8f),
                bottomRight = RRectCornerRadii(4f, 4f),
                bottomLeft = RRectCornerRadii(4f, 4f),
            ),
            Paint().color(0f, 1f, 0f, 1f),
        )
        val cmd = surface.recorder.recordedCommands().single() as NormalizedDrawCommand.FillRRect
        assertEquals("non_uniform_radii", cmd.nonUniformRadiiRefusalReasonOrNull())
    }
}
