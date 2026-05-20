package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkCornerPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * i3 acceptance tests -- `paint.pathEffect = SkCornerPathEffect`
 * dispatch on `drawPath`.
 *
 * Mirrors the H5 ([PathEffectDashTest]) pattern. The corner effect
 * rounds the polygon's sharp vertices into quadratic arcs of the
 * given radius. We verify the GPU device routes through
 * [org.skia.foundation.SkCornerPathEffect.filterPath] before the
 * stroker by checking that the rounded silhouette differs from the
 * raw stroked polygon outside the corner-pullback zones (vertex
 * pixels are no longer hit by the silhouette) while mid-edge pixels
 * remain untouched.
 *
 * Uses `strokeWidth = 2f` (not hairline) so corner / edge pixels
 * have predictable integer positions independent of hairline
 * rasterisation rounding.
 */
class PathEffectCornerTest {

    @Test
    fun `cornered square stroke leaves the original sharp-corner pixel unpainted`() {
        // 24x24 outline at [16, 16] - [40, 40], strokeWidth = 2,
        // cornerRadius = 8. With sharp corners the top-left corner
        // pixel block around (16, 16) is painted. With the corner
        // effect, the path becomes `lineTo(24, 16) -> quadTo(16, 16 ;
        // 16, 24)` which curves smoothly inside the original corner ;
        // the corner pixel (16, 16) is well outside the rounded arc
        // band and stays unpainted.
        val sharpContext = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(sharpContext != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(16f, 16f)
            .lineTo(40f, 16f)
            .lineTo(40f, 40f)
            .lineTo(16f, 40f)
            .close()
            .detach()

        val sharp = renderToPixels(sharpContext!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2f
                isAntiAlias = false
            }
            canvas.drawPath(path, p)
        }

        val roundedContext = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(roundedContext != null, "No WebGPU adapter")
        val rounded = renderToPixels(roundedContext!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2f
                isAntiAlias = false
                pathEffect = SkCornerPathEffect.Make(8f)
            }
            canvas.drawPath(path, p)
        }

        // Sharp stroke paints the top-left corner pixel band.
        val sharpAtCorner = sharp.hasBluePixelIn(15, 15, 17, 17)
        assertTrue(sharpAtCorner, "sharp stroke must paint at the top-left corner")

        // Rounded stroke pulls back by 8 px from each edge so the
        // corner pixel at (16, 16) is well outside the smoothing arc.
        val roundedAtCorner = rounded.hasBluePixelIn(15, 15, 16, 16)
        assertTrue(
            !roundedAtCorner,
            "rounded stroke must NOT paint at the original sharp-corner pixel (16, 16)",
        )

        // And the two outputs must not be byte-identical.
        var diff = false
        for (i in sharp.indices) {
            if (sharp[i] != rounded[i]) { diff = true; break }
        }
        assertTrue(diff, "rounded output must differ from sharp output")
    }

    @Test
    fun `corner effect leaves mid-edge pixels painted`() {
        // The mid-edge pixel is NOT a corner, so the rounded version
        // still paints there. Sanity check that the effect didn't
        // accidentally erase the whole stroke (e.g. by returning an
        // empty path or by dropping the recursion).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(16f, 16f)
            .lineTo(40f, 16f)
            .lineTo(40f, 40f)
            .lineTo(16f, 40f)
            .close()
            .detach()

        val pixels = renderToPixels(context!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2f
                isAntiAlias = false
                pathEffect = SkCornerPathEffect.Make(8f)
            }
            canvas.drawPath(path, p)
        }

        // Top edge mid-point is around x = 28 (rect [16, 40], so
        // mid-edge band 24..32 is outside the 8-px pullback zone from
        // either corner). The 2-px stroke straddles y = 15-17.
        val midPainted = pixels.hasBluePixelIn(27, 15, 29, 17)
        assertTrue(midPainted, "mid-edge pixel band near (28, 16) must remain painted")
    }

    /**
     * Returns true if any pixel in the inclusive rectangle
     * `[x0..x1] x [y0..y1]` has the BLUE colour.
     */
    private fun ByteArray.hasBluePixelIn(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (rgbaAt(x, y) == BLUE) return true
            }
        }
        return false
    }

    /**
     * Render a single drawing closure on a fresh [SkWebGpuDevice] backed
     * by [context]. The context is consumed (its `.use{}` block runs to
     * completion). Returns the device's pixel readback as RGBA8 bytes.
     */
    private fun renderToPixels(
        context: WebGpuContext,
        draw: (SkCanvas) -> Unit,
    ): ByteArray = context.use { ctx ->
        SkWebGpuDevice(ctx, W, H).use { device ->
            device.setBackground(SK_ColorWHITE)
            draw(SkCanvas(device))
            device.flush()
        }
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

    private companion object {
        const val W: Int = 64
        const val H: Int = 64
        val BLUE: List<Int> = listOf(0, 0, 255, 255)
        val WHITE: List<Int> = listOf(255, 255, 255, 255)
    }
}
