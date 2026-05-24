package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/animatedimageblurs.cpp::AnimatedTiledImageBlur`
 * (the second of three GMs declared in that file).
 *
 * Draws `images/mandrill_512.png` blurred through a
 * [SkImageFilters.Blur] with four different [SkTileMode] values —
 * `kDecal`, `kClamp`, `kRepeat`, `kMirror` — arranged in a 2×2 grid.
 * Each quadrant is a 250×250 destination rect containing an
 * [SkImageFilters.Blur] whose `cropRect` is the same [SkRect] so that
 * the blur kernel's edge behaviour is immediately visible.
 *
 * **Animation.** Upstream animates `fBlurSigma` via [TimeUtils::PingPong]
 * between 0 and 250. For the static reference snapshot we use
 * `0.3 * 250 = 75f` (the constructor initialiser in upstream) which is
 * what DM captures on the first frame (t=0 of the animation clock).
 *
 * **API mapping.**
 * ```cpp
 * paint.setImageFilter(SkImageFilters::Blur(fBlurSigma, fBlurSigma, tileMode, nullptr, rect));
 * canvas->drawImageRect(fImage, rect, SkFilterMode::kLinear, &paint);
 * ```
 * maps to
 * ```kotlin
 * paint.imageFilter = SkImageFilters.Blur(sigma, sigma, tileMode, null, rect.toIRect())
 * canvas.drawImageRect(image, rect, rect, SkSamplingOptions(SkFilterMode.kLinear), paint)
 * ```
 * The upstream `cropRect` parameter in `SkImageFilters::Blur` is typed as
 * `SkRect` in the public header and converted to an `SkIRect` internally;
 * our [SkImageFilters.Blur] accepts an [org.graphiks.math.SkIRect] — we
 * convert via [SkRect.roundOut].
 */
public class AnimatedTiledImageBlurGM : GM() {

    companion object {
        private const val kMaxBlurSigma: Float = 250f
    }

    init {
        setBGColor(SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC))
    }

    override fun getName(): String = "animated-tiled-image-blur"
    override fun getISize(): SkISize = SkISize.Make(530, 530)

    // t=0 snapshot sigma: matches upstream `AnimatedTiledImageBlur()` ctor
    // which sets `fBlurSigma(0.3f * kMaxBlurSigma)`.
    private var blurSigma: Float = 0.3f * kMaxBlurSigma

    private var image: SkImage? = null

    override fun onOnceBeforeDraw() {
        image = ToolUtils.GetResourceAsImage("images/mandrill_512.png")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val img = image ?: return

        fun drawBlurredImage(tx: Float, ty: Float, tileMode: SkTileMode) {
            val rect = SkRect.MakeIWH(250, 250)
            c.save()
            c.translate(tx, ty)
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Blur(
                    blurSigma, blurSigma, tileMode, null,
                    org.graphiks.math.SkIRect.MakeWH(250, 250),
                )
            }
            c.drawImageRect(img, rect, rect, SkSamplingOptions(SkFilterMode.kLinear), paint)
            c.restore()
        }

        drawBlurredImage(10f,  10f,  SkTileMode.kDecal)
        drawBlurredImage(270f, 10f,  SkTileMode.kClamp)
        drawBlurredImage(10f,  270f, SkTileMode.kRepeat)
        drawBlurredImage(270f, 270f, SkTileMode.kMirror)
    }
}
