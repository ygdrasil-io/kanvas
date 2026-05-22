package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/gradients_no_texture.cpp::LinearGradientGM`
 * (500 × 500). Two variants are registered upstream :
 *  - `linear_gradient`           (dither = true)
 *  - `linear_gradient_nodither`  (dither = false)
 *
 * Renders 100 stacked 5-px-tall linear-gradient bars whose width grows
 * by `+30 px` per row (from 540 to 540+99·30 px), each carrying a
 * white-green-white triple stop at fractional positions that follow
 * the same `unitPos / (540 + index·30)` schedule as upstream.
 *
 * **kanvas-skia adaptation** : [SkPaint.isDither] is plumbed through but
 * the F16 raster path doesn't perform classic 8-bit dithering — the
 * `_nodither` and `_dither` variants therefore look identical visually.
 */
public class LinearGradientGM(private val fDither: Boolean = true) : GM() {

    private val kWidthBump = 30f
    private val kHeight = 5f
    private val kMinWidth = 540f
    private val kCount = 100

    private val fShader: Array<SkShader?> = arrayOfNulls(kCount)

    override fun getName(): String =
        if (fDither) "linear_gradient" else "linear_gradient_nodither"

    override fun getISize(): SkISize = SkISize.Make(500, 500)

    override fun onOnceBeforeDraw() {
        val white = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
        val green = SkColorSetARGB(0xFF, 0x00, 0x82, 0x00)
        // Six-stop white / white / green / green / white / white
        // arrangement (clamped). Stops 0, 50, 70, 500, 540 expressed
        // in unit space ; stop 5 is pinned to 1.
        val colors = intArrayOf(white, white, green, green, white, white)
        val unitPos = floatArrayOf(0f, 50f, 70f, 500f, 540f)

        for (i in 0 until kCount) {
            val p0 = SkPoint(0f, 0f)
            val p1 = SkPoint(500f + i * kWidthBump, 0f)
            val width = kMinWidth + i * kWidthBump
            val pos = FloatArray(6)
            for (inner in unitPos.indices) {
                pos[inner] = unitPos[inner] / width
            }
            pos[5] = 1f
            fShader[i] = SkLinearGradient.Make(
                p0, p1,
                colors = colors,
                positions = pos,
                tileMode = SkTileMode.kClamp,
            )
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            isDither = fDither
        }
        for (i in 0 until kCount) {
            paint.shader = fShader[i]
            c.drawRect(
                SkRect.MakeLTRB(0f, i * kHeight, kMinWidth + i * kWidthBump, (i + 1) * kHeight),
                paint,
            )
        }
    }
}

/** Convenience subclass used by tests targeting `linear_gradient_nodither.png`. */
public class LinearGradientNoDitherGM : GM() {
    private val gm = LinearGradientGM(fDither = false)
    override fun getName(): String = gm.name()
    override fun getISize(): SkISize = gm.size()
    override fun onDraw(canvas: SkCanvas?) { gm.draw(canvas) }
}
