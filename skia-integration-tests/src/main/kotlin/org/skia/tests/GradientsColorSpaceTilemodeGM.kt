package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorYELLOW
import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode

/**
 * Mirrors the OKLCH tilemode slice from upstream `gm/gradients.cpp`.
 */
public class GradientsColorSpaceTilemodeGM : GM() {

    override fun getName(): String = "gradients_color_space_tilemode"
    override fun getISize(): SkISize = SkISize.Make(360, 105)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGRAY)
        val tileModes = listOf(
            SkTileMode.kClamp,
            SkTileMode.kRepeat,
            SkTileMode.kMirror,
            SkTileMode.kDecal,
        )
        val paint = SkPaint()
        for ((i, tileMode) in tileModes.withIndex()) {
            paint.shader = SkShaders.LinearGradient(
                arrayOf(SkPoint(20f, 0f), SkPoint(120f, 0f)),
                SkGradient(
                    colors = intArrayOf(SK_ColorBLUE, SK_ColorYELLOW),
                    tileMode = tileMode,
                    interpolation = SkGradient.Interpolation(
                        colorSpace = SkGradient.Interpolation.ColorSpace.kOKLCH,
                    ),
                ),
            )
            c.drawRect(SkRect.MakeXYWH(5f, 5f + i * 25f, 350f, 20f), paint)
        }
    }
}
