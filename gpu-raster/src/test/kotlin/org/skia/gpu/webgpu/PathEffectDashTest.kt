package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkCornerPathEffect
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * H5 acceptance tests -- `paint.pathEffect` dispatch on `drawPath`.
 *
 * Before H5 the GPU device silently dropped `paint.pathEffect`. This
 * slice routes the path through [org.skia.foundation.SkPathEffect.filterPath]
 * before the stroker, with [SkDashPathEffect] as the first supported
 * subtype ; all other variants throw a deferred-error.
 *
 * Layout : 64 x 32. A horizontal line at y = 16 from x = 4 to x = 60
 * (length = 56 px) is stroked with a hairline blue paint. With dash
 * intervals `[8, 8]` the line decomposes into 7 dashes on / 8 px off /
 * 7 dashes on / ... -> blue bands and white gaps along the row.
 *
 * Each test spins up a fresh [WebGpuContext] -- the context is
 * single-shot ([AutoCloseable]) and can't be reused across `.use{}`
 * blocks.
 */
class PathEffectDashTest {

    @Test
    fun `dashed hairline alternates blue and white pixels along the line`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
            pathEffect = SkDashPathEffect.Make(floatArrayOf(8f, 8f), 0f)
        }

        val pixels = renderToPixels(context!!) { canvas -> canvas.drawPath(path, paint) }

        // First dash : pixels at x = 4..11 (length 8) are blue, on row 16
        // or 15 (hairline non-AA rounding). Sample inside that range.
        val onMid = pixels.rgbaAt(6, 16) == BLUE || pixels.rgbaAt(6, 15) == BLUE
        assertTrue(onMid, "expected blue pixel inside first dash near x = 6")
        // First gap : pixels at x = 12..19 should remain white.
        assertEquals(WHITE, pixels.rgbaAt(15, 16), "gap at x = 15 stays white")
        assertEquals(WHITE, pixels.rgbaAt(15, 15), "gap at x = 15 row 15 stays white")
        // Second dash : pixels at x = 20..27 are blue.
        val onSecond = pixels.rgbaAt(23, 16) == BLUE || pixels.rgbaAt(23, 15) == BLUE
        assertTrue(onSecond, "expected blue pixel inside second dash near x = 23")
        // Second gap : x = 28..35 white.
        assertEquals(WHITE, pixels.rgbaAt(31, 16), "second gap stays white")
        // Rows well off the path : background.
        assertEquals(WHITE, pixels.rgbaAt(6, 5), "5+ rows above stays white")
        assertEquals(WHITE, pixels.rgbaAt(6, 26), "10 rows below stays white")
    }

    @Test
    fun `phase shifts the dash pattern start`() {
        // Same line but phase = 8 -- the cycle starts at the boundary
        // between the first "on" and the first "off", so the line begins
        // with a GAP (8 px white) then dashes.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
            pathEffect = SkDashPathEffect.Make(floatArrayOf(8f, 8f), 8f)
        }

        val pixels = renderToPixels(context!!) { canvas -> canvas.drawPath(path, paint) }

        // With phase = 8, the line starts in the "off" interval.
        // First 8 px (x = 4..11) should be a gap -> white.
        assertEquals(WHITE, pixels.rgbaAt(6, 16), "phase=8 starts with a gap (x=6 white)")
        assertEquals(WHITE, pixels.rgbaAt(6, 15), "phase=8 starts with a gap (x=6 row 15 white)")
        // Then x = 12..19 should be a dash -> blue somewhere.
        val onAfterPhase = pixels.rgbaAt(15, 16) == BLUE || pixels.rgbaAt(15, 15) == BLUE
        assertTrue(
            onAfterPhase,
            "expected blue pixel inside phase-shifted dash near x = 15",
        )
    }

    @Test
    fun `degenerate dash intervals draw nothing`() {
        // intervals = [0, 0] -> totalCycle = 0 -> filterPath returns an
        // empty path. The GPU drawPath must detect the empty result and
        // bail (matches CPU behaviour at SkBitmapDevice.drawPath:1099).
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
            pathEffect = SkDashPathEffect.Make(floatArrayOf(0f, 0f), 0f)
        }

        val pixels = renderToPixels(context!!) { canvas -> canvas.drawPath(path, paint) }

        // Every sampled pixel along the supposed line must remain white.
        for (x in 4 until 60 step 7) {
            assertEquals(WHITE, pixels.rgbaAt(x, 16), "degenerate dash drew at x=$x")
            assertEquals(WHITE, pixels.rgbaAt(x, 15), "degenerate dash drew at x=$x row 15")
        }
    }

    @Test
    fun `non-Dash path effect throws a clear deferred error`() {
        // SkCornerPathEffect is implemented at the foundation level but
        // not wired into the GPU dispatch this slice -- the drawPath hook
        // must throw a clear deferred-error rather than silently mis-
        // render. Same applies to Discrete / Compose / Sum / 1D / 2D.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val path: SkPath = SkPathBuilder()
            .moveTo(8f, 8f)
            .lineTo(24f, 8f)
            .lineTo(24f, 24f)
            .lineTo(8f, 24f)
            .close()
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
            pathEffect = SkCornerPathEffect.Make(4f)
        }

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val ex = assertThrows(IllegalStateException::class.java) {
                    canvas.drawPath(path, paint)
                }
                assertTrue(
                    ex.message?.contains("SkCornerPathEffect") == true,
                    "exception must name the unsupported variant ; got: ${ex.message}",
                )
                assertTrue(
                    ex.message?.contains("SkDashPathEffect") == true,
                    "exception must mention the supported variant ; got: ${ex.message}",
                )
            }
        }
    }

    @Test
    fun `dashed line differs from solid stroke at gap positions`() {
        // Cross-check : confirm the dashed-line test would actually fail
        // if the GPU silently dropped the pathEffect (pre-H5 behaviour).
        // A solid stroke produces a continuous blue line ; the dashed
        // version must have at least one fully-white pixel in the middle
        // of an "off" interval to prove the effect ran.
        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val solidContext = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(solidContext != null, "No WebGPU adapter")
        val solid = renderToPixels(solidContext!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
            }
            canvas.drawPath(path, p)
        }

        val dashedContext = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(dashedContext != null, "No WebGPU adapter")
        val dashed = renderToPixels(dashedContext!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
                pathEffect = SkDashPathEffect.Make(floatArrayOf(8f, 8f), 0f)
            }
            canvas.drawPath(path, p)
        }

        // Pixel (15, 16) / (15, 15) is in the FIRST gap for dashed,
        // but BLUE for the solid stroke.
        val solidOnAtGap = solid.rgbaAt(15, 16) == BLUE || solid.rgbaAt(15, 15) == BLUE
        assertTrue(solidOnAtGap, "solid stroke must paint x=15 on the path")
        val dashedGap =
            dashed.rgbaAt(15, 16) == WHITE && dashed.rgbaAt(15, 15) == WHITE
        assertTrue(dashedGap, "dashed stroke must leave x=15 unpainted (gap)")
        // And the two outputs must not be byte-identical.
        var diff = false
        for (i in solid.indices) {
            if (solid[i] != dashed[i]) { diff = true; break }
        }
        assertTrue(diff, "dashed output must differ from solid output")
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
        const val H: Int = 32
        val BLUE: List<Int> = listOf(0, 0, 255, 255)
        val WHITE: List<Int> = listOf(255, 255, 255, 255)
    }
}
