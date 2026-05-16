package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bitmapfilters.cpp::FilterGM`
 * (`DEF_GM(return new FilterGM;)`, name `"bitmapfilters"`, 540 × 250).
 *
 * Builds a 2×2 bitmap (red / green / blue / white quadrants) in three
 * colour types — N32, ARGB-4444 and RGB-565 — then draws each at 32×
 * magnification with three sampling configurations (`SkSamplingOptions()`,
 * `SkSamplingOptions(kLinear)`, linear + dither), repeated once at
 * alpha 0.5. Each row is labelled with its colour-type name.
 *
 * **Adaptation** — `:kanvas-skia` has no `kRGB_565` backing for [SkBitmap],
 * so the 565 row falls back to N32 storage (label still reads "565").
 * The 4444 row is built via a `kSrc`-blend draw onto a 4444-backed
 * canvas, same recipe as [CopyTo4444GM].
 */
public class BitmapFiltersGM : GM() {

    init {
        setBGColor(0xFFDDDDDD.toInt())
    }

    override fun getName(): String = "bitmapfilters"
    override fun getISize(): SkISize = SkISize.Make(540, 250)

    private var img32: SkImage? = null
    private var img4444: SkImage? = null
    private var img565: SkImage? = null

    override fun onOnceBeforeDraw() {
        val bm32 = makeBm()
        img32 = bm32.asImage()
        img4444 = copyTo(bm32, SkColorType.kARGB_4444).asImage()
        img565 = copyTo(bm32, SkColorType.kRGB_565).asImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)
        var y = drawRow(c, img4444!!, "ARGB_4444")
        c.translate(0f, y)
        y = drawRow(c, img565!!, "RGB_565")
        c.translate(0f, y)
        drawRow(c, img32!!, "RGBA_8888")
    }

    private fun makeBm(): SkBitmap {
        val bm = SkBitmap.allocPixels(
            SkImageInfo.Make(2, 2, SkColorType.kRGBA_8888, SkAlphaType.kPremul),
        )
        bm.setPixel(0, 0, SK_ColorRED)
        bm.setPixel(1, 0, SK_ColorGREEN)
        bm.setPixel(0, 1, SK_ColorBLUE)
        bm.setPixel(1, 1, SK_ColorWHITE)
        return bm
    }

    private fun copyTo(src: SkBitmap, ct: SkColorType): SkBitmap {
        val info = when (ct) {
            SkColorType.kARGB_4444 -> SkImageInfo.Make(src.width, src.height, ct, SkAlphaType.kPremul)
            // 565 falls back to N32 (no 565 storage in :kanvas-skia).
            SkColorType.kRGB_565 -> SkImageInfo.MakeN32(src.width, src.height, SkAlphaType.kOpaque)
            else -> SkImageInfo.MakeN32(src.width, src.height, SkAlphaType.kPremul)
        }
        val bm = SkBitmap.allocPixels(info)
        val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
        SkCanvas(bm).drawImage(src.asImage(), 0f, 0f, SkSamplingOptions.Default, paint)
        return bm
    }

    private fun drawRow(canvas: SkCanvas, img: SkImage, colorTypeName: String): Float {
        canvas.save()
        val paint = SkPaint().apply { isAntiAlias = true }
        val scale = 32
        val font = ToolUtils.DefaultPortableFont()
        canvas.drawString(colorTypeName, 0f, (img.height * scale * 5 / 8).toFloat(), font, paint)
        canvas.translate(48f, 0f)
        canvas.scale(scale.toFloat(), scale.toFloat())

        var x = 0f
        // First series — opaque
        x += drawSet(canvas, img, x, paint)
        val p2 = SkPaint().apply { alphaf = 0.5f }
        // Second series — alpha 0.5
        drawSet(canvas, img, x, p2)
        canvas.restore()
        return x * scale / 3f
    }

    private fun drawSet(canvas: SkCanvas, img: SkImage, x0: Float, paint: SkPaint): Float {
        var x = x0
        x += drawBm(canvas, img, x, 0f, SkSamplingOptions(), paint)
        x += drawBm(canvas, img, x, 0f, SkSamplingOptions(SkFilterMode.kLinear), paint)
        paint.isDither = true
        return x + drawBm(canvas, img, x, 0f, SkSamplingOptions(SkFilterMode.kLinear), paint)
    }

    private fun drawBm(canvas: SkCanvas, img: SkImage, x: Float, y: Float, sampling: SkSamplingOptions, paint: SkPaint): Float {
        canvas.drawImage(img, x, y, sampling, paint)
        return img.width.toFloat() * 5f / 4f
    }
}
