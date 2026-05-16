package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bitmapcopy.cpp::BitmapCopyGM`
 * (`DEF_GM(return new BitmapCopyGM;)`, name `"bitmapcopy"`, 540 × 330).
 *
 * Allocates a 40×40 N32 source bitmap drawn with a 4-quadrant colour
 * pattern (red / green / blue / yellow), then for each of three target
 * colour types (kRGB_565, kARGB_4444, kN32) "copies" the source bitmap
 * to a fresh destination bitmap and renders the destination next to a
 * label.
 *
 * **Adaptation** :
 *  - kRGB_565 is **not** implemented in `:kanvas-skia`'s [SkBitmap]
 *    (`setPixel` errors). We substitute kRGBA_8888 storage for the
 *    "565" cell — the label still reads "565" but the rendered pixels
 *    will not exhibit 5/6/5 banding. This produces minor colour drift
 *    in the 565 column vs the upstream reference.
 *  - kARGB_4444 copy is performed via a kSrc-blend draw onto a
 *    4444-backed [SkCanvas] (same recipe as [CopyTo4444GM]), since
 *    `:kanvas-skia` doesn't expose a `bitmap-to-bitmap readPixels`.
 */
public class BitmapCopyGM : GM() {

    init {
        setBGColor(0xFFDDDDDD.toInt())
    }

    override fun getName(): String = "bitmapcopy"
    override fun getISize(): SkISize = SkISize.Make(540, 330)

    private val dst: Array<SkBitmap?> = arrayOfNulls(NUM_CONFIGS)

    override fun onOnceBeforeDraw() {
        val src = SkBitmap.allocPixels(SkImageInfo.MakeN32(40, 40, SkAlphaType.kOpaque))
        val canvasTmp = SkCanvas(src)
        drawChecks(canvasTmp, 40, 40)

        for (i in 0 until NUM_CONFIGS) {
            dst[i] = copyTo(src, COLOR_TYPES[i])
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        val horizMargin = 10f
        val vertMargin = 10f

        c.clear(0xFFDDDDDD.toInt())

        val font = ToolUtils.DefaultPortableFont()

        var width = 40f
        var height = 40f
        if (font.getSpacing() > height) height = font.getSpacing()
        for (i in 0 until NUM_CONFIGS) {
            val name = COLOR_TYPE_LABELS[i]
            val tw = font.measureText(name, name.length, SkTextEncoding.kUTF8)
            if (tw > width) width = tw
        }

        val horizOffset = width + horizMargin
        val vertOffset = height + vertMargin
        c.translate(20f, 20f)

        for (i in 0 until NUM_CONFIGS) {
            c.save()
            val name = COLOR_TYPE_LABELS[i]
            val tw = font.measureText(name, name.length, SkTextEncoding.kUTF8)
            val x = (width - tw) / 2f
            val y = font.getSpacing() / 2f
            c.drawSimpleText(name, name.length, SkTextEncoding.kUTF8, x, y, font, paint)

            c.translate(0f, vertOffset)
            val tx = (width - 40f) / 2f
            c.drawImage(dst[i]!!.asImage(), tx, 0f, SkSamplingOptions(), paint)
            c.restore()

            c.translate(horizOffset, 0f)
        }
    }

    private fun copyTo(src: SkBitmap, ct: SkColorType): SkBitmap {
        val info = when (ct) {
            SkColorType.kARGB_4444 -> SkImageInfo.Make(src.width, src.height, ct, SkAlphaType.kPremul)
            // kRGB_565 not implemented — fall back to kRGBA_8888.
            SkColorType.kRGB_565 -> SkImageInfo.MakeN32(src.width, src.height, SkAlphaType.kOpaque)
            else -> SkImageInfo.MakeN32(src.width, src.height, SkAlphaType.kPremul)
        }
        val bm = SkBitmap.allocPixels(info)
        val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
        SkCanvas(bm).drawImage(src.asImage(), 0f, 0f, SkSamplingOptions.Default, paint)
        return bm
    }

    private fun drawChecks(canvas: SkCanvas, w: Int, h: Int) {
        val p = SkPaint()
        p.color = SK_ColorRED
        canvas.drawRect(SkRect.MakeIWH(w / 2, h / 2), p)
        p.color = SK_ColorGREEN
        canvas.drawRect(SkRect.MakeLTRB((w / 2).toFloat(), 0f, w.toFloat(), (h / 2).toFloat()), p)
        p.color = SK_ColorBLUE
        canvas.drawRect(SkRect.MakeLTRB(0f, (h / 2).toFloat(), (w / 2).toFloat(), h.toFloat()), p)
        p.color = SK_ColorYELLOW
        canvas.drawRect(SkRect.MakeLTRB((w / 2).toFloat(), (h / 2).toFloat(), w.toFloat(), h.toFloat()), p)
    }

    private companion object {
        private val COLOR_TYPES: Array<SkColorType> = arrayOf(
            SkColorType.kRGB_565,
            SkColorType.kARGB_4444,
            SkColorType.kRGBA_8888,
        )
        // Match upstream's `color_type_name` returns for these three types.
        private val COLOR_TYPE_LABELS: Array<String> = arrayOf("565", "4444", "8888")
        private const val NUM_CONFIGS: Int = 3
    }
}
