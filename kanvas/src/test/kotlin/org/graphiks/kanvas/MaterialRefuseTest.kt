package org.graphiks.kanvas

import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillPathCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUFillRectCommandBuilder
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUPathFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hermetic (no-GPU) coverage proving non-SolidColor fill materials REFUSE
 * (`unsupported_material:<kind>`) instead of being silently solid-filled.
 *
 * Families:
 * - KGPU-M32-010 (material-paint, refused sub-case): gradients refuse.
 * - KGPU-M32-012 (rounded-rect-gradients, refused sub-case): gradient rrect refuses.
 * - KGPU-M32-016 (images): image-shader / ImageDraw material refuses.
 * - KGPU-M32-019 (runtime-effects, refused sub-case): runtime-effect material refuses.
 *
 * The real GPU pixel + emitted-diagnostic end-to-end evidence (via
 * `surface.renderToRgba()` / the bridge) is GPU-gated (assumeTrue WebGPU) and
 * kept separate; these tests assert the dispatch-guard refusal that runs
 * BEFORE any fill dispatch, so dispatched-count would be 0 for these commands.
 */
class MaterialRefuseTest {
    private fun target(): GPUTargetFacts =
        GPUTargetFacts(width = 64, height = 64, colorFormat = "rgba8unorm")

    private fun firstCommand(block: Canvas.() -> Unit): NormalizedDrawCommand {
        val surface = Surface(width = 64, height = 64)
        Canvas(surface).block()
        return surface.recorder.recordedCommands().single()
    }

    private fun linearGradient(): GPUMaterialDescriptor.LinearGradient =
        GPUMaterialDescriptor.LinearGradient(
            startX = 0f, startY = 0f, endX = 10f, endY = 10f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        )

    private fun radialGradient(): GPUMaterialDescriptor.RadialGradient =
        GPUMaterialDescriptor.RadialGradient(
            centerX = 5f, centerY = 5f, radius = 5f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        )

    private fun sweepGradient(): GPUMaterialDescriptor.SweepGradient =
        GPUMaterialDescriptor.SweepGradient(
            centerX = 5f, centerY = 5f, startAngle = 0f, endAngle = 6.28f,
            startR = 1f, startG = 0f, startB = 0f, startA = 1f,
            endR = 0f, endG = 0f, endB = 1f, endA = 1f,
        )

    private fun rectCmd(material: GPUMaterialDescriptor): NormalizedDrawCommand.FillRect =
        GPUFillRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rect = GPURect(0f, 0f, 10f, 10f),
            target = target(),
            material = material,
        )

    private fun rrectCmd(material: GPUMaterialDescriptor): NormalizedDrawCommand.FillRRect =
        GPUFillRRectCommandBuilder.build(
            commandId = GPUDrawCommandID(0),
            rrect = GPURRect(GPURect(0f, 0f, 20f, 20f), radiusX = 4f, radiusY = 4f),
            target = target(),
            material = material,
        )

    private fun pathCmd(material: GPUMaterialDescriptor): NormalizedDrawCommand.FillPath =
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
            material = material,
        )

    // --- KGPU-M32-010 / -012: gradient materials refuse (dispatch guard) ---

    @Test
    fun `linear gradient rect is accepted by dispatch`() {
        assertNull(rectCmd(linearGradient()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `radial gradient rect refuses with unsupported_material`() {
        assertEquals("unsupported_material:RadialGradient", rectCmd(radialGradient()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `sweep gradient rect refuses with unsupported_material`() {
        assertEquals("unsupported_material:SweepGradient", rectCmd(sweepGradient()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `linear gradient rrect refuses with unsupported_material`() {
        assertEquals("unsupported_material:LinearGradient", rrectCmd(linearGradient()).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `linear gradient path is accepted by dispatch`() {
        assertNull(pathCmd(linearGradient()).fillGuardRefusalReasonOrNull())
    }

    // --- KGPU-M32-016: ImageDraw material refuses (dispatch guard, kanvas-level) ---

    @Test
    fun `image-draw rect refuses with unsupported_material`() {
        val img = GPUMaterialDescriptor.ImageDraw(imageSourceId = "img", imageWidth = 16, imageHeight = 16)
        assertEquals("unsupported_material:ImageDraw", rectCmd(img).fillGuardRefusalReasonOrNull())
    }

    // --- KGPU-M32-019: runtime-effect material refuses (dispatch guard) ---

    @Test
    fun `runtime-effect rect refuses with unsupported_material`() {
        val rt = GPUMaterialDescriptor.RuntimeEffect(effectId = "blur", descriptorVersion = 1)
        assertEquals("unsupported_material:RuntimeEffect", rectCmd(rt).fillGuardRefusalReasonOrNull())
    }

    // --- Canvas lowering: gradients become non-solid materials (not silent solid) ---

    @Test
    fun `drawRect with linear gradient shader lowers to a non-solid material`() {
        val cmd = firstCommand {
            drawRect(
                Rect(0f, 0f, 10f, 10f),
                Paint().shader(
                    Shader.LinearGradient(
                        start = KanvasPoint(0f, 0f), end = KanvasPoint(10f, 10f),
                        stops = listOf(Triple(1f, 0f, 0f), Triple(0f, 0f, 1f)),
                    ),
                ),
            )
        } as NormalizedDrawCommand.FillRect
        assertEquals(GPUMaterialKind.LinearGradient, cmd.material.kind)
        assertNull(cmd.fillGuardRefusalReasonOrNull())
    }

    // --- FIX REGRESSION: image-shader (Shader.Bitmap) must NOT silently solid-fill ---

    @Test
    fun `drawRect with bitmap image shader is refused not silently solid-filled`() {
        val image = Image(width = 16, height = 16, colorType = KanvasColorType.RGBA_8888, sourceId = "img-1")
        val cmd = firstCommand {
            drawRect(Rect(0f, 0f, 10f, 10f), Paint().color(1f, 0f, 0f, 1f).shader(Shader.Bitmap(image)))
        } as NormalizedDrawCommand.FillRect
        assertTrue(cmd.material.kind != GPUMaterialKind.SolidColor, "bitmap shader must not lower to SolidColor")
        assertEquals(GPUMaterialKind.ImageDraw, cmd.material.kind)
        assertEquals("unsupported_material:ImageDraw", cmd.fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `drawRRect with bitmap image shader is refused not silently solid-filled`() {
        val image = Image(width = 16, height = 16, colorType = KanvasColorType.RGBA_8888, sourceId = "img-2")
        val cmd = firstCommand {
            drawRRect(
                RRect(Rect(0f, 0f, 20f, 20f), RRectCornerRadii(4f, 4f)),
                Paint().color(1f, 0f, 0f, 1f).shader(Shader.Bitmap(image)),
            )
        } as NormalizedDrawCommand.FillRRect
        assertEquals(GPUMaterialKind.ImageDraw, cmd.material.kind)
        assertEquals("unsupported_material:ImageDraw", cmd.fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `drawPath with bitmap image shader is refused not silently solid-filled`() {
        val image = Image(width = 16, height = 16, colorType = KanvasColorType.RGBA_8888, sourceId = "img-3")
        val cmd = firstCommand {
            val path = Path().apply { moveTo(0f, 0f); lineTo(10f, 0f); lineTo(5f, 10f); close() }
            drawPath(path, Paint().color(1f, 0f, 0f, 1f).shader(Shader.Bitmap(image)))
        } as NormalizedDrawCommand.FillPath
        assertEquals(GPUMaterialKind.ImageDraw, cmd.material.kind)
        assertEquals("unsupported_material:ImageDraw", cmd.fillGuardRefusalReasonOrNull())
    }

    // --- FIX REGRESSION: runtime-effect shader must NOT silently solid-fill ---

    @Test
    fun `drawRect with runtime-effect shader is refused not silently solid-filled`() {
        val cmd = firstCommand {
            drawRect(
                Rect(0f, 0f, 10f, 10f),
                Paint().color(1f, 0f, 0f, 1f).shader(Shader.RuntimeEffect(effectId = "blur")),
            )
        } as NormalizedDrawCommand.FillRect
        assertTrue(cmd.material.kind != GPUMaterialKind.SolidColor, "runtime-effect shader must not lower to SolidColor")
        assertEquals(GPUMaterialKind.RuntimeEffect, cmd.material.kind)
        assertEquals("unsupported_material:RuntimeEffect", cmd.fillGuardRefusalReasonOrNull())
    }

    // --- Sanity: solid color does NOT refuse (defaults unchanged) ---

    @Test
    fun `solid color fill command does not refuse`() {
        assertNull(rectCmd(GPUMaterialDescriptor.SolidColor(1f, 0f, 0f, 1f)).fillGuardRefusalReasonOrNull())
    }

    @Test
    fun `drawRect with default solid paint lowers to SolidColor and does not refuse`() {
        val cmd = firstCommand { drawRect(Rect(0f, 0f, 10f, 10f), Paint().color(1f, 0f, 0f, 1f)) }
            as NormalizedDrawCommand.FillRect
        assertEquals(GPUMaterialKind.SolidColor, cmd.material.kind)
        assertNull(cmd.fillGuardRefusalReasonOrNull())
    }
}
