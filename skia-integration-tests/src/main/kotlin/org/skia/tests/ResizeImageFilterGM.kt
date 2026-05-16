package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/resizeimagefilter.cpp::ResizeGM` GM
 * (`resizeimagefilter`, 630 × 100, background transparent black).
 *
 * Exercises [SkImageFilters.MatrixTransform] as a "down-then-up"
 * resize step :
 *
 *  - Five 96 × 96 panels at horizontal stride `96 + 10 px`,
 *    each rendering a `kClamp` clipped saveLayer over the panel
 *    rect, then drawing a 4-pixel-inset oval into that layer.
 *    The layer's paint carries a
 *    [SkImageFilters.MatrixTransform] that scales the layer
 *    contents down to 16 × 16 via the inverse of the on-canvas
 *    scale, then back up — the resampling kernel of the inner
 *    [SkSamplingOptions] is what each panel showcases :
 *      1. nearest  — `SkSamplingOptions()`.
 *      2. linear   — `SkSamplingOptions(kLinear)`.
 *      3. mip-linear — `SkSamplingOptions(kLinear, kLinear)`.
 *      4. Mitchell cubic — `SkSamplingOptions(SkCubicResampler::Mitchell())`.
 *      5. anisotropic 16-tap — `SkSamplingOptions::Aniso(16)`.
 *
 *  - A sixth panel chains an Image filter (`Image(16×16 oval bitmap,
 *    (-4, -4, 20, 20) → (-24, -24, 96, 96), cubic({1/3, 1/3}))`) as
 *    the input of a Mitchell `MatrixTransform`, validating filter
 *    composition.
 *
 * C++ original :
 * ```cpp
 * SkISize getISize() override { return SkISize::Make(630, 100); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->clear(SK_ColorBLACK);
 *     const SkSamplingOptions samplings[] = {
 *         SkSamplingOptions(),
 *         SkSamplingOptions(SkFilterMode::kLinear),
 *         SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear),
 *         SkSamplingOptions(SkCubicResampler::Mitchell()),
 *         SkSamplingOptions::Aniso(16),
 *     };
 *     const SkRect srcRect = SkRect::MakeWH(96, 96);
 *     const SkSize deviceSize = SkSize::Make(16, 16);
 *     for (const auto& sampling : samplings) {
 *         this->draw(canvas, srcRect, deviceSize, sampling, nullptr);
 *         canvas->translate(srcRect.width() + 10, 0);
 *     }
 *     {
 *         // sixth panel — see implementation.
 *     }
 * }
 * ```
 */
public class ResizeImageFilterGM : GM() {

    init {
        setBGColor(0x00000000)
    }

    override fun getName(): String = "resizeimagefilter"

    override fun getISize(): SkISize = SkISize.Make(630, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.clear(SK_ColorBLACK)

        val samplings: Array<SkSamplingOptions> = arrayOf(
            SkSamplingOptions(),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
            // Skia's `SkCubicResampler::Mitchell()` = (B=1/3, C=1/3).
            SkSamplingOptions(SkCubicResampler(1f / 3f, 1f / 3f)),
            SkSamplingOptions.Aniso(16),
        )
        val srcRect = SkRect.MakeWH(96f, 96f)
        val deviceW = 16f
        val deviceH = 16f

        for (sampling in samplings) {
            draw(c, srcRect, deviceW, deviceH, sampling, input = null)
            c.translate(srcRect.width() + 10f, 0f)
        }

        // Sixth panel — Image filter (16×16 oval) used as input to a
        // Mitchell MatrixTransform.
        run {
            val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(16, 16))
            val sc = surface.canvas
            sc.clear(0x00000000)
            val paint = SkPaint().apply { color = 0xFF00FF00.toInt() }
            val ovalRect = SkRect.MakeWH(16f, 16f).apply { inset(2f / 3f, 2f / 3f) }
            sc.drawOval(ovalRect, paint)
            val image: SkImage = surface.makeImageSnapshot()

            val inRect = SkRect.MakeXYWH(-4f, -4f, 20f, 20f)
            val outRect = SkRect.MakeXYWH(-24f, -24f, 120f, 120f)
            val source: SkImageFilter? = SkImageFilters.Image(
                image, inRect, outRect,
                SkSamplingOptions(SkCubicResampler(1f / 3f, 1f / 3f)),
            )
            draw(c, srcRect, deviceW, deviceH, samplings[3], input = source)
        }
    }

    /**
     * Mirrors the C++ `draw(canvas, rect, deviceSize, sampling,
     * input)` helper :
     *
     *  - Translate to `(rect.x, rect.y)` and scale by the
     *    canvas-to-device ratio (here 16 / 96 ≈ 0.167) so the layer
     *    contents are rasterised at the smaller device size.
     *  - Build a [SkImageFilters.MatrixTransform] whose matrix is
     *    the inverse scale — when the layer is composited back, the
     *    filter resamples the small bitmap up to the original size
     *    using [sampling].
     *  - `saveLayer(rect, paint{imageFilter=filter})` ; draw a
     *    `kClamp` 4-px inset oval in green into the layer ; restore.
     */
    private fun draw(
        canvas: SkCanvas,
        rect: SkRect,
        deviceW: Float, deviceH: Float,
        sampling: SkSamplingOptions,
        input: SkImageFilter?,
    ) {
        // Upstream maps `rect` through the current CTM to get the
        // *device* rect, then computes the device-scale. At call time
        // the CTM has only been translated (panel-by-panel translate)
        // — no scale yet — so `mapRect` is the identity on width /
        // height. Skip the matrix mapping and use `rect.width/height`
        // directly.
        val dstW = rect.width()
        val dstH = rect.height()
        canvas.save()
        val deviceScaleX = deviceW / dstW
        val deviceScaleY = deviceH / dstH
        canvas.translate(rect.left, rect.top)
        canvas.scale(deviceScaleX, deviceScaleY)
        canvas.translate(-rect.left, -rect.top)

        val matrix = SkMatrix.MakeScale(1f / deviceScaleX, 1f / deviceScaleY)
        val filter: SkImageFilter? = SkImageFilters.MatrixTransform(matrix, sampling, input)

        val filteredPaint = SkPaint().apply { imageFilter = filter }
        canvas.saveLayer(rect, filteredPaint)

        val paint = SkPaint().apply { color = 0xFF00FF00.toInt() }
        val ovalRect = SkRect.MakeLTRB(rect.left, rect.top, rect.right, rect.bottom).apply {
            inset(4f, 4f)
        }
        canvas.drawOval(ovalRect, paint)

        canvas.restore() // saveLayer
        canvas.restore() // save
    }
}
