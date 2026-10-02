@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.gm.blur.InverseFillFiltersGm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Retained public W7 regression for GM337's unfiltered inverse/hairline cell. */
class W7InverseHairlineAssemblySurfaceTest {
    companion object {
        private const val widthI32 = 384
        private const val heightI32 = 128
        private val cellBounds = RectF32.ofLTRB(0f, 0f, 128f, 128f)

        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `GM337 plain inverse cell through real GmCanvas is native and repeatable`() {
        val surface = Surface(widthI32, heightI32, config = InverseFillFiltersGm().compositionConfig()).also { target ->
            val canvas = GmCanvas(target.canvas(), widthI32, heightI32)
            canvas.drawRect(
                RectF32.ofLTRB(0f, 0f, widthI32.toFloat(), heightI32.toFloat()),
                Paint(color = ColorARGB.White, antiAlias = false),
            )
            canvas.save()
            canvas.clipRect(cellBounds)
            canvas.drawPath(inverseCircle(), Paint(antiAlias = true))
            canvas.restore()
            canvas.drawRect(cellBounds, Paint(style = PaintStyle.STROKE, color = ColorARGB.White))
        }

        val result = surface.renderAndRepeat()
        assertPixel(result, 10, 65, ColorARGB.Black)
        assertPixel(result, 65, 65, ColorARGB.White)
        assertPixel(result, 130, 65, ColorARGB.White)
    }

    private fun inverseCircle(): Path = Path { }.apply {
        addCircle(65f, 65f, 30f)
        fillType = FillType.INVERSE_WINDING
    }

    private fun Surface.renderAndRepeat(): RenderResult {
        val first = render()
        assertNative(first)
        val second = render()
        assertNative(second)
        assertArrayEquals(
            first.pixels.toByteArray(),
            second.pixels.toByteArray(),
            "second retained Surface frame must be byte-identical",
        )
        return first
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
        assertArrayEquals(
            ubyteArrayOf(
                expected.red.toUByte(), expected.green.toUByte(), expected.blue.toUByte(), expected.alpha.toUByte(),
            ).toByteArray(),
            result.pixels.copyOfRange(offsetI32, offsetI32 + 4).toByteArray(),
            "pixel ($xI32,$yI32)",
        )
    }
}
