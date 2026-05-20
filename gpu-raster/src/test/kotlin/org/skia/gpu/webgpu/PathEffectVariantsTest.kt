package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.core.SkCanvas
import org.skia.foundation.SkCornerPathEffect
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPath1DPathEffect
import org.skia.foundation.SkPath2DPathEffect
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathEffect
import org.skia.tools.SkDiscretePathEffect

/**
 * II1 acceptance tests -- extend `paint.pathEffect` dispatch on
 * `drawPath` to cover [SkDiscretePathEffect], [SkComposePathEffect]
 * via [SkPathEffect.MakeCompose] and [SkSumPathEffect] via
 * [SkPathEffect.MakeSum].
 *
 * Pattern : all three variants share the recurse-with-`pathEffect=null`
 * dispatch already used for Dash (#583). Tests verify each effect runs
 * (output differs from a solid stroke) without crashing the pipeline.
 *
 * Layout : 64 x 32. A horizontal line at y = 16 from x = 4 to x = 60
 * is stroked with a hairline blue paint.
 */
class PathEffectVariantsTest {

    @Test
    fun `discrete path effect jitters the polyline output`() {
        // Discrete with segLength = 10, deviation = 2 on a 56 px line
        // produces a noisy polyline. The output must NOT be byte-identical
        // to the solid stroke (the jitter perturbs at least one vertex
        // off the baseline row).
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

        val discreteContext = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(discreteContext != null, "No WebGPU adapter")
        val discrete = renderToPixels(discreteContext!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
                pathEffect = SkDiscretePathEffect.Make(10f, 2f, seed = 42)
            }
            canvas.drawPath(path, p)
        }

        // The two outputs must differ -- the jitter perturbs at least one
        // pixel off the y = 16 / y = 15 hairline row.
        var diff = false
        for (i in solid.indices) {
            if (solid[i] != discrete[i]) { diff = true; break }
        }
        assertTrue(diff, "discrete path effect must produce output that differs from a solid stroke")
    }

    @Test
    fun `discrete path effect with zero deviation degenerates to identity`() {
        // deviation = 0 -- the jitter math reduces to "emit chord vertices
        // along the original line". The stroked output should be visually
        // equivalent to a plain stroke (every pixel on the baseline blue,
        // every pixel off the baseline white).
        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val pixels = renderToPixels(context!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
                pathEffect = SkDiscretePathEffect.Make(10f, 0f, seed = 7)
            }
            canvas.drawPath(path, p)
        }

        // Sample a pixel mid-line -- must be blue.
        val onLine = pixels.rgbaAt(30, 16) == BLUE || pixels.rgbaAt(30, 15) == BLUE
        assertTrue(onLine, "deviation=0 must still draw the line (blue near x=30)")
        // Sample well off the line -- must be white.
        assertEquals(WHITE, pixels.rgbaAt(30, 5), "row 5 far from the line stays white")
    }

    @Test
    fun `compose path effect chains dash inside discrete`() {
        // Outer = Discrete (jitter the dashed output) ; inner = Dash
        // (decompose into stipples). The chain must run without crashing
        // and produce a non-empty image distinct from a plain stroke.
        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val outer = SkDiscretePathEffect.Make(6f, 1f, seed = 13)
        val inner = SkDashPathEffect.Make(floatArrayOf(8f, 8f), 0f)
        val compose = SkPathEffect.MakeCompose(outer, inner)
        assertTrue(compose != null, "MakeCompose must return a non-null effect for two non-null operands")

        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val composed = renderToPixels(context!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
                pathEffect = compose
            }
            canvas.drawPath(path, p)
        }

        // The composed output must contain at least one blue pixel
        // (something got drawn) and at least one white pixel inside the
        // line bounds (a gap from the inner dash effect made it through).
        var anyBlue = false
        var gapInsideLine = false
        for (x in 4 until 60) {
            for (y in 14..17) {
                val px = composed.rgbaAt(x, y)
                if (px == BLUE) anyBlue = true
                if (x in 12..19 && px == WHITE) gapInsideLine = true
            }
        }
        assertTrue(anyBlue, "compose(Discrete, Dash) must emit blue pixels somewhere on the line")
        assertTrue(gapInsideLine, "compose(Discrete, Dash) must preserve the dash gap interior")
    }

    @Test
    fun `sum path effect combines both branches`() {
        // SkPathEffect.MakeSum(Dash, Discrete) applies BOTH effects to the
        // input independently and concatenates the verb streams. Output
        // contains the dashed segments AND the jittered polyline.
        val path: SkPath = SkPathBuilder()
            .moveTo(4f, 16f)
            .lineTo(60f, 16f)
            .detach()

        val first = SkDashPathEffect.Make(floatArrayOf(8f, 8f), 0f)
        val second = SkDiscretePathEffect.Make(10f, 2f, seed = 99)
        val sum = SkPathEffect.MakeSum(first, second)
        assertTrue(sum != null, "MakeSum must return non-null for two non-null operands")

        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")
        val summed = renderToPixels(context!!) { canvas ->
            val p = SkPaint().apply {
                color = SK_ColorBLUE
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
                isAntiAlias = false
                pathEffect = sum
            }
            canvas.drawPath(path, p)
        }

        // The sum output must paint at least one pixel inside what would
        // be a dash gap (because the discrete branch covers the whole
        // line) AND must paint near the start of the line.
        val startBlue = summed.rgbaAt(6, 16) == BLUE || summed.rgbaAt(6, 15) == BLUE
        assertTrue(startBlue, "sum(Dash, Discrete) must paint near the start of the line")
        // Pixel inside the would-be first dash gap (x in 12..19) -- the
        // discrete branch should cover it (jittered or not).
        var insideGapBlue = false
        for (x in 12..19) {
            for (y in 13..18) {
                if (summed.rgbaAt(x, y) == BLUE) { insideGapBlue = true; break }
            }
            if (insideGapBlue) break
        }
        assertTrue(insideGapBlue, "sum(Dash, Discrete) must paint inside the dash gap (discrete branch fills it)")
    }

    @Test
    fun `MakeCompose with null operand short-circuits to the other`() {
        // Skia-iso null-handling : MakeCompose(null, x) returns x, so the
        // dispatch only sees the non-null leaf -- here, Discrete.
        val inner = SkDiscretePathEffect.Make(10f, 1f, seed = 1)
        val composed = SkPathEffect.MakeCompose(outer = null, inner = inner)
        assertNotEquals(null, composed)
        // The returned effect is `inner` itself -- the dispatch will match
        // SkDiscretePathEffect, not SkComposePathEffect.
        assertTrue(
            composed is SkDiscretePathEffect,
            "MakeCompose(null, x) must return x unchanged ; got ${composed!!::class.simpleName}",
        )
    }

    @Test
    fun `1D path effect still throws a clear deferred error`() {
        // SkPath1DPathEffect remains out of scope for II1 -- the dispatch
        // must throw a clear error rather than silently mis-render.
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(context != null, "No WebGPU adapter")

        val stamp: SkPath = SkPathBuilder()
            .moveTo(0f, 0f)
            .lineTo(4f, 0f)
            .lineTo(4f, 4f)
            .lineTo(0f, 4f)
            .close()
            .detach()
        val pe = SkPath1DPathEffect.Make(
            stamp,
            advance = 8f,
            phase = 0f,
            style = SkPath1DPathEffect.Style.kTranslate,
        )
        Assumptions.assumeTrue(pe != null, "SkPath1DPathEffect.Make returned null ; cannot test")

        val path: SkPath = SkPathBuilder()
            .moveTo(8f, 16f)
            .lineTo(56f, 16f)
            .detach()

        val paint = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            isAntiAlias = false
            pathEffect = pe
        }

        context!!.use { ctx ->
            SkWebGpuDevice(ctx, W, H).use { device ->
                device.setBackground(SK_ColorWHITE)
                val canvas = SkCanvas(device)
                val ex = assertThrows(IllegalStateException::class.java) {
                    canvas.drawPath(path, paint)
                }
                assertTrue(
                    ex.message?.contains("SkPath1DPathEffect") == true,
                    "exception must name the unsupported variant ; got: ${ex.message}",
                )
                assertTrue(
                    ex.message?.contains("deferred") == true,
                    "exception must mention deferred status ; got: ${ex.message}",
                )
            }
        }
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
