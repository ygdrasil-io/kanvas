package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect
import kotlin.math.abs

/**
 * G2.1 acceptance test — translucent SrcOver blending on GPU.
 *
 * Drops the G1.2 `alpha == 0xFF` guard : the shader now premultiplies
 * the source color before the pipeline blend, so the standard SrcOver
 * `(src=One, dst=OneMinusSrcAlpha)` math produces correctly blended
 * pixels for any alpha.
 *
 * The render target stores premultiplied values (consequence of premul
 * shader output + SrcOver blend). Readback bytes are therefore premul
 * too — this matters when comparing against `SkBitmap.pixels8888` which
 * is conventionally non-premul. A premul-to-unpremul present pass is
 * future work (G6, alongside the linear-Rec.2020 working-space
 * convergence). For now, the test asserts directly on the premul bytes.
 */
class TranslucentSrcOverTest {

    @Test
    fun `50 percent blue rect over opaque red blends to expected premul bytes`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // alpha = 0x80 (128) -> 0.50196 in normalized float
        val translucentBlue = SkColorSetARGB(0x80, 0x00, 0x00, 0xFF)
        val paint = SkPaint().apply { color = translucentBlue }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorRED)
                val canvas = SkCanvas(device)
                canvas.drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), paint)
                device.flush()
            }
        }

        // OUTSIDE the rect : background red, premul == unpremul for opaque.
        // (50, 50) is well clear of the rect [10, 30) x [10, 30).
        val outside = pixels.rgbaAt(50, 50)
        assertEquals(
            listOf(255, 0, 0, 255), outside,
            "outside the rect must keep the opaque-red clear value",
        )

        // INSIDE the rect (20, 20) : translucent blue over opaque red.
        //   src_unpremul = (0, 0, 1, 0.5019...)
        //   src_premul   = (0, 0, 0.5019..., 0.5019...)
        //   dst          = (1, 0, 0, 1)    (premul red, opaque)
        //   srcOver:     out = src_premul + dst * (1 - src.a)
        //                    = (0 + 0.498..., 0, 0.5019... + 0, 0.5019... + 0.498...)
        //                    = (~0.498, 0, ~0.502, 1.0)
        //   bytes        = (127, 0, 128, 255)  (give or take 1 ulp on R/B from
        //                                       FP rounding in the shader)
        val inside = pixels.rgbaAt(20, 20)
        assertNear(
            expected = listOf(127, 0, 128, 255),
            actual = inside,
            tolerance = 1,
            label = "translucent-blue-over-red blend at (20,20)",
        )

        // Sanity: alpha came out opaque (we composited a 50% src onto an
        // opaque dst -- SrcOver always yields opaque alpha in that case).
        assertEquals(255, inside[3], "blended pixel must be opaque")
    }

    private fun ByteArray.rgbaAt(x: Int, y: Int): List<Int> {
        val i = (y * W + x) * 4
        return listOf(
            this[i].toInt() and 0xFF,
            this[i + 1].toInt() and 0xFF,
            this[i + 2].toInt() and 0xFF,
            this[i + 3].toInt() and 0xFF,
        )
    }

    private fun assertNear(expected: List<Int>, actual: List<Int>, tolerance: Int, label: String) {
        for (c in 0 until 4) {
            assertTrue(
                abs(expected[c] - actual[c]) <= tolerance,
                "$label channel $c: expected ${expected[c]} +/- $tolerance, got ${actual[c]} " +
                    "(full pixel expected=$expected, actual=$actual)",
            )
        }
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
