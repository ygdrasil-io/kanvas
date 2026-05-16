package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkAlphaType
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Port of Skia's `gm/anisotropic.cpp::AnisotropicGM` (the `linear`
 * registration — `anisotropic_image_scale_linear`).
 *
 * Stress-test for image scaling under a linear filter. A 256 × 256
 * synthetic source image is constructed in [onOnceBeforeDraw] (100 thin
 * radial lines drawn out of the centre on a white background), then
 * sampled into a grid of progressively-squashed rects to surface
 * resampling artefacts on the radial-line input. Two banks of rects
 * are drawn :
 *
 *  - **Left column** : vertical minification — `xSize = imageW`,
 *    `ySize = imageH × gScales[i]`.
 *  - **Right column** : horizontal minification — `xSize = imageW × gScales[i]`,
 *    `ySize = imageH`.
 *
 * The 9 gScales (`0.9, 0.8, 0.75, 0.6, 0.5, 0.4, 0.25, 0.2, 0.1`) are
 * laid out in a 5-cell column ; entries past the midpoint are
 * positioned next to their less-squashed counterparts (mirrored layout
 * upstream uses to keep the page compact). The page is exactly
 * `2*kImageSize + 3*kSpacer` wide and `kNumVertImages*kImageSize +
 * (kNumVertImages+1)*kSpacer` tall.
 *
 * **Variant scope** : kanvas-skia's [SkSamplingOptions] only exposes
 * `kNearest` / `kLinear` filtering — [SkMipmapMode] enum exists but
 * mipmap sampling is not implemented in the raster pipeline (see
 * `SkMipmapMode.kt` javadoc). The upstream `_mip` / `_aniso` variants
 * therefore require API gaps to land first ; this port covers only the
 * `_linear` registration, which exercises the `SkFilterMode.kLinear`
 * sampler on a pre-snapshot raster image — a direct shake-out of
 * [SkBitmapShader]'s bilinear minification path.
 *
 * C++ original (`gm/anisotropic.cpp:155`):
 * ```cpp
 * DEF_GM(return new AnisotropicGM(AnisotropicGM::Mode::kLinear);)
 * ```
 */
public class AnisotropicImageScaleLinearGM : GM() {

    init {
        setBGColor(BG_COLOR)
    }

    private var fImage: SkImage? = null

    override fun getName(): String = "anisotropic_image_scale_linear"

    override fun getISize(): SkISize = SkISize.Make(
        2 * kImageSize + 3 * kSpacer,
        kNumVertImages * kImageSize + (kNumVertImages + 1) * kSpacer,
    )

    /**
     * Build the source image once — 100 hairline rays radiating from the
     * centre of a white kOpaque 256 × 256 raster surface. Mirrors the
     * upstream helper in `AnisotropicGM::onOnceBeforeDraw`.
     */
    override fun onOnceBeforeDraw() {
        val info = SkImageInfo.MakeN32(kImageSize, kImageSize, SkAlphaType.kOpaque)
        val surf = SkSurface.MakeRaster(info)
        val canvas = surf.canvas

        canvas.clear(SK_ColorWHITE)

        // Upstream paint : just `setAntiAlias(true)` ; default
        // stroke-width 0 (hairline). The kanvas-skia drawLine routes
        // through drawPath without a style override, so we must set
        // kStroke_Style explicitly to match upstream's `kStroke`
        // shorthand for `drawPoints(kLines, ...)`.
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
        fImage = surf.makeImageSnapshot()
    }

    private fun draw(canvas: SkCanvas, x: Int, y: Int, xSize: Int, ySize: Int) {
        val image = fImage ?: return
        val r = SkRect.MakeXYWH(x.toFloat(), y.toFloat(), xSize.toFloat(), ySize.toFloat())
        canvas.drawImageRect(
            image,
            SkRect.MakeXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            r,
            kLinearSampling,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = fImage ?: return

        val gScales = floatArrayOf(0.9f, 0.8f, 0.75f, 0.6f, 0.5f, 0.4f, 0.25f, 0.2f, 0.1f)

        // Minimise vertically.
        for (i in gScales.indices) {
            val height = floor(img.height * gScales[i]).toInt()

            val yOff: Int = if (i <= gScales.size / 2) {
                kSpacer + i * (img.height + kSpacer)
            } else {
                // Position the more highly squashed images with their
                // less squashed counterparts.
                (gScales.size - i) * (img.height + kSpacer) - height
            }

            draw(c, kSpacer, yOff, img.width, height)
        }

        // Minimise horizontally.
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
        val kLinearSampling: SkSamplingOptions = SkSamplingOptions(SkFilterMode.kLinear)
    }
}
