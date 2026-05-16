package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Port of Skia's `gm/anisotropic.cpp::AnisotropicGM` (the `kAniso`
 * registration — `anisotropic_image_scale_aniso`).
 *
 * Same source image + grid layout as [AnisotropicImageScaleLinearGM] /
 * [AnisotropicImageScaleMipGM]; the difference is the sampler is
 * `SkSamplingOptions.Aniso(16)` — anisotropic minification with up to
 * 16 N-tap averages along the texture-space major axis. The pre-built
 * mip pyramid is required so the per-tap mip-LOD selection can pick a
 * band-limited level for the minor axis.
 *
 * C++ original (`gm/anisotropic.cpp:157`):
 * ```cpp
 * DEF_GM(return new AnisotropicGM(AnisotropicGM::Mode::kAniso);)
 * ```
 */
public class AnisotropicImageScaleAnisoGM : GM() {

    init {
        setBGColor(BG_COLOR)
    }

    private var fImage: SkImage? = null

    override fun getName(): String = "anisotropic_image_scale_aniso"

    override fun getISize(): SkISize = SkISize.Make(
        2 * kImageSize + 3 * kSpacer,
        kNumVertImages * kImageSize + (kNumVertImages + 1) * kSpacer,
    )

    override fun onOnceBeforeDraw() {
        val info = SkImageInfo.MakeN32(kImageSize, kImageSize, SkAlphaType.kOpaque)
        val surf = SkSurface.MakeRaster(info)
        val canvas = surf.canvas

        canvas.clear(SK_ColorWHITE)

        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        val kNumLines = 100
        val kAngleStep: Float = 360f / kNumLines
        val kInnerOffset = 10

        canvas.translate(kImageSize / 2f, kImageSize / 2f)
        var angleDeg = 0f
        for (i in 0 until kNumLines) {
            val angleRad = angleDeg * (kPI / 180f)
            val s = sin(angleRad)
            val c = cos(angleRad)
            canvas.drawLine(
                c * kInnerOffset, s * kInnerOffset,
                c * kImageSize / 2f, s * kImageSize / 2f,
                p,
            )
            angleDeg += kAngleStep
        }
        fImage = surf.makeImageSnapshot().withDefaultMipmaps()
    }

    private fun draw(canvas: SkCanvas, x: Int, y: Int, xSize: Int, ySize: Int) {
        val image = fImage ?: return
        val r = SkRect.MakeXYWH(x.toFloat(), y.toFloat(), xSize.toFloat(), ySize.toFloat())
        canvas.drawImageRect(
            image,
            SkRect.MakeXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            r,
            kAnisoSampling,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fImage ?: return

        val gScales = floatArrayOf(0.9f, 0.8f, 0.75f, 0.6f, 0.5f, 0.4f, 0.25f, 0.2f, 0.1f)

        for (i in gScales.indices) {
            val height = floor(img.height * gScales[i]).toInt()
            val yOff: Int = if (i <= gScales.size / 2) {
                kSpacer + i * (img.height + kSpacer)
            } else {
                (gScales.size - i) * (img.height + kSpacer) - height
            }
            draw(c, kSpacer, yOff, img.width, height)
        }

        for (i in gScales.indices) {
            val width = floor(img.width * gScales[i]).toInt()
            val xOff: Int
            val yOff: Int
            if (i <= gScales.size / 2) {
                xOff = img.width + 2 * kSpacer
                yOff = kSpacer + i * (img.height + kSpacer)
            } else {
                xOff = img.width + 2 * kSpacer + img.width - width
                yOff = kSpacer + (gScales.size - i - 1) * (img.height + kSpacer)
            }
            draw(c, xOff, yOff, width, img.height)
        }
    }

    private companion object {
        const val kImageSize: Int = 256
        const val kSpacer: Int = 10
        const val kNumVertImages: Int = 5
        const val kPI: Float = 3.14159265358979323846f
        val BG_COLOR: Int = SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC)
        val kAnisoSampling: SkSamplingOptions = SkSamplingOptions.Aniso(16)
    }
}
