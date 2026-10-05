@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.surface.gpu.GPUPlanSurfaceTerminalException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterAll

/** Public W7 witness for a resolved AA path colour source inside a W6 layer. */
class W7AaPathLayerSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `aa layer B minus one refuses`() {
        // B is hand-derived from this fixed 7x7 direct-triangle fixture before either Surface
        // exists. The triangle's conservative bounds are (1,1)..(5,5), so its layer and AA
        // source are 4x4. Charge every declared physical row: root RGBA8 (196), seven aligned
        // 256-byte readback rows (1792), layer RGBA8 (64), AA4 colour (256), sampled resolve
        // (64), W4d V/I/U pool floors (16384 + 4096 + 4096), the W4d uniform (16), and the
        // solid material source uniform (16). Thus B = 26980; no lifetime/cache alias discount
        // is taken.  The native sRGB-4x capability branch below deliberately does not claim B.
        val budgetB = listOf(196L, 1792L, 64L, 256L, 64L, 16_384L, 4_096L, 4_096L, 16L, 16L)
            .fold(0L, Math::addExact)
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val triangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }

        fun record(surface: Surface) = surface.canvas {
            saveLayer()
            drawPath(triangle, Paint(blue, antiAlias = true))
            restore()
        }

        val admitted = Surface(7, 7, config = RenderConfig(frameLocalBudgetBytes = budgetB))
        record(admitted)
        val admittedResult = renderOrAcceptExactAaCapabilityRefusal(admitted)
        if (admittedResult == null) {
            // The exact sRGB-4x gate is a refusal before native allocation.  Prove that it does
            // not poison this same public Surface; this is capability-refusal recovery, not a
            // claim that the B/B-1 budget path ran on this host.
            admitted.discardRecordedOperations()
            admitted.canvas { drawRect(bounds, Paint(blue, antiAlias = false)) }
            val recovered = admitted.render()
            assertPixel(recovered.pixels, 7, 3, 3, 17, 61, 211, 255)
            assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                recovered.nativeEvidenceScopeKinds.toString())
            return
        }
        assertPixel(admittedResult.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertTrue(admittedResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            admittedResult.nativeEvidenceScopeKinds.toString())

        val refusal = Surface(7, 7, config = RenderConfig(
            frameLocalBudgetBytes = Math.subtractExact(budgetB, 1L),
        ))
        record(refusal)
        val sentinel = UByteArray(7 * 7 * 4) { 0x5au }
        val before = sentinel.copyOf()
        val failure = assertFailsWith<IllegalStateException> { refusal.readPixels(bounds, sentinel) }
        assertTrue(failure.message?.startsWith("w6a.layer.frame_budget_exceeded:") == true, failure.message)
        assertContentEquals(before, sentinel)

        refusal.discardRecordedOperations()
        refusal.canvas { drawRect(bounds, Paint(blue, antiAlias = false)) }
        val recovered = refusal.render()
        assertPixel(recovered.pixels, 7, 3, 3, 17, 61, 211, 255)
        assertTrue(recovered.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            recovered.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa filtered path stays outside color source`() {
        val triangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }
        val bounds = RectF32.ofLTRB(0f, 0f, 7f, 7f)
        val filters = listOf(
            Paint(ColorARGB.Blue, imageFilter = ImageFilter.Blur(1f, 1f), antiAlias = true),
            Paint(ColorARGB.Blue, maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1f), antiAlias = true),
        )
        filters.forEach { paint ->
            val surface = Surface(7, 7)
            surface.canvas {
                saveLayer()
                drawPath(triangle, paint)
                restore()
            }
            val sentinel = UByteArray(7 * 7 * 4) { 0x5au }
            val before = sentinel.copyOf()
            val failure = assertFailsWith<IllegalStateException> { surface.readPixels(bounds, sentinel) }
            // A filter must not be stripped and promoted to the W4d AA-colour source.  Its
            // owner-specific W6/W6b refusal is the public boundary until separate filter and
            // ResolvedCoverage projects define a source contract.
            assertTrue(failure.message?.startsWith("w6a.layer.unsupported_spatial_filter:") == true,
                failure.message)
            assertContentEquals(before, sentinel)
        }
    }

    @Test
    fun `aa children preserve order`() {
        // The second opaque child must cover the first at (2, 2); (6, 6) is
        // deliberately outside both source triangles. These literals precede Surface.
        val first = ColorARGB.of(255, 239, 51, 73)
        val second = ColorARGB.of(255, 17, 61, 211)
        val firstTriangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }
        val secondTriangle = Path().apply {
            moveTo(2f, 1f); lineTo(5f, 1f); lineTo(2f, 4f); close()
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(firstTriangle, Paint(first, antiAlias = true))
            drawPath(secondTriangle, Paint(second, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 2, 2, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 6, 6, 0, 0, 0, 0)
        // A non-overlap interior keeps the first child, proving order rather than replacement.
        assertPixel(result.pixels, 7, 1, 3, 239, 51, 73, 255)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa source alpha is composed exactly once`() {
        // SrcOver of a fully covered alpha-128 white source on opaque black is
        // the literal linear-sRGB attachment byte (188, 188, 188, 255). A
        // second alpha application would instead darken this pixel.
        val opaqueBlack = ColorARGB.Black
        val halfWhite = ColorARGB.of(128, 255, 255, 255)
        val fullyCovered = Path().apply { addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f)) }

        val surface = Surface(7, 7)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(opaqueBlack, antiAlias = false))
            saveLayer()
            drawPath(fullyCovered, Paint(halfWhite, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 3, 3, 188, 188, 188, 255)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa translated layer keeps source alignment`() {
        // The layer has a non-zero device origin, while the child has a fractional
        // translation. These literals precede Surface; the complete 5x5 layer
        // region must also equal the origin-zero control at its known (2, 1) offset.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val localTriangle = Path().apply {
            moveTo(2f, 1f); lineTo(5f, 1f); lineTo(2f, 4f); close()
        }
        val originZeroTriangle = Path().apply {
            moveTo(0f, 0f); lineTo(3f, 0f); lineTo(0f, 3f); close()
        }

        val translated = Surface(8, 7)
        translated.canvas {
            saveLayer(RectF32.ofLTRB(2f, 1f, 7f, 6f))
            translate(.5f, .5f)
            drawPath(localTriangle, Paint(blue, antiAlias = true))
            restore()
        }
        val originZero = Surface(5, 5)
        originZero.canvas {
            saveLayer(RectF32.ofLTRB(0f, 0f, 5f, 5f))
            translate(.5f, .5f)
            drawPath(originZeroTriangle, Paint(blue, antiAlias = true))
            restore()
        }
        val translatedResult = renderOrAcceptExactAaCapabilityRefusal(translated) ?: return
        val originZeroResult = renderOrAcceptExactAaCapabilityRefusal(originZero) ?: return
        assertPixel(translatedResult.pixels, 8, 3, 2, 17, 61, 211, 255)
        assertPixel(translatedResult.pixels, 8, 7, 6, 0, 0, 0, 0)
        val edgeAlpha = translatedResult.pixels[((2 * 8 + 4) * 4) + 3].toInt()
        assertTrue(edgeAlpha in 1..254, "expected translated AA edge alpha, got $edgeAlpha")
        assertContentEquals(originZeroResult.pixels, copyPixelRegion(translatedResult.pixels, 8, 2, 1, 5, 5))
        assertTrue(translatedResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            translatedResult.nativeEvidenceScopeKinds.toString())
        assertTrue(originZeroResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            originZeroResult.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa concave stencil path preserves notch`() {
        // A concave winding L forces the stencil route.  These full-coverage samples and the
        // notch are literal public observations, fixed before Surface construction.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val concaveL = Path().apply {
            moveTo(1f, 1f); lineTo(6f, 1f); lineTo(6f, 3f)
            lineTo(3f, 3f); lineTo(3f, 6f); lineTo(1f, 6f); close()
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(concaveL, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 4, 4, 0, 0, 0, 0)
        assertPixel(result.pixels, 7, 0, 0, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa even odd stencil path preserves hole`() {
        // Same-winding contours select EVEN_ODD. The center is a hole, not transparent fallout.
        val blue = ColorARGB.of(255, 17, 61, 211)
        val donut = Path().apply {
            addRect(RectF32.ofLTRB(1f, 1f, 6f, 6f))
            addRect(RectF32.ofLTRB(2f, 2f, 5f, 5f))
            fillType = FillType.EVEN_ODD
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(donut, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 3, 3, 0, 0, 0, 0)
        assertPixel(result.pixels, 7, 0, 0, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa stencil children are isolated`() {
        // Both concave children require independent stencil state. The second's blue sample and
        // the exterior transparent sample detect a stale stencil/resolve from the first child.
        val green = ColorARGB.of(255, 45, 179, 97)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val first = Path().apply {
            moveTo(1f, 1f); lineTo(3f, 1f); lineTo(3f, 3f)
            lineTo(2f, 3f); lineTo(2f, 5f); lineTo(1f, 5f); close()
        }
        val second = Path().apply {
            moveTo(4f, 1f); lineTo(6f, 1f); lineTo(6f, 3f)
            lineTo(5f, 3f); lineTo(5f, 5f); lineTo(4f, 5f); close()
        }

        val surface = Surface(7, 7)
        surface.canvas {
            saveLayer()
            drawPath(first, Paint(green, antiAlias = true))
            drawPath(second, Paint(blue, antiAlias = true))
            restore()
        }
        val result = renderOrAcceptExactAaCapabilityRefusal(surface) ?: return
        assertPixel(result.pixels, 7, 1, 1, 45, 179, 97, 255)
        assertPixel(result.pixels, 7, 4, 1, 17, 61, 211, 255)
        assertPixel(result.pixels, 7, 0, 6, 0, 0, 0, 0)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            result.nativeEvidenceScopeKinds.toString())
    }

    @Test
    fun `aa direct triangle preserves painted parent`() {
        // These literal samples are fixed before either Surface exists.  The full-cover sample
        // must be blue, while both exterior samples prove the isolated resolve did not clear the
        // opaque red parent.  (4, 1) is centred on the diagonal AA edge in the transparent run.
        val red = ColorARGB.of(255, 239, 51, 73)
        val blue = ColorARGB.of(255, 17, 61, 211)
        val triangle = Path().apply {
            moveTo(1f, 1f); lineTo(5f, 1f); lineTo(1f, 5f); close()
        }

        val paintedParent = Surface(7, 7)
        paintedParent.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 7f, 7f), Paint(red, antiAlias = false))
            saveLayer()
            drawPath(triangle, Paint(blue, antiAlias = true))
            restore()
        }
        val parentResult = try {
            paintedParent.render()
        } catch (refusal: GPUPlanSurfaceTerminalException) {
            // The runtime capability snapshot may refuse sRGB 4x. This does not establish
            // hardware incapability and cannot stand in for the positive pixel oracle below.
            assertTrue(refusal.message?.contains("w4d.general.texture-sample-support-unavailable") == true,
                refusal.message)
            assertTrue(refusal.message?.contains("W4d.2 four-sample color support is unavailable") == true,
                refusal.message)
            println("W7 capability refusal (no positive pixel witness): ${refusal.message}")
            return
        }
        assertPixel(parentResult.pixels, 7, 1, 1, 17, 61, 211, 255)
        assertPixel(parentResult.pixels, 7, 0, 0, 239, 51, 73, 255)
        assertPixel(parentResult.pixels, 7, 5, 5, 239, 51, 73, 255)
        assertTrue(parentResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            parentResult.nativeEvidenceScopeKinds.toString())

        val transparentParent = Surface(7, 7)
        transparentParent.canvas {
            saveLayer()
            drawPath(triangle, Paint(blue, antiAlias = true))
            restore()
        }
        val transparentResult = transparentParent.render()
        val diagonalAlpha = transparentResult.pixels[((1 * 7 + 4) * 4) + 3].toInt()
        assertTrue(diagonalAlpha in 1..254, "expected intermediate AA alpha, got $diagonalAlpha")
        assertTrue(transparentResult.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            transparentResult.nativeEvidenceScopeKinds.toString())
    }

    private fun assertPixel(
        pixels: UByteArray,
        width: Int,
        x: Int,
        y: Int,
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int,
    ) {
        val offset = (y * width + x) * 4
        assertTrue(pixels[offset].toInt() == red && pixels[offset + 1].toInt() == green &&
            pixels[offset + 2].toInt() == blue && pixels[offset + 3].toInt() == alpha,
            "pixel ($x,$y) was ${pixels.copyOfRange(offset, offset + 4).toList()}")
    }

    private fun copyPixelRegion(
        pixels: UByteArray,
        sourceWidth: Int,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
    ): UByteArray = UByteArray(width * height * 4).also { result ->
        for (row in 0 until height) {
            val sourceOffset = ((top + row) * sourceWidth + left) * 4
            val destinationOffset = row * width * 4
            pixels.copyInto(result, destinationOffset, sourceOffset, sourceOffset + width * 4)
        }
    }

    private fun renderOrAcceptExactAaCapabilityRefusal(surface: Surface): RenderResult? = try {
        surface.render()
    } catch (refusal: GPUPlanSurfaceTerminalException) {
        // The runtime capability snapshot may refuse sRGB 4x. This does not establish hardware
        // incapability and cannot stand in for the positive pixel oracles above.
        assertTrue(refusal.message?.contains("w4d.general.texture-sample-support-unavailable") == true,
            refusal.message)
        assertTrue(refusal.message?.contains("W4d.2 four-sample color support is unavailable") == true,
            refusal.message)
        println("W7 capability refusal (no positive pixel witness): ${refusal.message}")
        null
    }
}
