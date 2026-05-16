package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkEmbossMaskFilter
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/emboss.cpp::EmbossGM` (600 × 120).
 *
 * Walks a small image-and-shape sequence to exercise the
 * [SkEmbossMaskFilter] pipeline :
 *  1. Plain bitmap (a 100 × 100 AA black filled circle).
 *  2. Same bitmap with an emboss mask filter.
 *  3. Emboss + sRGB-blended red colour filter — historically
 *     crashed, kept here as a regression marker.
 *  4. A stroked blue circle with a different emboss recipe.
 *  5. Two `Hello` / `World` strings drawn with the same paint
 *     (paint mutated between draws to swap shader & colour).
 */
public class EmbossGM : GM() {

    override fun getName(): String = "emboss"
    override fun getISize(): SkISize = SkISize.Make(600, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint()
        val img = makeBm()

        c.drawImage(img, 10f, 10f)
        c.translate(img.width.toFloat() + 10f, 0f)

        paint.maskFilter = SkEmbossMaskFilter.Make(
            convertRadiusToSigma(3f),
            SkEmbossMaskFilter.Light(
                direction = floatArrayOf(1f, 1f, 1f),
                ambient = 128,
                specular = 16 * 2,
            ),
        )
        c.drawImage(img, 10f, 10f, SkSamplingOptions.Default, paint)
        c.translate(img.width.toFloat() + 10f, 0f)

        // emboss + colorfilter combo — was the crashing regression upstream.
        paint.colorFilter = SkColorFilters.Blend(0xFFFF0000.toInt(), SkBlendMode.kSrcATop)
        c.drawImage(img, 10f, 10f, SkSamplingOptions.Default, paint)
        c.translate(img.width.toFloat() + 10f, 0f)

        paint.isAntiAlias = true
        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 10f
        paint.maskFilter = SkEmbossMaskFilter.Make(
            convertRadiusToSigma(4f),
            SkEmbossMaskFilter.Light(
                direction = floatArrayOf(1f, 1f, 1f),
                ambient = 128,
                specular = 16 * 2,
            ),
        )
        paint.colorFilter = null
        paint.color = SK_ColorBLUE
        paint.isDither = true
        c.drawCircle(50f, 50f, 30f, paint)
        c.translate(100f, 0f)

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 50f)
        paint.style = SkPaint.Style.kFill_Style
        c.drawString("Hello", 0f, 50f, font, paint)

        paint.color = SK_ColorGREEN
        c.drawString("World", 0f, 100f, font, paint)
    }

    private fun makeBm(): SkImage {
        val info = SkImageInfo.MakeN32Premul(100, 100)
        val surf = SkSurface.MakeRaster(info)
        val paint = SkPaint().apply { isAntiAlias = true }
        surf.canvas.drawCircle(50f, 50f, 50f, paint)
        return surf.makeImageSnapshot()
    }

    public companion object {
        /** Mirrors Skia's `SkBlurMask::ConvertRadiusToSigma(r)`. */
        public fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
