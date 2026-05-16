package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/rrectclipdrawpaint.cpp::rrect_clip_draw_paint`
 * (256 × 256).
 *
 * Exercises a `clipRRect` followed by `drawPaint` (the entire clip
 * area gets filled in one call). Originally probed a fast-path in the
 * GPU `SurfaceDrawContext` that tried to replace this idiom with a
 * single `drawRRect`. This GM is the visual proof the substitution
 * stays bit-equivalent across solid / linear / radial gradient fills.
 *
 * Layout : 4 saveLayer panels, each `clipRRect`-ed then `drawPaint`-ed,
 * stacked under successive `concat(zoomOut)` matrices to test that
 * the clip + paint composition survives nested transforms.
 */
public class RRectClipDrawPaintGM : GM() {

    override fun getName(): String = "rrect_clip_draw_paint"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rrect = SkRRect.MakeRectXY(SkRect.MakeXYWH(10f, 10f, 236f, 236f), 30f, 40f)

        val p = SkPaint().apply { color = SK_ColorRED }
        val zoomOut = SkMatrix.MakeScale(0.7f, 0.7f, 128f, 128f)

        val layerRect = SkRect.MakeWH(256f, 256f)
        c.saveLayer(layerRect, null)
        c.clipRRect(rrect, doAntiAlias = true)
        c.drawPaint(p)
        c.restore()

        c.concat(zoomOut)
        p.color = SK_ColorBLUE
        c.saveLayer(layerRect, null)
        c.clipRRect(rrect, doAntiAlias = false)
        c.drawPaint(p)
        c.restore()

        // Linear gradient cyan→green
        val cyan = 0xFF00FFFF.toInt()
        val green = 0xFF00FF00.toInt()
        p.shader = SkLinearGradient.Make(
            SkPoint(0f, 0f), SkPoint(256f, 256f),
            intArrayOf(cyan, green),
            null,
            SkTileMode.kClamp,
        )
        c.concat(zoomOut)
        c.saveLayer(layerRect, null)
        c.clipRRect(rrect, doAntiAlias = true)
        c.drawPaint(p)
        c.restore()

        // Radial gradient magenta→gray
        val magenta = 0xFFFF00FF.toInt()
        val gray = 0xFF888888.toInt()
        p.shader = SkRadialGradient.Make(
            SkPoint(128f, 128f), 128f,
            intArrayOf(magenta, gray),
            null,
            SkTileMode.kClamp,
        )
        c.concat(zoomOut)
        c.saveLayer(layerRect, null)
        c.clipRRect(rrect, doAntiAlias = false)
        c.drawPaint(p)
        c.restore()
    }
}
