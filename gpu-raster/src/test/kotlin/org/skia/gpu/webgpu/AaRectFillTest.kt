package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.graphiks.math.SkRect
import kotlin.math.abs

/**
 * G2.3a acceptance tests — analytical-coverage AA rect on the GPU.
 *
 * The shader now multiplies the premultiplied source color by a
 * coverage factor in `[0, 1]` derived from the intersection length
 * between each pixel and the rect bounds (the same axis-aligned
 * formula `SkBitmapDevice` uses for AA). Non-AA continues to round to
 * pixelEdge, so coverage degenerates to 1.0 for interior pixels and
 * the bytes are byte-identical to the pre-G2.3a path (the existing
 * `ClearRedTest` / `RectFillCrossTest` / `TranslucentSrcOverTest` /
 * `BlendModeTest` suites cover that backward-compat surface).
 */
class AaRectFillTest {

    @Test
    fun `aa rect with half-integer edges produces partial coverage on edge pixels`() {
        // Rect [10.5, 10.5, 30.5, 30.5] : edges sit exactly on
        // half-integer device coordinates. Edge pixels (col 10, col 30,
        // row 10, row 30) are bisected by the edge -> coverage = 0.5.
        // Interior pixels (cols 11..29, rows 11..29) -> coverage = 1.0.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10.5f, 10.5f, 30.5f, 30.5f), paint)
                device.flush()
            }
        }

        // Far outside the rect : background white, untouched.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(50, 50), "outside : white bg")

        // Interior pixel (15, 15) : coverage = 1.0 -> full blue SrcOver
        // over white = pure blue (opaque src wipes dst).
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(15, 15), "interior : opaque blue")

        // Left-edge pixel (10, 15) : cov_x = 0.5 (pixel [10, 11] meets
        // rect at [10.5, 11]), cov_y = 1.0 -> coverage = 0.5.
        //   premul src       = (0, 0, 1, 1) * 0.5 = (0, 0, 0.5, 0.5)
        //   SrcOver on white = src + dst * (1 - 0.5)
        //                    = (0 + 0.5,  0 + 0.5,  0.5 + 0.5,  0.5 + 0.5)
        //                    = (0.5, 0.5, 1.0, 1.0)
        //   bytes (+/- 1)   = (128, 128, 255, 255)
        assertNear(
            expected = listOf(128, 128, 255, 255),
            actual = pixels.rgbaAt(10, 15),
            tolerance = 1,
            label = "left edge half-coverage",
        )

        // Right-edge pixel (30, 15) : symmetric to left, same expected.
        assertNear(
            expected = listOf(128, 128, 255, 255),
            actual = pixels.rgbaAt(30, 15),
            tolerance = 1,
            label = "right edge half-coverage",
        )

        // Top-edge pixel (15, 10) : symmetric on y.
        assertNear(
            expected = listOf(128, 128, 255, 255),
            actual = pixels.rgbaAt(15, 10),
            tolerance = 1,
            label = "top edge half-coverage",
        )

        // Corner pixel (10, 10) : cov_x = 0.5, cov_y = 0.5 -> coverage = 0.25.
        //   premul src       = (0, 0, 1, 1) * 0.25 = (0, 0, 0.25, 0.25)
        //   SrcOver on white = src + dst * 0.75
        //                    = (0 + 0.75, 0 + 0.75, 0.25 + 0.75, 0.25 + 0.75)
        //                    = (0.75, 0.75, 1.0, 1.0)
        //   bytes (+/- 1)   = (191, 191, 255, 255)
        assertNear(
            expected = listOf(191, 191, 255, 255),
            actual = pixels.rgbaAt(10, 10),
            tolerance = 1,
            label = "corner quarter-coverage",
        )
    }

    @Test
    fun `aa rect with integer edges still produces full coverage everywhere inside`() {
        // Sanity : an AA rect that happens to land on integer
        // boundaries should look identical to the non-AA path -- every
        // visible pixel has coverage = 1.0 (no edge bisection).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }
        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawRect(SkRect.MakeLTRB(10f, 10f, 30f, 30f), paint)
                device.flush()
            }
        }

        // First column of the rect (col 10) should be solid blue,
        // not half-covered : cov_x for an integer-edge rect at col 10
        // (center 10.5) is clamp(min(11, 30) - max(10, 10), 0, 1) = 1.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 15), "left edge : full coverage")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(29, 15), "right edge : full coverage")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(30, 15), "outside : col 30 not visited")
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
