package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import kotlin.math.abs

/**
 * G3.3b.2a acceptance test — analytical-coverage AA polygon fill on
 * the GPU. The shader iterates per-fragment over the polygon's
 * perimeter edge equations and computes
 * `coverage = min over edges of clamp(signed_dist + 0.5, 0, 1)`.
 *
 * Tests a half-integer-edge square. Expected math at the edges :
 *   edge pixel    -> 1 axis half-covered, other full -> coverage = 0.5
 *   corner pixel  -> 2 axes half-covered             -> coverage = 0.5
 *     (min(0.5, 0.5) = 0.5 — NOT 0.25 like the rect AA's product, since
 *      the polygon AA uses min-over-edges not product-over-axes)
 */
class AaPolygonFillTest {

    @Test
    fun `aa polygon with half-integer edges yields fractional coverage on edge pixels`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        // Square polygon at (10.5, 10.5)-(30.5, 30.5) — same half-integer
        // edges as AaRectFillTest's first case, but routed through
        // drawPath -> AA polygon pipeline.
        val path = SkPathBuilder()
            .moveTo(10.5f, 10.5f)
            .lineTo(30.5f, 10.5f)
            .lineTo(30.5f, 30.5f)
            .lineTo(10.5f, 30.5f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Far outside the polygon : white.
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(50, 50), "outside : white bg")

        // Interior pixel : opaque blue (all edges report coverage 1).
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(15, 15), "interior : opaque blue")

        // Left-edge pixel (10, 15) : the left edge bisects this pixel
        // (pixel-center x=10.5 is exactly on the rect's left edge).
        //   coverage_left = clamp(0 + 0.5, 0, 1) = 0.5
        //   other edges far from this pixel -> coverage 1
        //   min = 0.5
        //   premul src = (0, 0, 0.5, 0.5) ; SrcOver on white = (0.5, 0.5, 1, 1)
        //   bytes (+/- 1)                                    = (128, 128, 255, 255)
        assertNear(listOf(128, 128, 255, 255), pixels.rgbaAt(10, 15), 1, "left edge half-coverage")
        assertNear(listOf(128, 128, 255, 255), pixels.rgbaAt(30, 15), 1, "right edge half-coverage")
        assertNear(listOf(128, 128, 255, 255), pixels.rgbaAt(15, 10), 1, "top edge half-coverage")

        // Corner pixel : 2 axes at distance 0 -> both have coverage 0.5.
        // Polygon AA uses MIN over edges (not product), so coverage = 0.5,
        // NOT 0.25 like the rect-AA product formulation. This is a
        // deliberate divergence from the rect-AA convention -- analytical
        // edge coverage for polygons is per-edge, not per-axis.
        assertNear(listOf(128, 128, 255, 255), pixels.rgbaAt(10, 10), 1, "corner pixel (polygon min, not rect product)")
    }

    @Test
    fun `aa polygon with integer edges yields full coverage everywhere inside`() {
        // Sanity : an AA polygon with integer-edge geometry should look
        // like a non-AA fill (coverage = 1 everywhere inside).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path = SkPathBuilder()
            .moveTo(10f, 10f)
            .lineTo(30f, 10f)
            .lineTo(30f, 30f)
            .lineTo(10f, 30f)
            .close()
            .detach()
        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            isAntiAlias = true
        }

        val pixels = context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                SkCanvas(device).drawPath(path, paint)
                device.flush()
            }
        }

        // Interior + first-column pixel both fully covered : pixel-center
        // is at .5, perpendicular distance to integer edges is at least
        // 0.5 -> clamp(0.5 + 0.5, 0, 1) = 1.0.
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(10, 15), "col 10 : full coverage")
        assertEquals(listOf(0, 0, 255, 255), pixels.rgbaAt(29, 15), "col 29 : full coverage")
        assertEquals(listOf(255, 255, 255, 255), pixels.rgbaAt(30, 15), "col 30 : outside polygon")
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
                "$label channel $c : expected ${expected[c]} +/- $tolerance, got ${actual[c]} " +
                    "(full pixel expected=$expected, actual=$actual)",
            )
        }
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
