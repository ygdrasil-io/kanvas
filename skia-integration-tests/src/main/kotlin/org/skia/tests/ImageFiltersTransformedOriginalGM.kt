package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColorChannel
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagefilterstransformed.cpp::ImageFiltersTransformedGM`
 * (registered name `"imagefilterstransformed"`, 420 × 240).
 *
 * Draws image filters with a CTM containing shearing / rotation to check
 * that the scale portion of the CTM is correctly extracted and applied to the
 * image inputs separately from the non-scale portion.
 *
 * Three rows, each containing 5 filters (Blur, DropShadow, DisplacementMap,
 * Dilate, Erode) applied to an oval:
 *  - row 0 : scale(0.8) only
 *  - row 1 : scale(0.8) + rotate(45°)
 *  - row 2 : scale(0.8) + skew(0.5, 0.2)
 *
 * C++ original (collapsed):
 * ```cpp
 * class ImageFiltersTransformedGM : public GM {
 *     ImageFiltersTransformedGM() { this->setBGColor(SK_ColorBLACK); }
 *     SkString getName() const override { return "imagefilterstransformed"; }
 *     SkISize getISize() override { return SkISize::Make(420, 240); }
 *     void onDraw(SkCanvas* canvas) override { ... }
 * };
 * ```
 */
public class ImageFiltersTransformedOriginalGM : GM() {

    private var fCheckerboard: SkImage? = null
    private var fGradientCircle: SkImage? = null

    init {
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = "imagefilterstransformed"
    override fun getISize(): SkISize = SkISize.Make(420, 240)

    override fun onOnceBeforeDraw() {
        fCheckerboard = ToolUtils.create_checkerboard_image(64, 64, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)
        fGradientCircle = makeGradientCircle(64, 64)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val checkerboard = fCheckerboard ?: return
        val gradientCircle = fGradientCircle ?: return

        val gradient = SkImageFilters.Image(gradientCircle, SkSamplingOptions(SkFilterMode.kLinear))
        val checker = SkImageFilters.Image(checkerboard, SkSamplingOptions(SkFilterMode.kLinear))

        val filters = arrayOf(
            SkImageFilters.Blur(12f, 0f, null),
            SkImageFilters.DropShadow(0f, 15f, 8f, 0f, SK_ColorGREEN, null),
            SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kR, 12f, gradient, checker),
            SkImageFilters.Dilate(2, 2, checker),
            SkImageFilters.Erode(2, 2, checker),
        )

        val margin = 20f
        val size = 60f

        for (j in 0 until 3) {
            c.save()
            c.translate(margin, 0f)
            for (i in filters.indices) {
                val paint = SkPaint().apply {
                    color = SK_ColorWHITE
                    imageFilter = filters[i]
                    isAntiAlias = true
                }
                c.save()
                c.translate(size * 0.5f, size * 0.5f)
                c.scale(0.8f, 0.8f)
                when (j) {
                    1 -> c.rotate(45f)
                    2 -> c.skew(0.5f, 0.2f)
                }
                c.translate(-size * 0.5f, -size * 0.5f)
                c.drawOval(SkRect.MakeXYWH(0f, size * 0.1f, size, size * 0.6f), paint)
                c.restore()
                c.translate(size + margin, 0f)
            }
            c.restore()
            c.translate(0f, size + margin)
        }
    }

    private fun makeGradientCircle(width: Int, height: Int): SkImage {
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(x, y) * 0.8f
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(width, height))
        val sc = surface.canvas
        sc.clear(0x00000000)
        val paint = SkPaint().apply {
            shader = SkRadialGradient.Make(
                center = SkPoint(x, y),
                radius = radius,
                colors = intArrayOf(SK_ColorWHITE, SK_ColorBLACK),
                positions = null,
                tileMode = SkTileMode.kClamp,
            )
        }
        sc.drawCircle(x, y, radius, paint)
        return surface.makeImageSnapshot()
    }
}
