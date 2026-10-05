@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** W7 witness: an admitted NORMAL mask-blur sibling does not invalidate an unfiltered root hairline. */
class W7FilteredSiblingHairlineSurfacePixelTest {
    companion object {
        private const val widthI32 = 128
        private const val heightI32 = 96
        private const val hairlineOffsetXI32 = 96

        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `unfiltered identity root hairline survives normal blur sibling`() {
        val surface = filteredSiblingSurface { canvas ->
            canvas.translate(hairlineOffsetXI32.toFloat(), 0f)
            canvas.drawRect(
                RectF32.ofLTRB(2.5f, 2.5f, 7.5f, 7.5f),
                Paint(ColorARGB.White, antiAlias = true, style = PaintStyle.STROKE),
            )
        }

        val result = surface.renderAndRepeat()
        assertPixel(result, 24, 24, ColorARGB.Black)
        assertPixel(result, 90, 90, ColorARGB.Blue)
        assertIdentityHairlineTable(result)
    }

    @Test
    fun `unfiltered transformed root hairline survives normal blur sibling`() {
        val surface = filteredSiblingSurface { canvas ->
            canvas.translate(hairlineOffsetXI32.toFloat(), 0f)
            canvas.save()
            canvas.translate(2f, 1f)
            canvas.scale(3f, 1f)
            canvas.drawRect(
                RectF32.ofLTRB(1.5f, 1.5f, 3.5f, 3.5f),
                Paint(ColorARGB.White, antiAlias = true, style = PaintStyle.STROKE),
            )
            canvas.restore()
        }

        val result = surface.renderAndRepeat()
        assertPixel(result, 24, 24, ColorARGB.Black)
        assertPixel(result, 90, 90, ColorARGB.Blue)
        assertTransformedHairlineTable(result)
    }

    @Test
    fun `actual image filtered stroke remains refused without readback publication and recovers`() {
        val surface = filteredSiblingSurface { canvas ->
            canvas.translate(hairlineOffsetXI32.toFloat(), 0f)
            canvas.drawRect(
                RectF32.ofLTRB(2.5f, 2.5f, 7.5f, 7.5f),
                Paint(
                    ColorARGB.White,
                    antiAlias = true,
                    style = PaintStyle.STROKE,
                    imageFilter = ImageFilter.Blur(1f, 1f),
                ),
            )
        }
        val sentinel = UByteArray(widthI32 * heightI32 * 4) { 0x5au }
        val before = sentinel.copyOf()

        val failure = assertFailsWith<IllegalStateException> { surface.readPixels(fullBounds(), sentinel) }
        assertTrue(
            failure.message?.startsWith("w6a.layer.unsupported_child:") == true,
            failure.message ?: "missing filtered-stroke refusal",
        )
        assertContentEquals(before, sentinel)

        surface.discardRecordedOperations()
        surface.canvas { drawRect(fullBounds(), Paint(ColorARGB.Blue, antiAlias = false)) }
        val recovered = surface.renderAndRepeat()
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            assertPixel(recovered, xI32, yI32, ColorARGB.Blue)
        }
    }

    private fun filteredSiblingSurface(drawHairline: (org.graphiks.kanvas.canvas.Canvas) -> Unit): Surface =
        Surface(widthI32, heightI32).also { target -> target.canvas {
            drawRect(fullBounds(), Paint(ColorARGB.Blue, antiAlias = false))
            drawPath(
                triangle(),
                Paint(
                    ColorARGB.Black,
                    antiAlias = true,
                    maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 1.5f),
                ),
            )
            save()
            drawHairline(this)
            restore()
        } }

    private fun triangle(): Path = Path().apply {
        moveTo(16f, 16f)
        lineTo(80f, 16f)
        lineTo(16f, 80f)
        close()
    }

    private fun fullBounds(): RectF32 = RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat())

    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render()
        assertNative(first)
        val second = render()
        assertNative(second)
        assertContentEquals(first.pixels, second.pixels, "second retained Surface frame must be byte-identical")
        return first
    }

    private fun assertIdentityHairlineTable(result: RenderResult) {
        for (localYI32 in 0 until 10) for (localXI32 in 0 until 16) {
            val expected = if (
                localXI32 in 2..7 && localYI32 in 2..7 &&
                (localXI32 == 2 || localXI32 == 7 || localYI32 == 2 || localYI32 == 7)
            ) ColorARGB.White else ColorARGB.Blue
            assertPixel(result, hairlineOffsetXI32 + localXI32, localYI32, expected)
        }
    }

    private fun assertTransformedHairlineTable(result: RenderResult) {
        for (localYI32 in 0 until 10) for (localXI32 in 0 until 16) {
            val expected = if (
                localXI32 in 6..12 && localYI32 in 2..4 &&
                (localXI32 == 6 || localXI32 == 12 || localYI32 == 2 || localYI32 == 4)
            ) ColorARGB.White else ColorARGB.Blue
            assertPixel(result, hairlineOffsetXI32 + localXI32, localYI32, expected)
        }
    }

    private fun assertNative(result: RenderResult) {
        val trace = "diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.isClean, trace)
        assertTrue(result.diagnostics.isEmpty, trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertEquals(0, result.stats.opsRefused, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")), trace)
    }

    private fun assertPixel(result: RenderResult, xI32: Int, yI32: Int, expected: ColorARGB) {
        val offsetI32 = (yI32 * widthI32 + xI32) * 4
        assertContentEquals(
            ubyteArrayOf(
                expected.red.toUByte(), expected.green.toUByte(), expected.blue.toUByte(), expected.alpha.toUByte(),
            ),
            result.pixels.copyOfRange(offsetI32, offsetI32 + 4),
            "pixel ($xI32,$yI32)",
        )
    }
}
