package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
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
 * Port of Skia's `gm/imagefiltersclipped.cpp::ImageFiltersClippedGM`
 * (`DEF_GM(return new ImageFiltersClippedGM;)`, name
 * `"imagefiltersclipped"`, 860 × 500).
 *
 * Walks an 8-filter table over a 5-row × 8-col grid, sliding the clip
 * rect leftward by `xOffset = {0, 16, 32, 48, 64}` per row so each
 * filter draws the same circle through progressively narrower clip
 * rects. A 9th column on the right draws a Perlin-noise fractal-noise
 * shader through the same 5-row clip-narrowing pattern.
 *
 * **Adaptations** :
 *  - `PointLitDiffuse(... cropRect)` — upstream's 6-arg overload with
 *    a trailing crop rect isn't exposed by `:kanvas-skia`. We wrap the
 *    [SkImageFilters.PointLitDiffuse] output with [SkImageFilters.Crop]
 *    using the same crop rect (semantic-equivalent: crop the diffuse
 *    output to `r`).
 *  - `ToolUtils::create_checkerboard_image` isn't a shared helper here ;
 *    we inline a [makeCheckerboardImage] private to this file (same
 *    8-pixel checker, 0xFFA0A0A0 / 0xFF404040 palette).
 *
 * C++ original (class form) — see `gm/imagefiltersclipped.cpp` ;
 * the DEF_SIMPLE_GM `imagefilter_convolve_subset` is ported separately
 * in [ImageFilterConvolveSubsetGM].
 */
public class ImageFiltersClippedGM : GM() {

    init {
        setBGColor(0x00000000)
    }

    override fun getName(): String = "imagefiltersclipped"

    override fun getISize(): SkISize = SkISize.Make(860, 500)

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
        val resizeMatrix = SkMatrix.MakeScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y)
        // Upstream uses SkPoint3{32, 32, 10} for the point light's
        // (x, y, z). Our PointLitDiffuse takes a FloatArray location.
        val pointLocation = floatArrayOf(32f, 32f, 10f)

        val r = SkRect.MakeWH(64f, 64f)
        val filters: Array<SkImageFilter?> = arrayOf(
            SkImageFilters.Blur(12f, 12f, null),
            SkImageFilters.DropShadow(10f, 10f, 3f, 3f, SK_ColorGREEN, null),
            SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kR, 12f, gradient, checkerboard),
            SkImageFilters.Dilate(2, 2, checkerboard),
            SkImageFilters.Erode(2, 2, checkerboard),
            SkImageFilters.Offset(-16f, 32f, null),
            SkImageFilters.MatrixTransform(resizeMatrix, SkSamplingOptions.Default, null),
            // Crop output of lighting to the checkerboard rect `r` — upstream's
            // 6-arg PointLitDiffuse(..., cropRect = r). Our factory doesn't
            // accept a cropRect, so we wrap the result with Crop(r).
            SkImageFilters.Crop(r, SkImageFilters.PointLitDiffuse(pointLocation, SK_ColorWHITE, 1f, 2f, checkerboard)),
        )

        val margin = 16f
        val bounds = SkRect.MakeWH(64f, 64f)
        bounds.outset(margin, margin)

        c.save()
        var xOffset = 0
        while (xOffset < 80) {
            c.save()
            bounds.left = xOffset.toFloat()
            for (i in filters.indices) {
                drawClippedFilter(c, filters[i], i, r, bounds)
                c.translate(r.width() + margin, 0f)
            }
            c.restore()
            c.translate(0f, r.height() + margin)
            xOffset += 16
        }
        c.restore()

        // Rightmost column — fractal-noise shader piped through the same
        // clip-narrowing 5-row pattern.
        val noise = SkPerlinNoiseShader.MakeFractalNoise(0.1f, 0.05f, 1, 0f)
        val rectFilter: SkImageFilter = SkImageFilters.Shader(noise)
        c.translate(filters.size * (r.width() + margin), 0f)
        xOffset = 0
        while (xOffset < 80) {
            bounds.left = xOffset.toFloat()
            drawClippedFilter(c, rectFilter, 0, r, bounds)
            c.translate(0f, r.height() + margin)
            xOffset += 16
        }
    }

    /**
     * Mirrors upstream's `draw_clipped_filter(canvas, filter, i,
     * primBounds, clipBounds)` — paints a circle inside `primBounds`
     * after clipping to `clipBounds`, with an index-specific transform
     * for slots 5 (Offset) and 6 (MatrixTransform).
     */
    private fun drawClippedFilter(
        canvas: SkCanvas,
        filter: SkImageFilter?,
        i: Int,
        primBounds: SkRect,
        clipBounds: SkRect,
    ) {
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            imageFilter = filter
            isAntiAlias = true
        }
        canvas.save()
        canvas.clipRect(clipBounds)
        if (i == 5) {
            canvas.translate(16f, -32f)
        } else if (i == 6) {
            canvas.scale(1f / RESIZE_FACTOR_X, 1f / RESIZE_FACTOR_Y)
        }
        canvas.drawCircle(primBounds.centerX(), primBounds.centerY(),
            primBounds.width() * 2f / 5f, paint)
        canvas.restore()
    }

    /**
     * Mirrors `make_gradient_circle(width, height)` — a radial gradient
     * from white at the centre to black at `radius = 0.8 · min(w/2, h/2)`,
     * masked to a circle of the same radius via [SkCanvas.drawCircle].
     */
    private fun makeGradientCircle(width: Int, height: Int): SkImage {
        val x = width / 2f
        val y = height / 2f
        val radius = minOf(x, y) * 0.8f
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

    /**
     * Inlined `ToolUtils::create_checkerboard_image(w, h, c1, c2,
     * checkSize)` — fills a w×h surface with c1 and overlays
     * `checkSize`-pixel c2 squares in a 2-cell-tiled checker, matching
     * upstream's `draw_checkerboard` recipe.
     */
    private fun makeCheckerboardImage(w: Int, h: Int, c1: Int, c2: Int, size: Int): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(c1)
        val paint = SkPaint().apply { color = c2 }
        var y = 0
        while (y < h) {
            var x = (y / size) % 2 * size
            while (x < w) {
                canvas.drawRect(SkRect.MakeLTRB(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()), paint)
                x += 2 * size
            }
            y += size
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        private const val RESIZE_FACTOR_X: Float = 2f
        private const val RESIZE_FACTOR_Y: Float = 5f
    }
}
