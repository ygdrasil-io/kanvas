package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of upstream `gm/cubicpaths.cpp` `CubicPathGM`
 * (`DEF_GM(return new CubicPathGM)`, 1240 × 390 canvas).
 *
 * First GM ported on top of the **`kInverse*` rasterizer** unblocked
 * by Phase 3.8.
 *
 * Renders a single cubic path
 * `moveTo(25, 10)  cubicTo(40, 20, 60, 20, 75, 10)`
 * 36 times across a 4 × 3 × 3 grid that varies independently:
 *  - **fill rule** (rows): `kWinding`, `kEvenOdd`, `kInverseWinding`,
 *    `kInverseEvenOdd`.
 *  - **paint style** (cells per row): fill, stroke (width 10), and
 *    stroke-and-fill.
 *  - **cap + join** (column blocks of 3 styles): butt-bevel,
 *    round-round, square-bevel.
 *
 * Each cell clips to a `100 × 30` rect, draws the cubic in dark green
 * `0xff007000` at the configured style/cap/join/fill, then strokes
 * the rect outline as a 1-px black hairline (`strokeWidth = 0`,
 * matching the upstream `setStrokeWidth(-1)` cargo default which
 * Skia treats as hairline). Three text labels (style / fill / cap)
 * sit below each cell in the same dark green.
 *
 * Inverse fills here exercise the Phase 3.8 scanline-walker
 * extension: rows above / below the path's edge bbox contribute
 * full-coverage spans, the per-row span loop seeds
 * `inside = isInside(0, fillType)` so the region left of the first
 * crossing is already filled, and the trailing flush emits the
 * residual span to `clip.right`.
 *
 * **Reference image**: `cubicpath.png`, 1240 × 390, white background.
 */
public class CubicPathGM : GM() {

    override fun getName(): String = "cubicpath"
    override fun getISize(): SkISize = SkISize.Make(1240, 390)

    private fun drawPathCell(
        path: SkPath, canvas: SkCanvas, color: Int,
        clip: SkRect,
        cap: SkPaint.Cap, join: SkPaint.Join,
        style: SkPaint.Style, fill: SkPathFillType,
        strokeWidth: Float,
    ) {
        val typedPath = path.makeFillType(fill)
        val paint = SkPaint().apply {
            this.color = color
            this.style = style
            this.strokeCap = cap
            this.strokeJoin = join
            this.strokeWidth = strokeWidth
        }
        canvas.save()
        canvas.clipRect(clip)
        canvas.drawPath(typedPath, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // --- the cubic path itself ------------------------------------
        val path = SkPathBuilder()
            .moveTo(25f, 10f)
            .cubicTo(40f, 20f, 60f, 20f, 75f, 10f)
            .detach()

        val fills = arrayOf(
            SkPathFillType.kWinding to "Winding",
            SkPathFillType.kEvenOdd to "Even / Odd",
            SkPathFillType.kInverseWinding to "Inverse Winding",
            SkPathFillType.kInverseEvenOdd to "Inverse Even / Odd",
        )
        val styles = arrayOf(
            SkPaint.Style.kFill_Style to "Fill",
            SkPaint.Style.kStroke_Style to "Stroke",
            SkPaint.Style.kStrokeAndFill_Style to "Stroke And Fill",
        )
        data class CapJoin(val cap: SkPaint.Cap, val join: SkPaint.Join, val name: String)
        val caps = arrayOf(
            CapJoin(SkPaint.Cap.kButt_Cap, SkPaint.Join.kBevel_Join, "Butt"),
            CapJoin(SkPaint.Cap.kRound_Cap, SkPaint.Join.kRound_Join, "Round"),
            CapJoin(SkPaint.Cap.kSquare_Cap, SkPaint.Join.kBevel_Join, "Square"),
        )

        // --- title ----------------------------------------------------
        val titlePaint = SkPaint().apply {
            color = SK_ColorBLACK
            isAntiAlias = true
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 15f)
        val title = "Cubic Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10"
        c.drawString(title, 20f, 20f, font, titlePaint)

        // --- 4 × 3 × 3 grid -------------------------------------------
        val rect = SkRect.MakeWH(100f, 30f)
        c.save()
        c.translate(10f, 30f)
        c.save()
        for (capIdx in caps.indices) {
            if (capIdx > 0) c.translate((rect.width() + 40f) * styles.size, 0f)
            c.save()
            for (fillIdx in fills.indices) {
                if (fillIdx > 0) c.translate(0f, rect.height() + 40f)
                c.save()
                for (styleIdx in styles.indices) {
                    if (styleIdx > 0) c.translate(rect.width() + 40f, 0f)

                    val cellColor = 0xff007000.toInt()
                    drawPathCell(
                        path, c, cellColor, rect,
                        caps[capIdx].cap, caps[capIdx].join,
                        styles[styleIdx].first, fills[fillIdx].first,
                        strokeWidth = 10f,
                    )

                    // Hairline rect outline — upstream uses
                    // `setStrokeWidth(-1)` (legacy cargo default Skia
                    // historically treated as hairline). Modern Skia
                    // hairlines via `strokeWidth = 0`; we use that.
                    val rectPaint = SkPaint().apply {
                        color = SK_ColorBLACK
                        style = SkPaint.Style.kStroke_Style
                        strokeWidth = 0f
                        isAntiAlias = true
                    }
                    c.drawRect(rect, rectPaint)

                    val labelPaint = SkPaint().apply { color = cellColor }
                    font.size = 10f
                    c.drawString(styles[styleIdx].second, 0f, rect.height() + 12f, font, labelPaint)
                    c.drawString(fills[fillIdx].second, 0f, rect.height() + 24f, font, labelPaint)
                    c.drawString(caps[capIdx].name, 0f, rect.height() + 36f, font, labelPaint)
                }
                c.restore()
            }
            c.restore()
        }
        c.restore()
        c.restore()
    }
}
