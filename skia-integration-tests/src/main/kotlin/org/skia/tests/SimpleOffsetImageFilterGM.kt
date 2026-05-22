package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/offsetimagefilter.cpp::SimpleOffsetImageFilterGM`
 * (`simple-offsetimagefilter`, 640 × 200).
 *
 * Two rows of six "blue-rect, red-offset-rect" pairs exercising
 * `SkImageFilters::Offset` with various combinations of optional
 * `cropRect` and `clipRect`. The blue rect is the geometry source ;
 * the red rect is the same rect drawn with an Offset image filter,
 * with an optional crop (purple stroke) and clip (green stroke)
 * outlined for visual triage.
 *
 * Row 1 : crop / clip permutations covering null / src / dst.
 * Row 2 : `crop == clip == src/dst` and inverted combos.
 *
 * Pure SkImageFilters::Offset coverage : verifies our DAG correctly
 * threads cropRect through Crop(kDecal, …) and respects upstream
 * `clipRect ∩ cropRect` truncation.
 */
public class SimpleOffsetImageFilterGM : GM() {

    override fun getName(): String = "simple-offsetimagefilter"
    override fun getISize(): SkISize = SkISize.Make(640, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val cr0 = SkIRect.MakeWH(40, 40)
        val cr1 = SkIRect.MakeWH(20, 20)
        val cr2 = SkIRect.MakeXYWH(40, 0, 40, 40)
        val r = SkRect.Make(cr0)
        val r2 = SkRect.Make(cr2)

        c.translate(40f, 40f)

        c.save()
        doDraw(c, r, null)

        c.translate(100f, 0f)
        doDraw(c, r, SkImageFilters.Offset(20f, 20f, null))

        c.translate(100f, 0f)
        doDraw(c, r, SkImageFilters.Offset(20f, 20f, null, SkRect.Make(cr0)), cr0)

        c.translate(100f, 0f)
        doDraw(c, r, SkImageFilters.Offset(20f, 20f, null), clipR = r)

        c.translate(100f, 0f)
        doDraw(c, r, SkImageFilters.Offset(20f, 20f, null, SkRect.Make(cr1)), cr1)

        val clipR = SkRect.MakeXYWH(40f, 40f, 40f, 40f)
        c.translate(100f, 0f)
        doDraw(c, r, SkImageFilters.Offset(20f, 20f, null), clipR = clipR)
        c.restore()

        // 2nd row
        c.translate(0f, 80f)

        // crop==clip==src
        doDraw(c, r, SkImageFilters.Offset(40f, 0f, null, SkRect.Make(cr0)), cr0, r)
        c.translate(100f, 0f)
        // crop==src, clip==dst
        doDraw(c, r, SkImageFilters.Offset(40f, 0f, null, SkRect.Make(cr0)), cr0, r2)
        c.translate(100f, 0f)
        // crop==dst, clip==src
        doDraw(c, r, SkImageFilters.Offset(40f, 0f, null, SkRect.Make(cr2)), cr2, r)
        c.translate(100f, 0f)
        // crop==clip==dst
        doDraw(c, r, SkImageFilters.Offset(40f, 0f, null, SkRect.Make(cr2)), cr2, r2)
    }

    private fun doDraw(
        c: SkCanvas,
        r: SkRect,
        imgf: SkImageFilter?,
        cropR: SkIRect? = null,
        clipR: SkRect? = null,
    ) {
        val p = SkPaint()

        if (clipR != null) {
            p.color = 0xFF00FF00.toInt()
            p.style = SkPaint.Style.kStroke_Style
            val inset = SkRect.MakeLTRB(clipR.left + 0.5f, clipR.top + 0.5f, clipR.right - 0.5f, clipR.bottom - 0.5f)
            c.drawRect(inset, p)
            p.style = SkPaint.Style.kFill_Style
        }

        if (imgf != null && cropR != null) {
            p.color = 0x66FF00FF.toInt()
            p.style = SkPaint.Style.kStroke_Style
            val cr = SkRect.MakeLTRB(
                cropR.left + 0.5f, cropR.top + 0.5f,
                cropR.right - 0.5f, cropR.bottom - 0.5f,
            )
            c.drawRect(cr, p)
            p.style = SkPaint.Style.kFill_Style
        }

        p.color = 0x660000FF.toInt()
        c.drawRect(r, p)

        if (clipR != null) {
            c.save()
            c.clipRect(clipR)
        }
        if (imgf != null) {
            p.imageFilter = imgf
        }
        p.color = 0x66FF0000.toInt()
        c.drawRect(r, p)

        if (clipR != null) {
            c.restore()
        }
    }
}
