package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorGetR
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Layout / rendering helpers shared by every
 * `RuntimeIntrinsics<Group>GM` port that consumes the
 * `make_unary_sksl_1d` skeleton (D2.4.c.1 trig, D2.4.c.2
 * exponential, D2.4.c.3 common, D2.4.c.4 geometric).
 *
 * Mirrors upstream's `gm/runtimeintrinsics.cpp` static helpers
 * (`kBoxSize` / `kPadding` / `kLabelHeight` / `next_column` /
 * `next_row` / `draw_label` / `plot`).
 *
 * **Why a separate file** : 4 GM ports share the same plumbing
 * (3-row remap → render shader to 100×100 sub-surface → drawImage →
 * polyline overlay) ; centralising avoids per-GM duplication. The
 * matrix (D2.4.c.5) and relational (D2.4.c.6) GMs each carry their
 * own SkSL template + plot helper, so they don't reuse this file.
 */
internal object RuntimeIntrinsicsPlotHelper {

    /** Cell side length — matches upstream `kBoxSize = 100`. */
    const val kBoxSize: Int = 100

    /** Spacing between cells — matches upstream `kPadding = 5`. */
    const val kPadding: Int = 5

    /** Vertical room reserved for each cell's centred label —
     *  matches upstream `kLabelHeight = 15`. */
    const val kLabelHeight: Int = 15

    /** Total canvas width for [columns] cells side-by-side. */
    fun columnsToWidth(columns: Int): Int =
        (kPadding + kBoxSize) * columns + kPadding

    /** Total canvas height for [rows] rows of cells. */
    fun rowsToHeight(rows: Int): Int =
        (kPadding + kLabelHeight + kBoxSize) * rows + kPadding

    /**
     * Render a single intrinsic cell. Mirrors upstream's static
     * `plot(canvas, fn, xMin, xMax, yMin, yMax, label?, requireES3)` :
     *
     * 1. Centre [label] (or [fn] if null) horizontally above the cell.
     * 2. Build the SkSL via [SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d]
     *    and resolve the effect. Bind the four `xScale`/`xBias`/
     *    `yScale`/`yBias` uniforms from the supplied `(xMin, xMax,
     *    yMin, yMax)` plot range.
     * 3. Render the resulting shader into a 100×100 sub-surface
     *    (sRGB, MakeN32Premul) and `drawImage` that snapshot onto
     *    the parent canvas at `(0, 0)`.
     * 4. Re-read the top row of the rendered image and plot a green
     *    polyline through the back-decoded `y` values.
     * 5. `nextColumn` to advance.
     */
    fun plot(
        c: SkCanvas,
        fn: String,
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float,
        label: String? = null,
    ) {
        c.save()
        drawLabel(c, label ?: fn)

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

        // Plot — same convention as upstream : sample row 0, decode
        // R / 255 as the normalised y, plot `(x + 0.5, (1 - y) * box)`.
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

    /** Centred label — mirrors upstream `draw_label(canvas, label)`. */
    fun drawLabel(c: SkCanvas, label: String) {
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

    fun nextColumn(c: SkCanvas) {
        c.translate((kBoxSize + kPadding).toFloat(), 0f)
    }

    fun nextRow(c: SkCanvas) {
        c.restore()
        c.translate(0f, (kBoxSize + kPadding + kLabelHeight).toFloat())
        c.save()
    }

    /**
     * Render a "shader cell" — no polyline overlay, just the
     * shader output drawn into a 100×100 sub-surface and
     * composited onto the parent canvas. Used by the matrix and
     * relational GM ports (D2.4.c.5 / D2.4.c.6) where the visual
     * is a 2D colour map of the SkSL output, not a 1D function plot.
     *
     * Mirrors upstream's `draw_shader(canvas, shader)` followed by
     * a `next_column` advance, with the label centred above by
     * [drawLabel] (mirroring upstream's `plot_matrix_comp_mult` /
     * `plot_matrix_inverse` / `plot_bvec`).
     *
     * @param sksl canonical SkSL source of the runtime effect.
     * @param label text drawn centred above the cell.
     * @param configure callback invoked with the
     *   [SkRuntimeEffectBuilder] to bind the effect's uniforms /
     *   children before [SkRuntimeEffectBuilder.makeShader] is
     *   called.
     */
    fun drawShaderCell(
        c: SkCanvas,
        label: String,
        sksl: String,
        configure: (org.skia.effects.runtime.SkRuntimeEffectBuilder) -> Unit,
    ) {
        c.save()
        drawLabel(c, label)

        val effect = org.skia.effects.runtime.SkRuntimeEffect.MakeForShader(sksl).effect
            ?: error("Unable to compile runtime shader : $label")
        val builder = org.skia.effects.runtime.SkRuntimeEffectBuilder(effect)
        configure(builder)
        val shader = builder.makeShader()
            ?: error("Unable to build shader from effect : $label")

        val info = SkImageInfo.MakeN32Premul(kBoxSize, kBoxSize)
        val surface = SkSurface.MakeRaster(info)
        val sc = surface.canvas
        sc.clear(SK_ColorWHITE)
        sc.scale(kBoxSize.toFloat(), kBoxSize.toFloat())
        val paint = SkPaint().apply { this.shader = shader }
        sc.drawRect(SkRect.MakeWH(1f, 1f), paint)

        val img = surface.makeImageSnapshot()
        c.drawImage(img, 0f, 0f, SkSamplingOptions.Default, null)

        c.restore()
        nextColumn(c)
    }
}
