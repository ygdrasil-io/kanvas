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

/**
 * Partial port of upstream `gradients_color_space` for currently-supported
 * interpolation spaces in this tree.
 */
public class GradientsColorSpaceGM : GM() {

    override fun getName(): String = "gradients_color_space"
    override fun getISize(): SkISize = SkISize.Make(265, 355)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGRAY)
        val spaces = listOf(
            SkGradient.Interpolation.ColorSpace.kDestination,
            SkGradient.Interpolation.ColorSpace.kSRGB,
            SkGradient.Interpolation.ColorSpace.kSRGBLinear,
            SkGradient.Interpolation.ColorSpace.kLCH,
            SkGradient.Interpolation.ColorSpace.kOKLCH,
            SkGradient.Interpolation.ColorSpace.kHSL,
            SkGradient.Interpolation.ColorSpace.kHWB,
            SkGradient.Interpolation.ColorSpace.kA98RGB,
            SkGradient.Interpolation.ColorSpace.kProPhotoRGB,
            SkGradient.Interpolation.ColorSpace.kDisplayP3,
            SkGradient.Interpolation.ColorSpace.kRec2020,
        )
        val paint = SkPaint()
        for ((i, space) in spaces.withIndex()) {
            paint.shader = SkShaders.LinearGradient(
                arrayOf(SkPoint(5f, 0f), SkPoint(260f, 0f)),
                SkGradient(
                    colors = intArrayOf(SK_ColorBLUE, SK_ColorYELLOW),
                    interpolation = SkGradient.Interpolation(colorSpace = space),
                ),
            )
            c.drawRect(SkRect.MakeXYWH(5f, 5f + i * 30f, 255f, 20f), paint)
        }
    }
}
