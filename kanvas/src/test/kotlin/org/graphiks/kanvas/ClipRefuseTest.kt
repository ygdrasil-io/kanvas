package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Hermetic (no-GPU) coverage of the complex-clip refusal
 * (KGPU-M32-014 fill clips + KGPU-M32-018 text clip refused sub-cases).
 *
 * Only `WideOpen` and `DeviceRect` clips are dispatched; any other clip kind
 * (e.g. `ComplexStack`) is REFUSED with `unsupported_clip:<kind>`.
 *
 * HONESTY / reachability note: the public Kanvas/bridge API CANNOT construct a
 * non-WideOpen clip — `Canvas.drawRect/drawRRect/drawPath` never pass a clip
 * (the command builder defaults to `GPUClipFacts.wideOpen`), and the
 * `KanvasSkiaBridge` exposes no clip entrypoint. The complex-clip refusal is
 * therefore proven as a DISPATCH-LEVEL guard by constructing the command
 * directly; it is NOT a reachable end-to-end refuse via the public API today.
 * Real device-scissor clip work is dependency-gated (KGPU-M32-014 port sub-case).
 */
class ClipRefuseTest {
    private fun target(): GPUTargetFacts =
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

    private val solid = GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f)
    private fun complexClip(): GPUClipFacts = GPUClipFacts.complexStack(GPUBounds(0f, 0f, 10f, 10f))

    private fun rectCmd(clip: GPUClipFacts): NormalizedDrawCommand.FillRect =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rect = GPURect(0f, 0f, 10f, 10f),
            target = target(),
            material = solid,
            clip = clip,
        )

    private fun rrectCmd(clip: GPUClipFacts): NormalizedDrawCommand.FillRRect =
        GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rrect = GPURRect(GPURect(0f, 0f, 20f, 20f), radiusX = 4f, radiusY = 4f),
            target = target(),
            material = solid,
            clip = clip,
        )

    private fun pathCmd(clip: GPUClipFacts): NormalizedDrawCommand.FillPath =
        GPUFillPathCommandBuilder.build(
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
            material = solid,
            clip = clip,
        )

    // --- KGPU-M32-014: fill commands refuse complex clips (dispatch guard) ---

    @Test
    fun `complex-clip rect refuses with unsupported_clip`() {
        assertEquals("unsupported_clip:ComplexStack", rectCmd(complexClip()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `complex-clip rrect refuses with unsupported_clip`() {
        assertEquals("unsupported_clip:ComplexStack", rrectCmd(complexClip()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `complex-clip path refuses with unsupported_clip`() {
        assertEquals("unsupported_clip:ComplexStack", pathCmd(complexClip()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `wideopen and devicerect clips do not refuse on fills`() {
        assertNull(rectCmd(GPUClipFacts.wideOpen(GPUBounds(0f, 0f, 10f, 10f))).fillGuardRefusalReasonOrNull())
        assertNull(rectCmd(GPUClipFacts.deviceRect(GPUBounds(0f, 0f, 10f, 10f))).fillGuardRefusalReasonOrNull())
    }

    // --- Reachability honesty: public API only produces WideOpen clips ---

    @Test
    fun `drawRect via public API produces a WideOpen clip`() {
        val surface = Surface(width = 64, height = 64)
        Canvas(surface).drawRect(Rect(0f, 0f, 10f, 10f), Paint().color(1f, 0f, 0f, 1f))
        val cmd = surface.recorder.recordedCommands().single() as NormalizedDrawCommand.FillRect
        assertEquals(GPUClipKind.WideOpen, cmd.clip.kind)
    }

    // --- KGPU-M32-018: text run refuses complex clips (planner) ---

    @Test
    fun `text run with complex clip refuses with unsupported_clip`() {
        val surface = Surface(width = 320, height = 240)
        Canvas(surface).drawTextBlob(
            TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(65u, 66u, 67u),
                        positions = listOf(KanvasPoint(0f, 0f), KanvasPoint(20f, 0f), KanvasPoint(40f, 0f)),
                    ),
                ),
            ),
            10f,
            50f,
            Paint().color(1f, 1f, 1f, 1f),
        )
        val textCmd = surface.recorder.recordedCommands()
            .filterIsInstance<NormalizedDrawCommand.DrawTextRun>()
            .single()
            .copy(clip = GPUClipFacts.complexStack(GPUBounds(0f, 0f, 10f, 10f)))

        val plan = TextRunDispatchPlanner.plan(textCmd, 320, 240)
        val refused = assertIs<TextRunDispatchPlan.Refused>(plan)
        assertEquals("unsupported_clip:ComplexStack", refused.reason)
    }
}
