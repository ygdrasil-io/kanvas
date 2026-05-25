package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShaders

/**
 * Mirrors the many-stop OKLCH slice from upstream `gm/gradients.cpp`.
 */
public class GradientsColorSpaceManyStopsGM : GM() {

    override fun getName(): String = "gradients_color_space_many_stops"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorGRAY)
        val stopCount = 200
        val colors = IntArray(stopCount) { i ->
            val t = i.toFloat() / (stopCount - 1).toFloat()
            val lerp = if (t < 0.5f) t * 2f else (1f - t) * 2f
            val r = (0 + (255 - 0) * lerp + 0.5f).toInt().coerceIn(0, 255)
            val g = (0 + (255 - 0) * lerp + 0.5f).toInt().coerceIn(0, 255)
            val b = (255 + (0 - 255) * t + 0.5f).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val positions = FloatArray(stopCount) { it.toFloat() / (stopCount - 1).toFloat() }
        val paint = SkPaint().apply {
            shader = SkShaders.LinearGradient(
                arrayOf(SkPoint(0f, 0f), SkPoint(500f, 500f)),
                SkGradient(
                    colors = colors,
                    positions = positions,
                    interpolation = SkGradient.Interpolation(
                        colorSpace = SkGradient.Interpolation.ColorSpace.kOKLCH,
                    ),
                ),
            )
        }
        c.drawRect(SkRect.MakeWH(500f, 500f), paint)
    }
}
