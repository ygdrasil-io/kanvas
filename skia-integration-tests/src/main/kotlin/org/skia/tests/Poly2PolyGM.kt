package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/poly2poly.cpp::Poly2PolyGM` (835 × 840).
 *
 * Exercises [SkMatrix.MakePolyToPoly] — the polygon-to-polygon affine
 * builder used to fit `n ≤ 4` source points onto `n` destination
 * points. Four sub-cells lay out the four mapping kinds:
 *
 *  1. **Translate** (1 point) — `(0,0) → (5,5)`.
 *  2. **Rotate / uniform scale** (2 points) — same first point, second
 *     point shifted down by 16 px (atan2 ≈ 26.6° rotation, no scale).
 *  3. **Rotate / skew** (3 points) — a 64×64 box mapped to a tilted
 *     parallelogram.
 *  4. **Perspective** (4 points) — a 64×64 box mapped to a trapezoid,
 *     producing a true projective matrix (`persp0/persp1` non-zero).
 *
 * Each cell paints a 64×64 frame (gray stroke), its two diagonals, and
 * a red glyph (upstream uses glyph 3 from `fonts/Em.ttf` which renders
 * as an "X"; the resource isn't bundled here so we fall through to the
 * default portable typeface and draw the ASCII letter "X" with
 * [SkTextUtils.DrawString] for the same visual intent). The fitting
 * matrix is applied via [SkCanvas.concat].
 */
public class Poly2PolyGM : GM() {

    private var emFace = ToolUtils.DefaultPortableTypeface()

    override fun getName(): String = "poly2poly"
    override fun getISize(): SkISize = SkISize.Make(835, 840)

    override fun onOnceBeforeDraw() {
        // Upstream tries `fonts/Em.ttf` and falls back to
        // `DefaultPortableTypeface` — we go straight to the fallback.
        emFace = ToolUtils.DefaultPortableTypeface()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = 4f
        }
        val font = SkFont(emFace, 40f)

        // Translate (1 point)
        c.save()
        c.translate(10f, 10f)
        doDraw(
            c, font, paint,
            intArrayOf(0, 0),
            intArrayOf(5, 5),
            1,
        )
        c.restore()

        // Rotate/uniform-scale (2 points)
        c.save()
        c.translate(160f, 10f)
        doDraw(
            c, font, paint,
            intArrayOf(32, 32, 64, 32),
            intArrayOf(32, 32, 64, 48),
            2,
        )
        c.restore()

        // Rotate/skew (3 points)
        c.save()
        c.translate(10f, 110f)
        doDraw(
            c, font, paint,
            intArrayOf(0, 0, 64, 0, 0, 64),
            intArrayOf(0, 0, 96, 0, 24, 64),
            3,
        )
        c.restore()

        // Perspective (4 points)
        c.save()
        c.translate(160f, 110f)
        doDraw(
            c, font, paint,
            intArrayOf(0, 0, 64, 0, 64, 64, 0, 64),
            intArrayOf(0, 0, 96, 0, 64, 96, 0, 64),
            4,
        )
        c.restore()
    }

    private fun doDraw(
        canvas: SkCanvas,
        font: SkFont,
        paint: SkPaint,
        isrc: IntArray,
        idst: IntArray,
        count: Int,
    ) {
        val src = Array(count) { i -> SkPoint(isrc[2 * i].toFloat(), isrc[2 * i + 1].toFloat()) }
        val dst = Array(count) { i -> SkPoint(idst[2 * i].toFloat(), idst[2 * i + 1].toFloat()) }

        canvas.save()
        val mx = SkMatrix.MakePolyToPoly(src, dst)
        if (mx != null) {
            canvas.concat(mx)
        }

        paint.color = SK_ColorGRAY
        paint.style = SkPaint.Style.kStroke_Style
        val d = 64f
        canvas.drawRect(SkRect.MakeWH(d, d), paint)
        canvas.drawLine(0f, 0f, d, d, paint)
        canvas.drawLine(0f, d, d, 0f, paint)

        val fm = SkFontMetrics()
        font.getMetrics(fm)
        paint.color = SK_ColorRED
        paint.style = SkPaint.Style.kFill_Style
        val x = d / 2f
        val y = d / 2f - (fm.fAscent + fm.fDescent) / 2f
        SkTextUtils.Draw(
            canvas, "X", SkTextEncoding.kUTF8, x, y, font, paint,
            SkTextUtils.Align.kCenter_Align,
        )
        canvas.restore()
    }
}
