package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkVector
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/rrect.cpp` (`RRectGM`).
 *
 * A 4-row × 4-column grid of stroked rrects, each row showing a different
 * `inset` strategy and each column a different starting [SkRRect.Type]
 * (rect / oval / simple / complex per-corner radii). Within each cell, the
 * inset is applied 13 times for `d ∈ [-30, 30]` step `5`, producing nested
 * rings of rrects that visualise how the inset proc handles negative,
 * zero, and positive radii.
 *
 * The four insets differ in how they transform the corner radii:
 *  - **inset0**: subtract `dx` / `dy` from every radius (clamps at 0 implicitly
 *    via [SkRRect.setRectRadii]).
 *  - **inset1**: keep the radii as-is.
 *  - **inset2**: subtract only when the radius is non-zero (preserves zero).
 *  - **inset3**: scale radii proportionally to the new rect size.
 *
 * Stroke colour reflects the rrect's specialisation:
 *  - `isRect()` → red, `isOval()` → 565-quantised dark green, `isSimple()`
 *    → blue, otherwise (nine-patch / complex) → black.
 *
 * Reference image: `rrect.png`, 820 × 710, default white BG.
 */
public class RRectGM : GM() {

    override fun getName(): String = "rrect"
    override fun getISize(): SkISize = SkISize.Make(820, 710)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val insetProcs: Array<(SkRRect, Float, Float) -> SkRRect> =
            arrayOf(::inset0, ::inset1, ::inset2, ::inset3)

        val rrect = arrayOfNulls<SkRRect>(4)
        val r = SkRect.MakeLTRB(0f, 0f, 120f, 100f)
        val radii = arrayOf(
            SkVector(0f, 0f),
            SkVector(30f, 1f),
            SkVector(10f, 40f),
            SkVector(40f, 40f),
        )

        rrect[0] = SkRRect().apply { setRect(r) }
        rrect[1] = SkRRect().apply { setOval(r) }
        rrect[2] = SkRRect().apply { setRectXY(r, 20f, 20f) }
        rrect[3] = SkRRect().apply { setRectRadii(r, radii) }

        c.translate(50.5f, 50.5f)
        for (j in insetProcs.indices) {
            c.save()
            for (i in rrect.indices) {
                drawRR(c, rrect[i]!!, insetProcs[j])
                c.translate(200f, 0f)
            }
            c.restore()
            c.translate(0f, 170f)
        }
    }

    /**
     * Mirrors upstream's `drawrr` helper. Walks `d` from -30 to +30 step 5,
     * applies the inset proc, and stroke-draws each result.
     */
    private fun drawRR(
        canvas: SkCanvas,
        src: SkRRect,
        proc: (SkRRect, Float, Float) -> SkRRect,
    ) {
        var d = -30f
        while (d <= 30f) {
            val rr = proc(src, d, d)
            drawColored(canvas, rr)
            d += 5f
        }
    }

    /**
     * Mirrors upstream's `draw_rrect_color`. Per-type stroke colour scheme,
     * default 1-px stroke (paint default `strokeWidth = 0` → hairline
     * fallback in our pipeline; matches upstream up to the 1-px broadening).
     */
    private fun drawColored(canvas: SkCanvas, rrect: SkRRect) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        paint.color = when {
            rrect.isRect() -> 0xFFFF0000.toInt()             // SK_ColorRED
            rrect.isOval() -> ToolUtils.colorTo565(0xFF008800.toInt())
            rrect.isSimple() -> 0xFF0000FF.toInt()           // SK_ColorBLUE
            else -> 0xFF000000.toInt()                        // SK_ColorBLACK
        }
        canvas.drawRRect(rrect, paint)
    }

    // ----- inset procs (mirror upstream rrect.cpp file-level statics) ----

    /** Subtract `dx` / `dy` from every radius. */
    private fun inset0(src: SkRRect, dx: Float, dy: Float): SkRRect {
        val r = insetRect(src, dx, dy) ?: return SkRRect()    // empty
        val rr = SkRRect()
        val newRadii = Array(4) { i ->
            val v = src.radii(idxToCorner(i))
            SkVector(v.fX - dx, v.fY - dy)
        }
        rr.setRectRadii(r, newRadii)
        return rr
    }

    /** Keep the radii as-is. */
    private fun inset1(src: SkRRect, dx: Float, dy: Float): SkRRect {
        val r = insetRect(src, dx, dy) ?: return SkRRect()
        val rr = SkRRect()
        val keep = Array(4) { i -> src.radii(idxToCorner(i)).copy() }
        rr.setRectRadii(r, keep)
        return rr
    }

    /** Subtract only on non-zero radii. */
    private fun inset2(src: SkRRect, dx: Float, dy: Float): SkRRect {
        val r = insetRect(src, dx, dy) ?: return SkRRect()
        val rr = SkRRect()
        val adj = Array(4) { i ->
            val v = src.radii(idxToCorner(i))
            SkVector(
                if (v.fX != 0f) v.fX - dx else 0f,
                if (v.fY != 0f) v.fY - dy else 0f,
            )
        }
        rr.setRectRadii(r, adj)
        return rr
    }

    /** Scale radii proportionally to the new rect size. */
    private fun inset3(src: SkRRect, dx: Float, dy: Float): SkRRect {
        val r = insetRect(src, dx, dy) ?: return SkRRect()
        val rr = SkRRect()
        val ow = src.rect().width()
        val oh = src.rect().height()
        val nw = r.width()
        val nh = r.height()
        val scaled = Array(4) { i ->
            val v = src.radii(idxToCorner(i))
            SkVector(nw * v.fX / ow, nh * v.fY / oh)
        }
        rr.setRectRadii(r, scaled)
        return rr
    }

    /**
     * Mirrors `r.inset(dx, dy)` followed by an emptiness check. Returns
     * `null` when the inset rect would degenerate (caller treats as
     * `setEmpty`).
     */
    private fun insetRect(src: SkRRect, dx: Float, dy: Float): SkRect? {
        val sr = src.rect()
        val r = SkRect.MakeLTRB(sr.left + dx, sr.top + dy, sr.right - dx, sr.bottom - dy)
        return if (r.left >= r.right || r.top >= r.bottom) null else r
    }

    private fun idxToCorner(i: Int): SkRRect.Corner = when (i) {
        0 -> SkRRect.Corner.kUpperLeft_Corner
        1 -> SkRRect.Corner.kUpperRight_Corner
        2 -> SkRRect.Corner.kLowerRight_Corner
        else -> SkRRect.Corner.kLowerLeft_Corner
    }
}
