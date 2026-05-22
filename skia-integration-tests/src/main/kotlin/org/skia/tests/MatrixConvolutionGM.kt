package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import kotlin.math.roundToInt

/**
 * Port of Skia's `gm/matrixconvolution.cpp::MatrixConvolutionGM`
 * (500 × 300). Six variants are registered upstream :
 *  - `matrixconvolution`, `matrixconvolution_color` — basic 3×3 kernel
 *  - `matrixconvolution_big`, `matrixconvolution_big_color` — 7×7
 *  - `matrixconvolution_bigger` — 128×1
 *  - `matrixconvolution_biggest` — 1×255
 *
 * Each variant rasterises a tall "e" through a 2-stop linear gradient,
 * then applies a 3×3 / 7×7 / 128 / 255-tap convolution kernel in three
 * tile modes (Clamp / Decal / Repeat) stacked vertically, three kernel
 * offsets stacked horizontally, with a separate column for cropped
 * inputs (`tileBoundary` → cropRect) and a final `convolveAlpha=false`
 * column.
 *
 * **kanvas-skia adaptation** : [SkImageFilters.MatrixConvolution]'s
 * Kotlin signature doesn't surface the upstream `tileBoundary` (used
 * to constrain the convolution sampling rectangle inside the source).
 * We emulate via the existing `cropRect` overload — close enough for
 * the visual ; the difference is mostly visible on the `kRepeat` tile
 * mode along the cropped column.
 */
public class MatrixConvolutionGM(
    private val colorOne: SkColor,
    private val colorTwo: SkColor,
    private val kernelFixture: KernelFixture,
    private val nameSuffix: String,
) : GM() {

    public enum class KernelFixture { kBasic, kLarge, kLarger, kLargest }

    private val fColors: Array<SkColor4f> = arrayOf(
        SkColor4f.FromColor(colorOne),
        SkColor4f.FromColor(colorTwo),
    )
    private lateinit var fImage: SkImage

    init { setBGColor(0x00000000) }

    override fun getName(): String = "matrixconvolution$nameSuffix"
    override fun getISize(): SkISize = SkISize.Make(500, 300)

    override fun onOnceBeforeDraw() {
        makeBitmap()
    }

    private fun makeBitmap() {
        val info = SkImageInfo.MakeN32Premul(80, 80)
        val surf = SkSurface.MakeRaster(info)
        val paint = SkPaint().apply {
            color = 0xFFFFFFFF.toInt()
            // 2-stop linear gradient from colorOne (top) → colorTwo
            // (bottom of the 80-px stripe).
            shader = SkLinearGradient.Make(
                SkPoint(0f, 0f),
                SkPoint(0f, 80f),
                colors = intArrayOf(colorOne, colorTwo),
                positions = floatArrayOf(0f, 1f),
                tileMode = SkTileMode.kClamp,
            )
        }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 180f)
        surf.canvas.drawString("e", -10f, 80f, font, paint)
        fImage = surf.makeImageSnapshot()
    }

    private fun makeFilter(
        kernelOffsetIn: SkIPoint,
        tileMode: SkTileMode,
        convolveAlpha: Boolean,
    ): SkImageFilter? {
        // The kernelOffset is specified in a 0..2 coordinate space.
        val normalizedXOffset = kernelOffsetIn.fX / 2f
        val normalizedYOffset = kernelOffsetIn.fY / 2f
        // Used as cropRect ; upstream's `tileBoundary` parameter doesn't
        // exist on the kanvas-skia API.
        val tileBoundary = SkRect.MakeWH(fImage.width.toFloat(), fImage.height.toFloat())
        return when (kernelFixture) {
            KernelFixture.kBasic -> {
                val kernelOffset = SkIPoint.Make(
                    (2f * normalizedXOffset).roundToInt(),
                    (2f * normalizedYOffset).roundToInt(),
                )
                val kernel = FloatArray(9) { 1f }
                kernel[4] = -7f
                SkImageFilters.MatrixConvolution(
                    SkISize.Make(3, 3), kernel,
                    gain = 0.3f, bias = 100f,
                    kernelOffset = kernelOffset,
                    tileMode = tileMode,
                    convolveAlpha = convolveAlpha,
                    input = null,
                    cropRect = tileBoundary,
                )
            }
            KernelFixture.kLarge -> {
                val kernelOffset = SkIPoint.Make(
                    (6f * normalizedXOffset).roundToInt(),
                    (6f * normalizedYOffset).roundToInt(),
                )
                val kernel = FloatArray(49) { 1f }
                kernel[24] = -47f
                SkImageFilters.MatrixConvolution(
                    SkISize.Make(7, 7), kernel,
                    0.3f, 100f, kernelOffset, tileMode, convolveAlpha,
                    input = null, cropRect = tileBoundary,
                )
            }
            KernelFixture.kLarger -> {
                val kernelOffset = SkIPoint.Make(
                    (127f * normalizedXOffset).roundToInt(), 0,
                )
                val kernel = FloatArray(128)
                kernel[64] = 0.5f
                kernel[65] = -0.5f
                SkImageFilters.MatrixConvolution(
                    SkISize.Make(128, 1), kernel,
                    0.3f, 100f, kernelOffset, tileMode, convolveAlpha,
                    input = null, cropRect = tileBoundary,
                )
            }
            KernelFixture.kLargest -> {
                val kernelOffset = SkIPoint.Make(
                    0, (254f * normalizedYOffset).roundToInt(),
                )
                val kernel = FloatArray(255)
                kernel[126] = 0.5f
                kernel[128] = -0.5f
                SkImageFilters.MatrixConvolution(
                    SkISize.Make(1, 255), kernel,
                    0.3f, 100f, kernelOffset, tileMode, convolveAlpha,
                    input = null, cropRect = tileBoundary,
                )
            }
        }
    }

    private fun draw(
        canvas: SkCanvas, x: Int, y: Int,
        kernelOffset: SkIPoint,
        tileMode: SkTileMode,
        convolveAlpha: Boolean,
        cropRect: SkRect? = null,
    ) {
        var filter = makeFilter(kernelOffset, tileMode, convolveAlpha)
        if (cropRect != null) {
            filter = SkImageFilters.Crop(cropRect, SkTileMode.kDecal, filter)
        }
        val paint = SkPaint().apply { imageFilter = filter }
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.drawImage(fImage, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        val kernelOffset = SkIPoint.Make(1, 0)
        var ky = kernelOffset.fY
        var x = 10
        while (x < 310) {
            draw(c, x, 10, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kClamp, true)
            draw(c, x, 110, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kDecal, true)
            draw(c, x, 210, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kRepeat, true)
            ky++
            x += 100
        }
        ky = 1
        val smallRect = SkRect.MakeXYWH(10f, 5f, 60f, 60f)
        draw(c, 310, 10,  SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kClamp,  true, smallRect)
        draw(c, 310, 110, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kDecal,  true, smallRect)
        draw(c, 310, 210, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kRepeat, true, smallRect)

        draw(c, 410, 10,  SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kClamp,  false)
        draw(c, 410, 110, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kDecal,  false)
        draw(c, 410, 210, SkIPoint.Make(kernelOffset.fX, ky), SkTileMode.kRepeat, false)
    }
}
