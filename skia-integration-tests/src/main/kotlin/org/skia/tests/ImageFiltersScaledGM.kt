package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkColorChannel
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPerlinNoiseShader
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/imagefiltersscaled.cpp::ImageFiltersScaledGM`
 * (`DEF_GM(return new ImageFiltersScaledGM;)`, name
 * `"imagefiltersscaled"`, 1428 × 500).
 *
 * For each of 5 row scales `{0.5, 1, (1,2), (2,1), 2}`, walks a 10-filter
 * row :
 *
 *  0. `Blur(4, 4)`
 *  1. `DropShadow(5, 10, 3, 3, YELLOW)`
 *  2. `DisplacementMap(R, R, 12, gradient, checkerboard)`
 *  3. `Dilate(1, 1, checkerboard)`
 *  4. `Erode(1, 1, checkerboard)`
 *  5. `Offset(32, 0)` (with a pre-translate of `-32` to keep the
 *     filtered circle aligned with the unfiltered cell).
 *  6. `MatrixTransform(scale 4×, default sampling)` (with a
 *     pre-scale of `1/4` so the net cell size is unchanged).
 *  7. `Shader(fractal-noise 0.1, 0.05, octaves 1, seed 0)`
 *  8. `PointLitDiffuse(@(0,0,10), white, scale 1, kd 2)`
 *  9. `SpotLitDiffuse(@(-10,-10,20) → (40,40,0), exp 1,
 *     cutoff 15°, white, scale 1, kd 2)`
 *
 * Each filter draws a blue antialiased circle of radius `r.w·2/5`
 * inside a 64×64 cell, clipped to the cell, on a `SK_ColorBLACK`
 * background.
 */
public class ImageFiltersScaledGM : GM() {

    init { setBGColor(0x00000000) }

    override fun getName(): String = "imagefiltersscaled"
    override fun getISize(): SkISize = SkISize.Make(1428, 500)

    private lateinit var fCheckerboard: SkImage
    private lateinit var fGradientCircle: SkImage

    override fun onOnceBeforeDraw() {
        fCheckerboard = makeCheckerboardImage(64, 64, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)
        fGradientCircle = makeGradientCircle(64, 64)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val gradient: SkImageFilter = SkImageFilters.Image(fGradientCircle, SkSamplingOptions(SkFilterMode.kLinear))
        val checkerboard: SkImageFilter = SkImageFilters.Image(fCheckerboard, SkSamplingOptions(SkFilterMode.kLinear))

        val pointLocation = floatArrayOf(0f, 0f, 10f)
        val spotLocation = floatArrayOf(-10f, -10f, 20f)
        val spotTarget = floatArrayOf(40f, 40f, 0f)
        val spotExponent = 1f
        val cutoffAngle = 15f
        val kd = 2f
        val surfaceScale = 1f
        val white = SK_ColorWHITE
        val resizeMatrix = SkMatrix.MakeScale(RESIZE_FACTOR, RESIZE_FACTOR)

        val filters: Array<SkImageFilter?> = arrayOf(
            SkImageFilters.Blur(4f, 4f, null),
            SkImageFilters.DropShadow(5f, 10f, 3f, 3f, SK_ColorYELLOW, null),
            SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kR, 12f, gradient, checkerboard),
            SkImageFilters.Dilate(1, 1, checkerboard),
            SkImageFilters.Erode(1, 1, checkerboard),
            SkImageFilters.Offset(32f, 0f, null),
            SkImageFilters.MatrixTransform(resizeMatrix, SkSamplingOptions.Default, null),
            SkImageFilters.Shader(SkPerlinNoiseShader.MakeFractalNoise(0.1f, 0.05f, 1, 0f)),
            SkImageFilters.PointLitDiffuse(pointLocation, white, surfaceScale, kd, null),
            SkImageFilters.SpotLitDiffuse(
                spotLocation, spotTarget, spotExponent, cutoffAngle,
                white, surfaceScale, kd, null,
            ),
        )

        val scales = arrayOf(
            SkPoint(0.5f, 0.5f),
            SkPoint(1f, 1f),
            SkPoint(1f, 2f),
            SkPoint(2f, 1f),
            SkPoint(2f, 2f),
        )

        val r = SkRect.MakeWH(64f, 64f)
        val margin = 16f

        for (j in scales.indices) {
            c.save()
            for (i in filters.indices) {
                val paint = SkPaint().apply {
                    color = SK_ColorBLUE
                    imageFilter = filters[i]
                    isAntiAlias = true
                }
                c.save()
                c.scale(scales[j].fX, scales[j].fY)
                c.clipRect(r)
                if (i == 5) {
                    c.translate(-32f, 0f)
                } else if (i == 6) {
                    c.scale(1f / RESIZE_FACTOR, 1f / RESIZE_FACTOR)
                }
                c.drawCircle(r.centerX(), r.centerY(), r.width() * 2f / 5f, paint)
                c.restore()
                c.translate(r.width() * scales[j].fX + margin, 0f)
            }
            c.restore()
            c.translate(0f, r.height() * scales[j].fY + margin)
        }
    }

    private fun makeGradientCircle(width: Int, height: Int): SkImage {
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(x, y) * 4f / 5f
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(width, height))
        val canvas = surface.canvas
        canvas.clear(0x00000000)
        val paint = SkPaint().apply {
            shader = SkRadialGradient.Make(
                SkPoint(x, y), radius,
                intArrayOf(SK_ColorWHITE, SK_ColorBLACK),
                null,
                SkTileMode.kClamp,
            )
        }
        canvas.drawCircle(x, y, radius, paint)
        return surface.makeImageSnapshot()
    }

    private fun makeCheckerboardImage(w: Int, h: Int, c1: Int, c2: Int, size: Int): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(c1)
        val paint = SkPaint().apply { color = c2 }
        var y = 0
        while (y < h) {
            var x = (y / size) % 2 * size
            while (x < w) {
                canvas.drawRect(
                    SkRect.MakeLTRB(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    paint,
                )
                x += 2 * size
            }
            y += size
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        private const val RESIZE_FACTOR: Float = 4f
    }
}
