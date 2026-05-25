package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontPriv
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathMeasure
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.abs
import kotlin.math.max

/**
 * Port of `gm/drawatlas.cpp::drawTextRSXform` (DEF_SIMPLE_GM, 430 × 860).
 *
 * Draws the Latin alphabet (`ABCDFGHJKLMNOPQRSTUVWXYZ`) along two ovals
 * (CW and CCW) using per-glyph [SkRSXform]s, with a linear red→blue
 * gradient shader. Then repeats with the stroke style.
 *
 */
public class DrawTextRSXformGM : GM() {

    override fun getName(): String = "drawTextRSXform"
    override fun getISize(): SkISize = SkISize.Make(430, 860)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(0.5f, 0.5f)
        val doStroke = booleanArrayOf(false, true)
        for (st in doStroke) {
            drawTextPath(c, st)
            c.translate(0f, 860f)
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private fun makeShader(): SkLinearGradient {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(220f, 0f)
        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())
        return SkLinearGradient.Make(p0, p1, colors, null, SkTileMode.kMirror)
    }

    private fun drawTextPath(canvas: SkCanvas, doStroke: Boolean) {
        val text = "ABCDFGHJKLMNOPQRSTUVWXYZ"
        val n = text.length

        val font = ToolUtils.DefaultPortableFont().apply { size = 100f }
        val paint = SkPaint().apply {
            shader = makeShader()
            isAntiAlias = true
            if (doStroke) {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2.25f
                strokeJoin = SkPaint.Join.kRound_Join
            }
        }

        // Cumulative x-positions per glyph.
        val pos = Array(n) { SkPoint(0f, 0f) }
        var x = 0f
        for (i in 0 until n) {
            pos[i] = SkPoint(x, 0f)
            x += font.measureText(text.substring(i, i + 1))
        }

        val baselineOffset = -5f

        val dirs = arrayOf(SkPathDirection.kCW, SkPathDirection.kCCW)
        var path = SkPath.Oval(SkRect.MakeXYWH(160f, 160f, 540f, 540f))
        for (dir in dirs) {
            path = SkPath.Oval(SkRect.MakeXYWH(160f, 160f, 540f, 540f), dir)
            drawTextOnPath(canvas, text, pos, path, font, paint, baselineOffset)
        }

        // Draw the final path outline (stroke only).
        val outline = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        canvas.drawPath(path, outline)
    }

    /**
     * Mirrors `draw_text_on_path` from the cpp. Positions each glyph of
     * [text] along [path] using per-glyph [SkRSXform]s derived from
     * [SkPathMeasure.getPosTan], then draws a single RSXform text blob.
     */
    private fun drawTextOnPath(
        canvas: SkCanvas,
        text: String,
        xy: Array<SkPoint>,
        path: SkPath,
        font: SkFont,
        paint: SkPaint,
        baselineOffset: Float,
    ) {
        val meas = SkPathMeasure(path, false)

        val count = text.length
        val glyphs = font.textToGlyphs(text)
        val widths = font.getWidths(glyphs)

        // Conservative bounds for culling (mirrors upstream).
        val fontb = SkFontPriv.GetFontBounds(font)
        val maxDim = max(
            max(abs(fontb.left), abs(fontb.right)),
            max(abs(fontb.top), abs(fontb.bottom)),
        )
        val bounds = path.computeBounds().run {
            SkRect.MakeLTRB(left - maxDim, top - maxDim, right + maxDim, bottom + maxDim)
        }

        val xforms = Array(count) { i ->
            val offset = widths[i] * 0.5f
            val pos2 = SkPoint(0f, 0f)
            val tan = SkPoint(0f, 0f)
            if (!meas.getPosTan(xy[i].fX + offset, pos2, tan)) {
                pos2.fX = xy[i].fX; pos2.fY = xy[i].fY
                tan.fX = 1f; tan.fY = 0f
            }
            // baseline_offset shifts pos in the normal direction (-tan.y, tan.x).
            val nx = -tan.fY * baselineOffset
            val ny = tan.fX * baselineOffset
            pos2.fX += nx; pos2.fY += ny
            SkRSXform.Make(
                scos = tan.fX,
                ssin = tan.fY,
                tx = pos2.fX - tan.fY * xy[i].fY - tan.fX * offset,
                ty = pos2.fY + tan.fX * xy[i].fY - tan.fY * offset,
            )
        }

        val blob = SkTextBlob.MakeFromRSXformGlyphs(glyphs, xforms, font)
        canvas.drawTextBlob(blob, 0f, 0f, paint)

        // Draw the conservative bounds rect (mirrors `if (true)` block in cpp).
        val boundsP = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        canvas.drawRect(bounds, boundsP)
    }
}
