package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Placeholders for Skia's `gm/gradients.cpp` powerless-hue GM family
 * (`DEF_POWERLESS_HUE_GM(colorSpace)`, 415 × 330 each).
 *
 * Four GMs, one per CSS hue-bearing color space:
 *  - `gradients_powerless_hue_LCH`   — `ColorSpace::kLCH`
 *  - `gradients_powerless_hue_OKLCH` — `ColorSpace::kOKLCH`
 *  - `gradients_powerless_hue_HSL`   — `ColorSpace::kHSL`
 *  - `gradients_powerless_hue_HWB`   — `ColorSpace::kHWB`
 *
 * Each exercises how the gradient interpolator handles "powerless hue"
 * components (white, black, transparent) when interpolating in a hue-
 * bearing color space. Also tests hue propagation with kShorter / kIncreasing
 * / kDecreasing / kLonger hue methods on black-white sequences.
 *
 * **Implementation gap** : LCH and HSL are implemented as bounded sampler
 * slices. OKLCH and HWB still need their own perceptual conversion pipelines.
 */

public class GradientsPowerlessHueLchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_LCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        drawPowerlessHueGradients(canvas, SkGradient.Interpolation.ColorSpace.kLCH)
    }
}

public class GradientsPowerlessHueOklchGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_OKLCH"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: OKLCH powerless-hue interpolation not implemented")
    }
}

public class GradientsPowerlessHueHslGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HSL"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        drawPowerlessHueGradients(canvas, SkGradient.Interpolation.ColorSpace.kHSL)
    }
}

public class GradientsPowerlessHueHwbGM : GM() {
    override fun getName(): String = "gradients_powerless_hue_HWB"
    override fun getISize(): SkISize = SkISize.Make(415, 330)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GRADIENT_INTERPOLATION: HWB powerless-hue interpolation not implemented")
    }
}

private fun drawPowerlessHueGradients(
    canvas: SkCanvas?,
    colorSpace: SkGradient.Interpolation.ColorSpace,
) {
    val c = canvas ?: return
    ToolUtils.draw_checkerboard(c, 0xFF999999.toInt(), 0xFF666666.toInt(), 8)

    fun nextRow() {
        c.restore()
        c.translate(0f, 25f)
        c.save()
    }

    fun gradient(colors: IntArray, positions: FloatArray? = null, inPremul: Boolean = false) {
        val paint = SkPaint()
        paint.shader = SkShaders.LinearGradient(
            arrayOf(SkPoint(0f, 0f), SkPoint(200f, 0f)),
            SkGradient(
                colors = colors,
                positions = positions,
                tileMode = SkTileMode.kClamp,
                interpolation = SkGradient.Interpolation(
                    colorSpace = colorSpace,
                    inPremul = if (inPremul) {
                        SkGradient.Interpolation.InPremul.kYes
                    } else {
                        SkGradient.Interpolation.InPremul.kNo
                    },
                ),
            ),
        )
        c.drawRect(SkRect.MakeXYWH(0f, 0f, 200f, 20f), paint)
        c.translate(205f, 0f)
    }

    c.translate(5f, 5f)
    c.save()

    gradient(intArrayOf(SK_ColorWHITE, SK_ColorBLUE))
    gradient(intArrayOf(argb(255, 252, 252, 255), SK_ColorBLUE))
    nextRow()

    gradient(intArrayOf(SK_ColorBLACK, SK_ColorBLUE))
    gradient(intArrayOf(argb(255, 0, 0, 3), SK_ColorBLUE))
    nextRow()

    gradient(intArrayOf(SK_ColorTRANSPARENT, SK_ColorBLUE), inPremul = false)
    gradient(intArrayOf(argb(0, 0, 0, 3), SK_ColorBLUE), inPremul = false)
    nextRow()

    gradient(intArrayOf(SK_ColorTRANSPARENT, SK_ColorBLUE), inPremul = true)
    gradient(intArrayOf(argb(0, 0, 0, 3), SK_ColorBLUE), inPremul = true)
    nextRow()

    gradient(intArrayOf(argb(0, 255, 255, 255), SK_ColorBLUE), inPremul = false)
    gradient(intArrayOf(argb(0, 252, 252, 255), SK_ColorBLUE), inPremul = false)
    nextRow()

    gradient(intArrayOf(argb(0, 255, 255, 255), SK_ColorBLUE), inPremul = true)
    gradient(intArrayOf(argb(0, 252, 252, 255), SK_ColorBLUE), inPremul = true)
    nextRow()

    gradient(intArrayOf(SK_ColorRED, SK_ColorWHITE, SK_ColorBLUE))
    gradient(
        intArrayOf(SK_ColorRED, argb(255, 255, 252, 252), argb(255, 252, 252, 255), SK_ColorBLUE),
        floatArrayOf(0f, 0.5f, 0.5f, 1f),
    )
    nextRow()

    gradient(intArrayOf(SK_ColorRED, SK_ColorBLACK, SK_ColorBLUE))
    gradient(
        intArrayOf(SK_ColorRED, argb(255, 3, 0, 0), argb(255, 0, 0, 3), SK_ColorBLUE),
        floatArrayOf(0f, 0.5f, 0.5f, 1f),
    )
    nextRow()

    gradient(intArrayOf(SK_ColorRED, SK_ColorTRANSPARENT, SK_ColorBLUE))
    gradient(
        intArrayOf(SK_ColorRED, argb(0, 3, 0, 0), argb(0, 0, 0, 3), SK_ColorBLUE),
        floatArrayOf(0f, 0.5f, 0.5f, 1f),
    )
    nextRow()

    blackWhiteGradient(c, colorSpace, SkGradient.Interpolation.HueMethod.kShorter)
    nextRow()
    blackWhiteGradient(c, colorSpace, SkGradient.Interpolation.HueMethod.kIncreasing)
    nextRow()
    blackWhiteGradient(c, colorSpace, SkGradient.Interpolation.HueMethod.kDecreasing)
    nextRow()
    blackWhiteGradient(c, colorSpace, SkGradient.Interpolation.HueMethod.kLonger)
    c.restore()
}

private fun blackWhiteGradient(
    canvas: SkCanvas,
    colorSpace: SkGradient.Interpolation.ColorSpace,
    hueMethod: SkGradient.Interpolation.HueMethod,
) {
    val paint = SkPaint()
    paint.shader = SkShaders.LinearGradient(
        arrayOf(SkPoint(0f, 0f), SkPoint(405f, 0f)),
        SkGradient(
            colors = intArrayOf(
                SK_ColorWHITE,
                SK_ColorGRAY,
                SK_ColorWHITE,
                SK_ColorDKGRAY,
                SK_ColorWHITE,
                SK_ColorBLACK,
            ),
            tileMode = SkTileMode.kClamp,
            interpolation = SkGradient.Interpolation(
                colorSpace = colorSpace,
                hueMethod = hueMethod,
            ),
        ),
    )
    canvas.drawRect(SkRect.MakeXYWH(0f, 0f, 405f, 20f), paint)
}

private fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
