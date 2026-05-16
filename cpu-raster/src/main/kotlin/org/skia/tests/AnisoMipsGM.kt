package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/anisotropic.cpp::AnisoMipsGM`. Exercises mip-aware
 * sampling under a sequence of progressively more-anisotropic scales,
 * both via `drawImage` and via `image.makeShader` with the same
 * [SkSamplingOptions.Aniso] sampler.
 *
 * The GM draws two grids (one for `drawImage`, one for `makeShader`)
 * of 4×4 cells. Each cell carries a re-rendered source image whose
 * colour is `kColors[c % 4]` and which is mipped via
 * [SkImage.withDefaultMipmaps] right after the snapshot. The cell-
 * specific `(sx, sy)` scale is applied to the canvas before the draw.
 *
 * Reference : `anisomips.png` (520 × 260).
 */
public class AnisoMipsGM : GM() {

    override fun getName(): String = "anisomips"

    override fun getISize(): SkISize = SkISize.Make(520, 260)

    private fun updateImage(surf: SkSurface, color: SkColor): SkImage {
        surf.canvas.clear(color)
        val paint = SkPaint().apply {
            this.color = color.inv() or 0xFF000000.toInt()
        }
        surf.canvas.drawRect(
            SkRect.MakeLTRB(
                surf.width * 2f / 5f, surf.height * 2f / 5f,
                surf.width * 3f / 5f, surf.height * 3f / 5f,
            ),
            paint,
        )
        return surf.makeImageSnapshot().withDefaultMipmaps()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val info = SkImageInfo.MakeN32(kImageSize, kImageSize, SkAlphaType.kPremul)
        val surface = SkSurface.MakeRaster(info)
        val kScales = floatArrayOf(1f, 0.5f, 0.25f, 0.125f)
        val kColors = intArrayOf(0xFFF0F0F0.toInt(), SK_ColorBLUE, SK_ColorGREEN, SK_ColorRED)
        val kSampling = SkSamplingOptions.Aniso(16)

        for (shaderPass in booleanArrayOf(false, true)) {
            var ci = 0
            c.save()
            for (sy in kScales) {
                c.save()
                for (sx in kScales) {
                    c.save()
                    c.scale(sx, sy)
                    val image = updateImage(surface, kColors[ci])
                    if (shaderPass) {
                        val paint = SkPaint().apply {
                            shader = image.makeShader(kSampling)
                        }
                        c.drawRect(SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()), paint)
                    } else {
                        c.drawImage(image, 0f, 0f, kSampling)
                    }
                    c.restore()
                    c.translate(info.width * sx + kPad, 0f)
                    ci = (ci + 1) % kColors.size
                }
                c.restore()
                c.translate(0f, info.width * sy + kPad)
            }
            c.restore()
            for (sx in kScales) {
                c.translate(info.width * sx + kPad, 0f)
            }
        }
    }

    private companion object {
        const val kImageSize: Int = 128
        const val kPad: Float = 5f
    }
}
