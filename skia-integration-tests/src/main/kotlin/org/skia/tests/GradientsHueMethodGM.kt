package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/gradients.cpp::gradients_hue_method`
 * (`DEF_SIMPLE_GM_BG`, 285 x 155, gray background).
 *
 * Renders four HSL gradients with different hue methods, followed by two rows
 * that exercise explicit-position endpoints for kLonger interpolation.
 */
public class GradientsHueMethodGM : GM() {

    override fun getName(): String = "gradients_hue_method"
    override fun getISize(): SkISize = SkISize.Make(285, 155)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorGRAY)
        c.translate(5f, 5f)

        val pts = arrayOf(SkPoint(0f, 0f), SkPoint(200f, 0f))
        val rect = SkRect.MakeXYWH(0f, 0f, 200f, 20f)
        val gradientPaint = SkPaint()
        val labelPaint = SkPaint()
        val font = ToolUtils.DefaultPortableFont()

        val rows = arrayOf(
            SkGradient.Interpolation.HueMethod.kShorter to "Shorter",
            SkGradient.Interpolation.HueMethod.kLonger to "Longer",
            SkGradient.Interpolation.HueMethod.kIncreasing to "Increasing",
            SkGradient.Interpolation.HueMethod.kDecreasing to "Decreasing",
        )

        val repeatedColors = intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorRED, SK_ColorRED)
        for ((method, label) in rows) {
            gradientPaint.shader = SkShaders.LinearGradient(
                pts,
                SkGradient(
                    colors = repeatedColors,
                    tileMode = SkTileMode.kClamp,
                    interpolation = hsl(method),
                ),
            )
            c.drawRect(rect, gradientPaint)
            c.drawSimpleText(label, label.length, SkTextEncoding.kUTF8, 210f, 15f, font, labelPaint)
            c.translate(0f, 25f)
        }

        gradientPaint.shader = SkShaders.LinearGradient(
            pts,
            SkGradient(
                colors = intArrayOf(SK_ColorRED, SK_ColorGREEN),
                positions = floatArrayOf(0.3f, 0.7f),
                tileMode = SkTileMode.kClamp,
                interpolation = hsl(SkGradient.Interpolation.HueMethod.kLonger),
            ),
        )
        c.drawRect(rect, gradientPaint)
        c.translate(0f, 25f)

        gradientPaint.shader = SkShaders.LinearGradient(
            pts,
            SkGradient(
                colors = intArrayOf(SK_ColorRED, SK_ColorRED, SK_ColorGREEN, SK_ColorGREEN),
                positions = floatArrayOf(0.0f, 0.3f, 0.7f, 1.0f),
                tileMode = SkTileMode.kClamp,
                interpolation = hsl(SkGradient.Interpolation.HueMethod.kLonger),
            ),
        )
        c.drawRect(rect, gradientPaint)
    }

    private fun hsl(method: SkGradient.Interpolation.HueMethod): SkGradient.Interpolation =
        SkGradient.Interpolation(
            colorSpace = SkGradient.Interpolation.ColorSpace.kHSL,
            hueMethod = method,
        )
}
