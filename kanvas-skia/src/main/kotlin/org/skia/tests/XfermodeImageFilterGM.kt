package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/xfermodeimagefilter.cpp::XfermodeImageFilterGM`
 * (`xfermodeimagefilter`, 600 × 700, BG `SK_ColorBLACK`).
 *
 * Renders a grid of `80 × 80` cells, each clipped to the cell bounds and
 * painted via a paint whose [SkPaint.imageFilter] composites a 96-pt `"e"`
 * glyph against an 8-pixel checkerboard background through different
 * blend mode selections :
 *
 *  1. The 29 canonical [SkBlendMode]s applied to `Blend(mode, background)`
 *     — the foreground is the rasterised source (the green `"e"` bitmap
 *     drawn into the cell) and the background is the [SkImageFilters.Image]
 *     wrapper around the checkerboard.
 *  2. An `Arithmetic(0, 1, 1, 0, true, background, null)` cell — same
 *     semantics as a `Plus`-blend with `enforcePMColor`.
 *  3. A `Blend(SrcOver, background)` repeat — exercises the no-op blend
 *     case after the Arithmetic cell.
 *  4. Two offset-Blend cells (`SrcOver` then `Darken`) where both inputs
 *     are wrapped in [SkImageFilters.Offset] before going into the blend.
 *  5. Three "cropped" cells — same offset inputs, with a [SkImageFilters.Crop]
 *     wrapper applied to the blend output (mirrors upstream's `cropRect`
 *     parameter on `SkImageFilters::Blend`, which our [SkImageFilters.Blend]
 *     factory doesn't take).
 *  6. Three "small bg / large fg" combinations using [SkBlendMode.kScreen]
 *     and [SkBlendMode.kSrcIn] with a cropped foreground filter.
 */
public class XfermodeImageFilterGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "xfermodeimagefilter"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private lateinit var fBitmap: SkImage
    private lateinit var fCheckerboard: SkImage

    override fun onOnceBeforeDraw() {
        fBitmap = makeStringImage(80, 80, 0xFFD000D0.toInt(), 15, 65, 96, "e")
        fCheckerboard = makeCheckerboardImage(80, 80, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        val paint = SkPaint()

        val gModes = arrayOf(
            SkBlendMode.kClear, SkBlendMode.kSrc, SkBlendMode.kDst, SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver, SkBlendMode.kSrcIn, SkBlendMode.kDstIn, SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut, SkBlendMode.kSrcATop, SkBlendMode.kDstATop, SkBlendMode.kXor,
            SkBlendMode.kPlus, SkBlendMode.kModulate, SkBlendMode.kScreen, SkBlendMode.kOverlay,
            SkBlendMode.kDarken, SkBlendMode.kLighten, SkBlendMode.kColorDodge, SkBlendMode.kColorBurn,
            SkBlendMode.kHardLight, SkBlendMode.kSoftLight, SkBlendMode.kDifference, SkBlendMode.kExclusion,
            SkBlendMode.kMultiply, SkBlendMode.kHue, SkBlendMode.kSaturation, SkBlendMode.kColor,
            SkBlendMode.kLuminosity,
        )

        var x = 0
        var y = 0
        val background: SkImageFilter = SkImageFilters.Image(fCheckerboard, SkSamplingOptions.nearest())

        for (mode in gModes) {
            paint.imageFilter = SkImageFilters.Blend(mode, background)
            drawClippedBitmap(c, fBitmap, paint, x, y)
            x += fBitmap.width + MARGIN
            if (x + fBitmap.width > WIDTH) {
                x = 0
                y += fBitmap.height + MARGIN
            }
        }

        // Arithmetic cell.
        paint.imageFilter = SkImageFilters.Arithmetic(0f, 1f, 1f, 0f, true, background, null)
        drawClippedBitmap(c, fBitmap, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        // Null-mode (SrcOver) cell.
        paint.imageFilter = SkImageFilters.Blend(SkBlendMode.kSrcOver, background)
        drawClippedBitmap(c, fBitmap, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        val clipRect = SkRect.MakeWH((fBitmap.width + 4).toFloat(), (fBitmap.height + 4).toFloat())

        val foreground: SkImageFilter = SkImageFilters.Image(fBitmap, SkSamplingOptions.nearest())
        val offsetForeground: SkImageFilter = SkImageFilters.Offset(4f, -4f, foreground)
        val offsetBackground: SkImageFilter = SkImageFilters.Offset(4f, 4f, background)

        // Offsets on SrcMode (fixed-function blend).
        paint.imageFilter = SkImageFilters.Blend(SkBlendMode.kSrcOver, offsetBackground, offsetForeground)
        drawClippedPaint(c, clipRect, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        // Offsets on Darken (shader blend).
        paint.imageFilter = SkImageFilters.Blend(SkBlendMode.kDarken, offsetBackground, offsetForeground)
        drawClippedPaint(c, clipRect, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        // Cropping cells. Upstream takes a `&cropRect` arg on Blend ;
        // our [SkImageFilters.Blend] factory has no such overload, so we
        // wrap the result in [SkImageFilters.Crop] (kDecal) to apply the
        // crop bounds.
        val sampledModes = arrayOf(SkBlendMode.kOverlay, SkBlendMode.kSrcOver, SkBlendMode.kPlus)
        val offsets = arrayOf(
            intArrayOf(10, 10, -16, -16),
            intArrayOf(10, 10, 10, 10),
            intArrayOf(-10, -10, -6, -6),
        )
        for (i in 0 until 3) {
            val cropRect = SkIRect.MakeXYWH(
                offsets[i][0],
                offsets[i][1],
                fBitmap.width + offsets[i][2],
                fBitmap.height + offsets[i][3],
            )
            val blended = SkImageFilters.Blend(sampledModes[i], offsetBackground, offsetForeground)
            paint.imageFilter = SkImageFilters.Crop(SkRect.Make(cropRect), SkTileMode.kDecal, blended)
            drawClippedPaint(c, clipRect, paint, x, y)
            x += fBitmap.width + MARGIN
            if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }
        }

        // Small bg, large fg with Screen.
        val cropRect60 = SkIRect.MakeXYWH(10, 10, 60, 60)
        val cropped: SkImageFilter = SkImageFilters.Crop(
            SkRect.Make(cropRect60),
            SkTileMode.kDecal,
            SkImageFilters.Offset(0f, 0f, foreground),
        )
        paint.imageFilter = SkImageFilters.Blend(SkBlendMode.kScreen, cropped, background)
        drawClippedPaint(c, clipRect, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        // Small fg, large bg with Screen.
        paint.imageFilter = SkImageFilters.Blend(SkBlendMode.kScreen, background, cropped)
        drawClippedPaint(c, clipRect, paint, x, y)
        x += fBitmap.width + MARGIN
        if (x + fBitmap.width > WIDTH) { x = 0; y += fBitmap.height + MARGIN }

        // Small fg, large bg with SrcIn + full-size crop.
        val cropRectFull = SkIRect.MakeXYWH(0, 0, 80, 80)
        val srcInBlend = SkImageFilters.Blend(SkBlendMode.kSrcIn, background, cropped)
        paint.imageFilter = SkImageFilters.Crop(SkRect.Make(cropRectFull), SkTileMode.kDecal, srcInBlend)
        drawClippedPaint(c, clipRect, paint, x, y)
    }

    private fun drawClippedBitmap(canvas: SkCanvas, image: SkImage, paint: SkPaint, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(SkRect.MakeIWH(image.width, image.height))
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    private fun drawClippedPaint(canvas: SkCanvas, rect: SkRect, paint: SkPaint, x: Int, y: Int) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(rect)
        canvas.drawPaint(paint)
        canvas.restore()
    }

    /**
     * Mirrors `ToolUtils::CreateStringBitmap(w, h, color, x, y, textSize, str)`
     * — allocates an N32-premul raster surface, clears it transparent,
     * draws a single string with the portable font at [textSize] in
     * [color], snapshots to an image.
     */
    private fun makeStringImage(
        w: Int, h: Int, color: Int, x: Int, y: Int, textSize: Int, str: String,
    ): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(0)
        val paint = SkPaint().apply { this.color = color }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), textSize.toFloat())
        canvas.drawString(str, x.toFloat(), y.toFloat(), font, paint)
        return surface.makeImageSnapshot()
    }

    /** Inlined `ToolUtils::create_checkerboard_image(w, h, c1, c2, size)`. */
    private fun makeCheckerboardImage(w: Int, h: Int, c1: Int, c2: Int, size: Int): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(c1)
        val paint = SkPaint().apply { this.color = c2 }
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
        private const val WIDTH: Int = 600
        private const val HEIGHT: Int = 700
        private const val MARGIN: Int = 12
    }
}
