package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkRect
import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect

/**
 * G2.x -- analytical clipPath for axis-aligned simple shapes (circle /
 * oval / uniform-corner rrect / rect) on the GPU backend. Cross-checks
 * a covering rect fill clipped to each shape against the CPU raster
 * reference. The four shapes route through [SkClipShape.tryDetect] in
 * the canvas, get pushed onto [SkWebGpuDevice.setActiveClipShape], and
 * are evaluated analytically in `solid_color.wgsl` (rrect coverage
 * formula -- circles and ovals reduce to rrects with uniform radii =
 * half-extents).
 *
 * The tolerance is loose (~3 channels) to absorb the half-pixel AA
 * band differences between the CPU's `SkAAClip` (band-encoded run-
 * length coverage) and the GPU's analytical `rrect_cov` formula on the
 * boundary pixels. Interior + exterior pixels should match exactly.
 */
class ClipPathShapeWebGpuTest {

    @Test
    fun `circle clip masks rect fill to circle interior`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            // 64x64 canvas, full-canvas rect fill clipped to a centred
            // circle of radius 20. Interior pixels = blue ; exterior
            // pixels = white.
            val rasterRgba = renderRaster { canvas ->
                canvas.clipPath(SkPath.Circle(32f, 32f, 20f), doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            val gpuRgba = renderGpu(ctx) { canvas ->
                canvas.clipPath(SkPath.Circle(32f, 32f, 20f), doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            // Sample 4 cardinal interior pixels and 4 cardinal exterior
            // pixels. The boundary band (radius ± 0.5 px) is excluded.
            assertSamplesBlue(gpuRgba, listOf(32 to 32, 22 to 32, 32 to 22, 42 to 32, 32 to 42))
            assertSamplesWhite(gpuRgba, listOf(5 to 5, 60 to 5, 5 to 60, 60 to 60))
            assertSimilarPixelCount(gpuRgba, rasterRgba, tolerance = 3)
        }
    }

    @Test
    fun `oval clip masks rect fill to oval interior`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val bounds = SkRect.MakeLTRB(8f, 16f, 56f, 48f) // 48 x 32 oval
            val rasterRgba = renderRaster { canvas ->
                canvas.clipPath(SkPath.Oval(bounds), doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            val gpuRgba = renderGpu(ctx) { canvas ->
                canvas.clipPath(SkPath.Oval(bounds), doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            // Centre + axis interior + far-corner exterior samples.
            assertSamplesBlue(gpuRgba, listOf(32 to 32, 16 to 32, 48 to 32, 32 to 24, 32 to 40))
            assertSamplesWhite(gpuRgba, listOf(2 to 2, 62 to 2, 2 to 62, 62 to 62))
            assertSimilarPixelCount(gpuRgba, rasterRgba, tolerance = 3)
        }
    }

    @Test
    fun `rrect clip masks rect fill with rounded corners`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(8f, 8f, 56f, 56f), 12f, 12f)
            val rasterRgba = renderRaster { canvas ->
                canvas.clipRRect(rrect, doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            val gpuRgba = renderGpu(ctx) { canvas ->
                canvas.clipRRect(rrect, doAntiAlias = false)
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            // Centre + straight-edge mid-points = inside ; rounded corners
            // = outside.
            assertSamplesBlue(gpuRgba, listOf(32 to 32, 32 to 12, 12 to 32, 52 to 32, 32 to 52))
            assertSamplesWhite(gpuRgba, listOf(9 to 9, 54 to 9, 9 to 54, 54 to 54))
            assertSimilarPixelCount(gpuRgba, rasterRgba, tolerance = 3)
        }
    }

    @Test
    fun `rotated CTM clipPath circle still throws (no shape capture)`() {
        // Sanity : SkClipShape.tryDetect bails when the CTM is not
        // axis-aligned (rotation / skew). The canvas-side fail-fast
        // for unsupported curved clips on a non-raster device must
        // therefore still fire. This regression-guards the "ZERO
        // regression on existing draws under unsupported clip ops"
        // contract.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val device = SkWebGpuDevice(ctx, W, H)
            try {
                val canvas = SkCanvas(device)
                canvas.rotate(15f)
                canvas.clipPath(SkPath.Circle(32f, 32f, 20f))
                try {
                    canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
                    org.junit.jupiter.api.Assertions.fail<Unit>(
                        "Expected curved clipPath under rotated CTM to fail-fast on " +
                            "SkWebGpuDevice -- detector bailed, aaClip is set, " +
                            "simpleShapeClip is null, bindClip must throw.",
                    )
                } catch (e: IllegalStateException) {
                    // Expected.
                    assertTrue(
                        e.message?.contains("does not support arbitrary clipPath") == true,
                        "Wrong exception message : ${e.message}",
                    )
                }
            } finally {
                device.close()
            }
        }
    }

    @Test
    fun `clipRect intersected with circle keeps the tighter shape`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        context!!.use { ctx ->
            val gpuRgba = renderGpu(ctx) { canvas ->
                // Compose : (rect clip) ∩ (circle clip). The rect's bbox
                // already lives in [SkCanvas.State.clip] ; the circle is
                // the analytic simple shape. Drawn rect should appear
                // only inside both -- here the rect (0..32, 0..32) ∩
                // circle (centre 32, r 20) = top-left quadrant of the
                // circle.
                canvas.clipRect(SkRect.MakeLTRB(0f, 0f, 32f, 32f))
                canvas.clipPath(SkPath.Circle(32f, 32f, 20f))
                canvas.drawRect(SkRect.MakeLTRB(0f, 0f, 64f, 64f), bluePaint())
            }
            // Inside the intersection (top-left quadrant near the centre).
            assertSamplesBlue(gpuRgba, listOf(25 to 25, 20 to 25, 25 to 20))
            // Outside the clipRect (bottom-right quadrant) -- masked.
            assertSamplesWhite(gpuRgba, listOf(40 to 40, 50 to 50, 40 to 20, 20 to 40))
            // Outside the circle (top-left corner of canvas, far from centre).
            assertSamplesWhite(gpuRgba, listOf(2 to 2, 5 to 5))
        }
    }

    private fun renderRaster(block: (SkCanvas) -> Unit): ByteArray {
        val bitmap = SkBitmap(W, H, colorType = SkColorType.kRGBA_8888).apply {
            eraseColor(SK_ColorWHITE)
        }
        val device = SkBitmapDevice(bitmap)
        val canvas = SkCanvas(device)
        block(canvas)
        return bitmap.pixels8888.toRgbaBytes()
    }

    private fun renderGpu(context: WebGpuContext, block: (SkCanvas) -> Unit): ByteArray =
        SkWebGpuDevice(context, W, H).use { device ->
            device.setBackground(SK_ColorWHITE)
            val canvas = SkCanvas(device)
            block(canvas)
            device.flush()
        }

    private fun bluePaint() = SkPaint().apply { color = SK_ColorBLUE }

    private fun assertSamplesBlue(rgba: ByteArray, samples: List<Pair<Int, Int>>) {
        for ((x, y) in samples) {
            val idx = (y * W + x) * 4
            val r = rgba[idx].toInt() and 0xFF
            val g = rgba[idx + 1].toInt() and 0xFF
            val b = rgba[idx + 2].toInt() and 0xFF
            assertTrue(
                r < 32 && g < 32 && b > 220,
                "Expected blue at ($x, $y) but got (R=$r, G=$g, B=$b)",
            )
        }
    }

    private fun assertSamplesWhite(rgba: ByteArray, samples: List<Pair<Int, Int>>) {
        for ((x, y) in samples) {
            val idx = (y * W + x) * 4
            val r = rgba[idx].toInt() and 0xFF
            val g = rgba[idx + 1].toInt() and 0xFF
            val b = rgba[idx + 2].toInt() and 0xFF
            assertTrue(
                r > 220 && g > 220 && b > 220,
                "Expected white at ($x, $y) but got (R=$r, G=$g, B=$b)",
            )
        }
    }

    /**
     * Compare GPU vs CPU outputs byte-by-byte ; allow [tolerance]
     * channel diff to absorb the half-pixel AA boundary band where the
     * CPU's `SkAAClip` and the GPU's `rrect_cov` use slightly different
     * coverage models. Counts non-matching pixels and asserts a tight
     * bound (boundary band of a 64x64 shape is at most ~200 pixels).
     */
    private fun assertSimilarPixelCount(a: ByteArray, b: ByteArray, tolerance: Int) {
        assertEquals(a.size, b.size, "byte buffer size mismatch")
        var differingPixels = 0
        for (i in 0 until a.size / 4) {
            val base = i * 4
            var maxDiff = 0
            for (c in 0 until 4) {
                val da = a[base + c].toInt() and 0xFF
                val db = b[base + c].toInt() and 0xFF
                val d = kotlin.math.abs(da - db)
                if (d > maxDiff) maxDiff = d
            }
            if (maxDiff > tolerance) differingPixels++
        }
        // Boundary band of a ~64-perimeter shape with 1-px AA is <= ~150 px.
        // Allow a generous bound to absorb the band differences.
        val totalPixels = a.size / 4
        val similarity = 100.0 * (totalPixels - differingPixels) / totalPixels
        assertTrue(
            similarity >= 92.0,
            "GPU vs raster similarity = ${"%.2f".format(similarity)}% < 92%, " +
                "differingPixels=$differingPixels / $totalPixels",
        )
    }

    private fun IntArray.toRgbaBytes(): ByteArray {
        val out = ByteArray(size * 4)
        for (i in indices) {
            val p = this[i]
            out[i * 4]     = ((p ushr 16) and 0xFF).toByte()
            out[i * 4 + 1] = ((p ushr 8)  and 0xFF).toByte()
            out[i * 4 + 2] = ((p)         and 0xFF).toByte()
            out[i * 4 + 3] = ((p ushr 24) and 0xFF).toByte()
        }
        return out
    }

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
    }
}
