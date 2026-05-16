package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.foundation.SkFont

/**
 * Port of upstream Skia's
 * [`gm/offsetimagefilter.cpp`](https://github.com/google/skia/blob/main/gm/offsetimagefilter.cpp)
 * (registered `DEF_GM` name `offsetimagefilter`, 600 × 100, black background).
 *
 * Renders 5 clipped, offset images in a horizontal strip :
 *
 *  - Four cells alternate between an `"e"` glyph image (cells 0, 2) and a
 *    checkerboard image (cells 1, 3). Each cell crops the image by an
 *    increasing inset, then offsets the cropped result by `(i·5, i·10)`
 *    pixels, then clips the draw to the image's bounds. A red 2-px stroked
 *    rectangle is overlaid to visualise the intersection of the clip and
 *    the crop rect.
 *  - The fifth cell crops `fBitmap` to `(0, 0, 100, 100)`, draws **scaled
 *    2×** with an `Offset(-5, -10)` filter, and clips again to the image
 *    bounds.
 *
 * The crop-rect parameter on upstream `SkImageFilters::Offset` is mirrored
 * here by wrapping the offset filter with [SkImageFilters.Crop] (kDecal) at
 * the requested rect — same observable output, equivalent to upstream's
 * "set the output bounds to the crop rect".
 *
 * Reference : `offsetimagefilter.png`, 600 × 100, black background.
 */
public class OffsetImageFilterGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "offsetimagefilter"

    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    private lateinit var bitmap: SkImage
    private lateinit var checkerboard: SkImage

    override fun onOnceBeforeDraw() {
        bitmap = makeStringImage(80, 80, 0xFFD000D0.toInt(), 15, 65, 96, "e")
        checkerboard = makeCheckerboardImage(80, 80, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        val paint = SkPaint()

        for (i in 0 until 4) {
            val image = if (i and 0x01 == 1) checkerboard else bitmap
            val cropRect = SkIRect.MakeXYWH(
                i * 12,
                i * 8,
                image.width - i * 8,
                image.height - i * 12,
            )
            // Equivalent of upstream `Offset(dx, dy, tileInput, &cropRect)` :
            // tile the input through `Image` (so the chain has an explicit
            // source), offset, then crop to the requested bounds.
            val tileInput = SkImageFilters.Image(image, SkSamplingOptions.nearest())
            val dx = i * 5f
            val dy = i * 10f
            val cropRectF = SkRect.Make(cropRect)
            paint.imageFilter = SkImageFilters.Crop(
                cropRectF,
                SkTileMode.kDecal,
                SkImageFilters.Offset(dx, dy, tileInput),
            )
            drawClippedImage(c, image, paint, 1f, cropRect)
            c.translate((image.width + MARGIN).toFloat(), 0f)
        }

        val cropRect = SkIRect.MakeXYWH(0, 0, 100, 100)
        val cropRectF = SkRect.Make(cropRect)
        paint.imageFilter = SkImageFilters.Crop(
            cropRectF,
            SkTileMode.kDecal,
            SkImageFilters.Offset(-5f, -10f, null),
        )
        drawClippedImage(c, bitmap, paint, 2f, cropRect)
    }

    /**
     * Inlined upstream `DrawClippedImage` helper. Clips to the image's
     * native bounds, draws the image at the requested scale through the
     * supplied paint (carrying the offset filter), then overlays a red
     * 2-pixel boundary on the intersection of the clip and the
     * scaled crop rect.
     */
    private fun drawClippedImage(
        canvas: SkCanvas,
        image: SkImage,
        paint: SkPaint,
        scale: Float,
        cropRect: SkIRect,
    ) {
        val clipRect = SkRect.MakeIWH(image.width, image.height)

        canvas.save()
        canvas.clipRect(clipRect)
        canvas.scale(scale, scale)
        canvas.drawImage(image, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()

        // Boundary rect at the intersection of clip and scaled crop rect.
        val scaledCrop = SkMatrix.MakeScale(scale, scale).mapRect(SkRect.Make(cropRect))
        if (clipRect.intersect(scaledCrop)) {
            val strokePaint = SkPaint().apply {
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2f
                color = SK_ColorRED
            }
            canvas.drawRect(clipRect, strokePaint)
        }
    }

    /**
     * Mirrors `ToolUtils::CreateStringImage(w, h, color, x, y, textSize,
     * str)` (`tools/fonts/FontToolUtils.cpp:267`) — allocates an N32-premul
     * raster surface, clears it transparent, draws a single string with the
     * portable font at [textSize] in [color], snapshots to an image.
     */
    private fun makeStringImage(
        w: Int, h: Int, color: SkColor,
        x: Int, y: Int, textSize: Int, str: String,
    ): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(0x00000000)
        val paint = SkPaint().apply { this.color = color }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), textSize.toFloat())
        canvas.drawString(str, x.toFloat(), y.toFloat(), font, paint)
        return surface.makeImageSnapshot()
    }

    /**
     * Inlined `ToolUtils::create_checkerboard_image(w, h, c1, c2, size)` :
     * fill [w]×[h] with [c1] then overlay `size`-pixel [c2] squares in a
     * 2-cell-tiled checker.
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
        private const val HEIGHT: Int = 100
        private const val MARGIN: Int = 12
    }
}
