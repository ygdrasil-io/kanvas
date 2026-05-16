package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRegion
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkPoint

/**
 * Port of Skia's `gm/drawregionmodes.cpp` (`DrawRegionModesGM`,
 * GM name `drawregionmodes`).
 *
 * Stresses [SkCanvas.drawRegion] under :
 *  - rotated CTM ;
 *  - paint image filter (blur) ;
 *  - paint mask filter (blur) ;
 *  - paint stroke + dash path effect ;
 *  - paint shader (linear gradient).
 *
 * Reference image: `drawregionmodes.png`, 375 × 500.
 */
public class DrawRegionModesGM : GM() {

    override fun getName(): String = "drawregionmodes"
    override fun getISize(): SkISize = SkISize.Make(375, 500)

    private val fRegion: SkRegion = SkRegion()

    override fun onOnceBeforeDraw() {
        fRegion.op(SkIRect(50, 50, 100, 100), SkRegion.Op.kUnion)
        fRegion.op(SkIRect(50, 100, 150, 150), SkRegion.Op.kUnion)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGREEN)

        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            color = SK_ColorRED
            isAntiAlias = true
        }

        c.save()
        c.translate(-50f, 75f)
        c.rotate(-45f)
        c.drawRegion(fRegion, paint)

        c.translate(125f, 125f)
        paint.imageFilter = SkImageFilters.Blur(5f, 5f, null)
        c.drawRegion(fRegion, paint)

        c.translate(-125f, 125f)
        paint.imageFilter = null
        paint.maskFilter = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 5f)
        c.drawRegion(fRegion, paint)

        c.translate(-125f, -125f)
        paint.maskFilter = null
        paint.style = SkPaint.Style.kStroke_Style
        paint.pathEffect = SkDashPathEffect.Make(floatArrayOf(5f, 5f), 2.5f)
        c.drawRegion(fRegion, paint)

        c.restore()

        c.translate(100f, 325f)
        paint.pathEffect = null
        paint.style = SkPaint.Style.kFill_Style
        val pts = arrayOf(SkPoint.Make(50f, 50f), SkPoint.Make(150f, 150f))
        paint.shader = SkLinearGradient.Make(
            pts[0],
            pts[1],
            intArrayOf(0xFF0000FF.toInt(), 0xFFFFFF00.toInt()),
            null,
            SkTileMode.kClamp,
        )
        c.drawRegion(fRegion, paint)
    }
}
