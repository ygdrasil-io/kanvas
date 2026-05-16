package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/clippedbitmapshaders.cpp::ClippedBitmapShadersGM`
 * (six variants : `clipped-bitmap-shaders-{tile,mirror,clamp}[-hq]`).
 *
 * Draws a 3×3 grid (center cell omitted) of clipped rectangles filled
 * by a [SkBitmap.makeShader]-backed paint. The shader's local matrix
 * scales the 2×2 source bitmap by 8× and centres the pattern over the
 * empty middle cell. Repeat / Mirror / Clamp tile modes give visually
 * distinct outputs ; `hq = true` switches sampling to Mitchell bicubic.
 */
public class ClippedBitmapShadersGM(
    private val mode: SkTileMode,
    private val hq: Boolean = false,
) : GM() {

    override fun getName(): String {
        val descriptor = when (mode) {
            SkTileMode.kRepeat -> "tile"
            SkTileMode.kMirror -> "mirror"
            SkTileMode.kClamp -> "clamp"
            SkTileMode.kDecal -> "decal"
        }
        val base = "clipped-bitmap-shaders-$descriptor"
        return if (hq) "$base-hq" else base
    }

    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bmp = createBitmap()

        // s = scale(8) then translate(SLIDE_SIZE/2)
        val s = SkMatrix.MakeScale(8f, 8f).postTranslate(SLIDE_SIZE / 2f, SLIDE_SIZE / 2f)
        val sampling = if (hq) SkSamplingOptions(SkCubicResampler.Mitchell) else SkSamplingOptions()
        val paint = SkPaint().apply {
            shader = bmp.makeShader(mode, mode, sampling, s)
        }

        val margin = (SLIDE_SIZE / 3f - RECT_SIZE) / 2f
        for (i in 0 until 3) {
            val yOrigin = SLIDE_SIZE / 3f * i + margin
            for (j in 0 until 3) {
                val xOrigin = SLIDE_SIZE / 3f * j + margin
                if (i == 1 && j == 1) continue
                val rect = SkRect.MakeXYWH(xOrigin, yOrigin, RECT_SIZE, RECT_SIZE)
                c.save()
                c.clipRect(rect)
                c.drawRect(rect, paint)
                c.restore()
            }
        }
    }

    private fun createBitmap(): SkBitmap {
        val bm = SkBitmap.allocPixels(
            SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kPremul),
        )
        bm.setPixel(0, 0, SK_ColorRED)
        bm.setPixel(1, 0, SK_ColorGREEN)
        bm.setPixel(0, 1, SK_ColorBLACK)
        bm.setPixel(1, 1, SK_ColorBLUE)
        return bm
    }

    private companion object {
        private const val RECT_SIZE: Float = 64f
        private const val SLIDE_SIZE: Float = 300f
    }
}
