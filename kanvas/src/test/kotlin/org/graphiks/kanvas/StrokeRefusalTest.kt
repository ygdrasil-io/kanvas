package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hermetic (no-GPU) coverage of the stroke-style refusal mechanism
 * (KGPU-M32-013 + the stroke sub-case of KGPU-M32-015).
 *
 * A stroke-style draw must be marked for refusal on its normalized command so
 * it is REFUSED with `unsupported_stroke` instead of being silently filled.
 * The real GPU pixel + emitted-diagnostic evidence is produced by the
 * GPU-gated bridge tests (assumeTrue WebGPU), kept separate from these
 * headless tests.
 */
class StrokeRefusalTest {
    private fun target(): GPUTargetFacts =
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

    private fun firstCommand(block: Canvas.() -> Unit): NormalizedDrawCommand {
        val surface = Surface(width = 64, height = 64)
        Canvas(surface).block()
        return surface.recorder.recordedCommands().single()
    }

    @Test
    fun `drawRect with stroke paint records a stroke-marked fill command`() {
        val paint = Paint().color(1f, 0f, 0f, 1f).also { it.style = PaintStyle.STROKE }
        val cmd = firstCommand { drawRect(Rect(0f, 0f, 10f, 10f), paint) }
        val rect = cmd as NormalizedDrawCommand.FillRect
        assertTrue(rect.stroke, "stroke-style drawRect must mark the command stroke=true")
    }

    @Test
    fun `drawRect with default fill paint records a non-stroke command`() {
        val cmd = firstCommand { drawRect(Rect(0f, 0f, 10f, 10f), Paint().color(1f, 0f, 0f, 1f)) }
        val rect = cmd as NormalizedDrawCommand.FillRect
        assertFalse(rect.stroke, "fill-style drawRect must keep stroke=false")
    }

    @Test
    fun `drawRRect with stroke paint records a stroke-marked fill command`() {
        val paint = Paint().color(0f, 1f, 0f, 1f).also { it.style = PaintStyle.STROKE }
        val cmd = firstCommand {
            drawRRect(RRect(Rect(0f, 0f, 20f, 20f), RRectCornerRadii(4f, 4f)), paint)
        }
        val rrect = cmd as NormalizedDrawCommand.FillRRect
        assertTrue(rrect.stroke, "stroke-style drawRRect must mark the command stroke=true")
    }

    @Test
    fun `drawRRect with default fill paint records a non-stroke command`() {
        val cmd = firstCommand {
            drawRRect(RRect(Rect(0f, 0f, 20f, 20f), RRectCornerRadii(4f, 4f)), Paint())
        }
        val rrect = cmd as NormalizedDrawCommand.FillRRect
        assertFalse(rrect.stroke, "fill-style drawRRect must keep stroke=false")
    }

    @Test
    fun `drawPath with stroke paint records a stroke-marked fill command`() {
        val paint = Paint().color(0f, 0f, 1f, 1f).also { it.style = PaintStyle.STROKE }
        val cmd = firstCommand {
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(10f, 0f); lineTo(5f, 10f); close()
            }
            drawPath(path, paint)
        }
        val fillPath = cmd as NormalizedDrawCommand.FillPath
        assertTrue(fillPath.stroke, "stroke-style drawPath must mark the command stroke=true")
    }

    @Test
    fun `drawPath with default fill paint records a non-stroke command`() {
        val cmd = firstCommand {
            val path = Path().apply {
                moveTo(0f, 0f); lineTo(10f, 0f); lineTo(5f, 10f); close()
            }
            drawPath(path, Paint())
        }
        val fillPath = cmd as NormalizedDrawCommand.FillPath
        assertFalse(fillPath.stroke, "fill-style drawPath must keep stroke=false")
    }

    @Test
    fun `stroke-marked rect command refuses with unsupported_stroke`() {
        val cmd = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rect = GPURect(0f, 0f, 10f, 10f),
            target = target(),
            material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
            stroke = true,
        )
        assertEquals("unsupported_stroke", cmd.strokeRefusalReasonOrNull())
    }

    @Test
    fun `stroke-marked rrect command refuses with unsupported_stroke`() {
        val cmd = GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rrect = GPURRect(GPURect(0f, 0f, 20f, 20f), radiusX = 4f, radiusY = 4f),
            target = target(),
            material = GPUMaterialDescriptor.SolidColor(0f, 1f, 0f, 1f),
            stroke = true,
        )
        assertEquals("unsupported_stroke", cmd.strokeRefusalReasonOrNull())
    }

    @Test
    fun `stroke-marked path command refuses with unsupported_stroke`() {
        val cmd = GPUFillPathCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            pathKey = "k",
            pathDescriptor = GPUPathFacts(
                pathKey = "k", verbCount = 0, pointCount = 0, fillRule = "winding",
                inverseFill = false, finiteProof = "finite", volatility = "stable",
                transformClass = "identity", edgeCount = 0,
            ),
            tessellatedVertices = listOf(0f, 0f, 10f, 0f, 5f, 10f),
            contourStarts = listOf(0),
            edgeCount = 3,
            target = target(),
            material = GPUMaterialDescriptor.SolidColor(0f, 0f, 1f, 1f),
            stroke = true,
        )
        assertEquals("unsupported_stroke", cmd.strokeRefusalReasonOrNull())
    }

    @Test
    fun `fill-marked command does not refuse for stroke`() {
        val cmd = GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rect = GPURect(0f, 0f, 10f, 10f),
            target = target(),
            material = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f),
        )
        assertNull(cmd.strokeRefusalReasonOrNull(), "fill command must not produce a stroke refusal")
    }
}
