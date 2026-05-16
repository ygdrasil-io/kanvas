package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIPoint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefiltersgraph.cpp::ImageFiltersGraphGM`
 * (`DEF_GM(return new ImageFiltersGraphGM;)`, name
 * `"imagefiltersgraph"`, 600 × 150).
 *
 * Walks 6 image-filter graphs, each drawn 100 pixels apart, exercising
 * the filter DAG :
 *
 * 1. `Merge(Blur(Image(e)), ColorFilter(Erode(Blur(Image(e)))))`
 *    drawn via `drawPaint`.
 * 2. `Blend(SrcOver, ColorFilter(Matrix(α·0.5), Dilate(5,5)))` over `e`.
 * 3. `Arithmetic(0, 1, 1, 0, ColorFilter(Matrix(α·0.5)),
 *    Offset(10,10, ColorFilter(Matrix)))` over `e`.
 * 4. `Blend(SrcIn, Blur(10,10), nullptr, &cropRect)` over `e`.
 * 5. `MatrixConvolution([3,3] {-1..7..-1}, Dilate(5,5))` over `e`.
 * 6. `ColorFilter(Blend(GREEN, SrcIn), ColorFilter(Blend(BLUE, SrcIn),
 *    nullptr, &outerRect), &innerRect)` painted on a 100×100 red rect.
 *
 * **Adaptations** — kanvas-skia's `SkImageFilters` factories don't
 * expose the trailing `cropRect` argument on every filter (only
 * `Blur`). Where the upstream uses a crop on a `ColorFilter` /
 * `Blend`, we wrap with [SkImageFilters.Crop] at the same rect.
 *
 * The source image is built locally via [makeStringImage] (a port of
 * `ToolUtils::CreateStringImage(100, 100, SK_ColorWHITE, 20, 70, 96,
 * "e")`).
 */
public class ImageFiltersGraphGM : GM() {

    override fun getName(): String = "imagefiltersgraph"
    override fun getISize(): SkISize = SkISize.Make(600, 150)

    private lateinit var fImage: SkImage

    override fun onOnceBeforeDraw() {
        fImage = makeStringImage(100, 100, SK_ColorWHITE, 20f, 70f, 96f, "e")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        // ── 1 ── Merge(Blur, ColorFilter(Erode(Blur))).
        run {
            val bitmapSource: SkImageFilter =
                SkImageFilters.Image(fImage, SkSamplingOptions(SkFilterMode.kLinear))
            val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
            val blur = SkImageFilters.Blur(4f, 4f, bitmapSource)
            val erode = SkImageFilters.Erode(4, 4, blur)
            val color = SkImageFilters.ColorFilter(cf, erode)
            val merge = SkImageFilters.Merge(blur, color)
            val paint = SkPaint().apply { imageFilter = merge }
            c.drawPaint(paint)
            c.translate(100f, 0f)
        }

        // ── 2 ── Blend(SrcOver, ColorFilter(Matrix(α·0.5), Dilate)).
        run {
            val morph = SkImageFilters.Dilate(5, 5, null)
            val matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.5f, 0f,
            )
            val matrixFilter = SkColorFilters.Matrix(matrix)
            val colorMorph = SkImageFilters.ColorFilter(matrixFilter, morph)
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Blend(SkBlendMode.kSrcOver, colorMorph)
            }
            drawClippedImage(c, fImage, paint)
            c.translate(100f, 0f)
        }

        // ── 3 ── Arithmetic(0,1,1,0, ColorFilter(Matrix(α·0.5)),
        //                    Offset(10,10, ColorFilter(Matrix(α·0.5)))).
        run {
            val matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 0.5f, 0f,
            )
            val matrixCF = SkColorFilters.Matrix(matrix)
            val matrixFilter = SkImageFilters.ColorFilter(matrixCF, null)
            val offsetFilter = SkImageFilters.Offset(10f, 10f, matrixFilter)

            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Arithmetic(
                    0f, 1f, 1f, 0f, true,
                    matrixFilter, offsetFilter,
                )
            }
            drawClippedImage(c, fImage, paint)
            c.translate(100f, 0f)
        }

        // ── 4 ── Blend(SrcIn, Blur(10,10), nullptr, &cropRect).
        run {
            val blur = SkImageFilters.Blur(10f, 10f, null)
            val cropRect = SkRect.Make(SkIRect.MakeWH(95, 100))
            // Upstream's trailing crop param isn't exposed on Blend; wrap
            // the result with Crop(cropRect).
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.Crop(
                    cropRect,
                    SkImageFilters.Blend(SkBlendMode.kSrcIn, blur, null),
                )
            }
            drawClippedImage(c, fImage, paint)
            c.translate(100f, 0f)
        }

        // ── 5 ── MatrixConvolution([3,3] sharpen) over Dilate(5,5).
        run {
            val dilate = SkImageFilters.Dilate(5, 5, null)
            val kernel = floatArrayOf(
                -1f, -1f, -1f,
                -1f, 7f, -1f,
                -1f, -1f, -1f,
            )
            val convolve = SkImageFilters.MatrixConvolution(
                SkISize.Make(3, 3), kernel,
                gain = 1f, bias = 0f,
                kernelOffset = SkIPoint(1, 1),
                tileMode = SkTileMode.kClamp,
                convolveAlpha = false,
                input = dilate,
            )
            val paint = SkPaint().apply { imageFilter = convolve }
            drawClippedImage(c, fImage, paint)
            c.translate(100f, 0f)
        }

        // ── 6 ── ColorFilter(GREEN/SrcIn, ColorFilter(BLUE/SrcIn,
        //                    null, outerRect), innerRect) on red rect.
        run {
            val cf1 = SkColorFilters.Blend(SK_ColorBLUE, SkBlendMode.kSrcIn)
            val cf2 = SkColorFilters.Blend(SK_ColorGREEN, SkBlendMode.kSrcIn)
            val outerRect = SkRect.Make(SkIRect.MakeXYWH(10, 10, 80, 80))
            val innerRect = SkRect.Make(SkIRect.MakeXYWH(20, 20, 60, 60))
            // Same pattern as filters above: wrap with Crop(rect).
            val color1 = SkImageFilters.Crop(
                outerRect,
                SkImageFilters.ColorFilter(cf1, null),
            )
            val color2 = SkImageFilters.Crop(
                innerRect,
                SkImageFilters.ColorFilter(cf2, color1),
            )

            val paint = SkPaint().apply {
                imageFilter = color2
                color = SK_ColorRED
            }
            c.drawRect(SkRect.MakeXYWH(0f, 0f, 100f, 100f), paint)
            c.translate(100f, 0f)
        }
    }

    private fun drawClippedImage(canvas: SkCanvas, image: SkImage, paint: SkPaint) {
        canvas.save()
        canvas.clipRect(SkRect.MakeWH(image.width.toFloat(), image.height.toFloat()))
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    private fun makeStringImage(
        w: Int, h: Int, color: Int,
        x: Float, y: Float, fontSize: Float, text: String,
    ): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(0x00000000)
        val paint = SkPaint().apply { this.color = color; isAntiAlias = true }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), fontSize)
        canvas.drawString(text, x, y, font, paint)
        return surface.makeImageSnapshot()
    }
}
