package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.PI

/**
 * Port of Skia's
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GM(runtime_intrinsics_trig)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * Lays out a 3-column × 5-row grid of unary trig intrinsics. Each
 * cell : (1) draws a centred label (`fn` text), (2) renders the
 * SkSL `make_unary_sksl_1d(fn, false)` runtime shader into a
 * 100×100 off-screen surface scaled so the shader's `p` parameter
 * sweeps `[0, 1)²`, (3) plots a green polyline through the top
 * row of pixels — visualising `y(x)` for x linearly mapped to
 * `[xMin, xMax]` and y mapped from `[yMin, yMax]` to
 * `[0, kBoxSize]` (1 = bottom, 0 = top).
 *
 * Built on the [SkBuiltinShaderEffectsIntrinsicsTrig] cluster
 * (Phase D2.4.c.1) — the registry has 12 SkSL hash entries
 * (radians / degrees / sin / cos / tan / asin / acos / atan(x) /
 * atan(0.1, x) / atan(-0.1, x) / atan(x, 0.1) / atan(x, -0.1))
 * each pointing to a [SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * carrying the matching `kotlin.math` impl.
 *
 * **Known drift sources** (vs `runtime_intrinsics_trig.png`) :
 *  - Text labels — AWT-vs-FreeType glyph drift (~3-5 % canvas).
 *  - Working colour space — our render goes through a sRGB
 *    100×100 sub-surface then composites onto a Rec.2020 parent.
 *  - Polyline AA — our scanline 4×4 supersampling vs Skia's
 *    analytical AA.
 *
 * The floor is set low (~5 %) since the drift sources accumulate
 * across 12 cells ; the ratchet still catches regressions of any
 * single intrinsic's math.
 */
public class RuntimeIntrinsicsTrigGM : GM() {

    override fun getName(): String = "runtime_intrinsics_trig"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(3),
        rowsToHeight(5),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val piTwo = (2.0 * PI).toFloat()
        val piHalf = (PI / 2.0).toFloat()
        val piF = PI.toFloat()

        c.translate(kPadding.toFloat(), kPadding.toFloat())
        c.save()

        plot(c, "radians(x)", 0f, 360f, 0f, piTwo)
        plot(c, "degrees(x)", 0f, piTwo, 0f, 360f)
        nextRow(c)

        plot(c, "sin(x)", 0f, piTwo, -1f, 1f)
        plot(c, "cos(x)", 0f, piTwo, -1f, 1f)
        plot(c, "tan(x)", 0f, piF, -10f, 10f)
        nextRow(c)

        plot(c, "asin(x)", -1f, 1f, -piHalf, piHalf)
        plot(c, "acos(x)", -1f, 1f, 0f, piF)
        plot(c, "atan(x)", -10f, 10f, -piHalf, piHalf)
        nextRow(c)

        plot(c, "atan(0.1,  x)", -1f, 1f, 0f, piF)
        plot(c, "atan(-0.1, x)", -1f, 1f, -piF, 0f)
        nextRow(c)

        plot(c, "atan(x,  0.1)", -1f, 1f, -piHalf, piHalf)
        plot(c, "atan(x, -0.1)", -1f, 1f, -piF, piF)
        nextRow(c)
    }

    /**
     * Render a single intrinsic cell. Mirrors upstream's
     * `static void plot(canvas, fn, xMin, xMax, yMin, yMax,
     * label, requireES3)` — `label` is null (use `fn` as the
     * label) and `requireES3` is false (the trig cluster is all
     * ES2-compatible).
     */
    private fun plot(c: SkCanvas, fn: String, xMin: Float, xMax: Float, yMin: Float, yMax: Float) {
        c.save()
        drawLabel(c, fn)

        val sksl = SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d(fn, requireES3 = false)
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect
            ?: error("Unable to compile runtime shader : $fn")
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("xScale").set(xMax - xMin)
        builder.uniform("xBias").set(xMin)
        builder.uniform("yScale").set(1f / (yMax - yMin))
        builder.uniform("yBias").set(-yMin / (yMax - yMin))

        val shader = builder.makeShader()
            ?: error("Unable to build shader from effect : $fn")

        val info = SkImageInfo.MakeN32Premul(kBoxSize, kBoxSize)
        val surface = SkSurface.MakeRaster(info)
        val sc = surface.canvas
        sc.clear(SK_ColorWHITE)
        sc.scale(kBoxSize.toFloat(), kBoxSize.toFloat())
        val paint = SkPaint().apply { this.shader = shader }
        sc.drawRect(SkRect.MakeWH(1f, 1f), paint)

        val img = surface.makeImageSnapshot()
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, null)

        // Plot the green polyline through the top row of the
        // rendered image. The shader's `y` is broadcast across
        // RGB ; we use the red channel as the scalar back-out
        // (matches upstream).
        val plotPaint = SkPaint().apply {
            color4f = SkColor4f(0f, 0.5f, 0f, 1f)
            isAntiAlias = true
        }
        val pts = Array(kBoxSize) { SkPoint(0f, 0f) }
        for (x in 0 until kBoxSize) {
            val pixel = img.peekPixel(x, 0)
            val r = SkColorGetR(pixel) / 255f
            val y = (1f - r) * kBoxSize.toFloat()
            pts[x] = SkPoint(x + 0.5f, y)
        }
        c.drawPoints(SkCanvas.PointMode.kPolygon, pts, plotPaint)

        c.restore()
        nextColumn(c)
    }

    /** Mirrors upstream's `draw_label(canvas, label)`. Centres
     *  [label] horizontally, vertically aligns within
     *  [kLabelHeight], then translates the canvas down by
     *  [kLabelHeight] so the next draw lands below the label. */
    private fun drawLabel(c: SkCanvas, label: String) {
        val font: SkFont = ToolUtils.DefaultPortableFont(12f)
        val labelPaint = SkPaint().apply { color4f = SkColor4f(0f, 0f, 0f, 1f) }
        val bounds = SkRect.MakeEmpty()
        font.measureText(
            text = label,
            byteLength = label.length,
            encoding = SkTextEncoding.kUTF8,
            bounds = bounds,
        )
        c.drawSimpleText(
            text = label,
            byteLength = label.length,
            encoding = SkTextEncoding.kUTF8,
            x = (kBoxSize - bounds.width()) * 0.5f,
            y = (kLabelHeight + bounds.height()) * 0.5f,
            font = font,
            paint = labelPaint,
        )
        c.translate(0f, kLabelHeight.toFloat())
    }

    /** Advances the canvas to the next column. */
    private fun nextColumn(c: SkCanvas) {
        c.translate((kBoxSize + kPadding).toFloat(), 0f)
    }

    /** Restores the row-save, advances to the next row, and re-saves. */
    private fun nextRow(c: SkCanvas) {
        c.restore()
        c.translate(0f, (kBoxSize + kPadding + kLabelHeight).toFloat())
        c.save()
    }

    private companion object {
        const val kBoxSize: Int = 100
        const val kPadding: Int = 5
        const val kLabelHeight: Int = 15

        fun columnsToWidth(columns: Int): Int = (kPadding + kBoxSize) * columns + kPadding
        fun rowsToHeight(rows: Int): Int = (kPadding + kLabelHeight + kBoxSize) * rows + kPadding
    }
}
