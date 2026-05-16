package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SkColorChannel
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/displacement.cpp::DisplacementMapGM` (DEF_GM,
 * name `displacement`, 600 × 500).
 *
 * Builds five displacement-map combinations across four rows × five
 * columns. The displacement source toggles between checkerboards of
 * different sizes (square 80², 64², 96², 96×64, 64×96) and a "g"
 * glyph rasterised onto an 80×80 surface in `0xFF884422`. Each panel
 * is clipped to the 80×80 glyph image's bounds before drawing.
 *
 * **Adaptations**:
 *  - Upstream's `SkImageFilters::DisplacementMap(...,
 *    SkIRect* cropRect)` 6-arg overload isn't exposed by
 *    `:kanvas-skia` — for the cropped panels (rows 3 & 4), we wrap
 *    the result with [SkImageFilters.Crop] using the same rect
 *    (semantic equivalent : crop the displacement output).
 *  - `ToolUtils::CreateStringImage(80, 80, 0xFF884422, 15, 55, 96,
 *    "g")` isn't a shared helper — we inline a 1-shot offscreen
 *    raster (orange `g` at baseline `(15, 55)`, 96 pt portable
 *    typeface).
 *  - `ToolUtils::create_checkerboard_image(w, h, c1, c2, 8)` is
 *    similarly inlined.
 */
public class DisplacementGM : GM() {

    init { setBGColor(0xFF000000.toInt()) }

    override fun getName(): String = "displacement"
    override fun getISize(): SkISize = SkISize.Make(600, 500)

    private lateinit var fImage: SkImage
    private lateinit var fCheckerboard: SkImage
    private lateinit var fSmall: SkImage
    private lateinit var fLarge: SkImage
    private lateinit var fLargeW: SkImage
    private lateinit var fLargeH: SkImage

    override fun onOnceBeforeDraw() {
        fImage = makeStringImage(80, 80, 0xFF884422.toInt(), 15f, 55f, 96f, "g")

        val c1 = ToolUtils.colorTo565(0xFF244484.toInt())
        val c2 = ToolUtils.colorTo565(0xFF804020.toInt())

        fCheckerboard = makeCheckerboardImage(80, 80, c1, c2, 8)
        fSmall = makeCheckerboardImage(64, 64, c1, c2, 8)
        fLarge = makeCheckerboardImage(96, 96, c1, c2, 8)
        fLargeW = makeCheckerboardImage(96, 64, c1, c2, 8)
        fLargeH = makeCheckerboardImage(64, 96, c1, c2, 8)
    }

    private fun drawClippedBitmap(canvas: SkCanvas, x: Int, y: Int, paint: SkPaint) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(SkRect.MakeWH(fImage.width.toFloat(), fImage.height.toFloat()))
        canvas.drawImage(fImage, 0f, 0f, SkSamplingOptions.Default, paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        val paint = SkPaint()
        var displ: SkImageFilter = SkImageFilters.Image(fCheckerboard, SkSamplingOptions(SkFilterMode.kLinear))

        // Row 1 — no crop, scales 0, 16, 32, 48, 64.
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kG, 0f, displ, null)
        drawClippedBitmap(c, 0, 0, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kB, SkColorChannel.kA, 16f, displ, null)
        drawClippedBitmap(c, 100, 0, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kB, 32f, displ, null)
        drawClippedBitmap(c, 200, 0, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 48f, displ, null)
        drawClippedBitmap(c, 300, 0, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kA, 64f, displ, null)
        drawClippedBitmap(c, 400, 0, paint)

        // Row 2 — no crop, fixed scale 40, channel-pair rotation.
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kG, 40f, displ, null)
        drawClippedBitmap(c, 0, 100, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kB, SkColorChannel.kA, 40f, displ, null)
        drawClippedBitmap(c, 100, 100, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kB, 40f, displ, null)
        drawClippedBitmap(c, 200, 100, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 40f, displ, null)
        drawClippedBitmap(c, 300, 100, paint)
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kA, 40f, displ, null)
        drawClippedBitmap(c, 400, 100, paint)

        // Crop rect for rows 3 & 4.
        val cropRect = SkRect.MakeXYWH(30f, 30f, 40f, 40f)
        // Wrap each filter in Crop(cropRect, …) since DisplacementMap
        // doesn't accept a cropRect parameter in :kanvas-skia.
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kG, 0f, displ, null))
        drawClippedBitmap(c, 0, 200, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kB, SkColorChannel.kA, 16f, displ, null))
        drawClippedBitmap(c, 100, 200, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kB, 32f, displ, null))
        drawClippedBitmap(c, 200, 200, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 48f, displ, null))
        drawClippedBitmap(c, 300, 200, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kA, 64f, displ, null))
        drawClippedBitmap(c, 400, 200, paint)

        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kG, 40f, displ, null))
        drawClippedBitmap(c, 0, 300, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kB, SkColorChannel.kA, 40f, displ, null))
        drawClippedBitmap(c, 100, 300, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kB, 40f, displ, null))
        drawClippedBitmap(c, 200, 300, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 40f, displ, null))
        drawClippedBitmap(c, 300, 300, paint)
        paint.imageFilter = SkImageFilters.Crop(cropRect, SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kA, 40f, displ, null))
        drawClippedBitmap(c, 400, 300, paint)

        // Test for negative scale.
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, -40f, displ, null)
        drawClippedBitmap(c, 500, 0, paint)

        // Different-size displacement bitmaps.
        displ = SkImageFilters.Image(fSmall, SkSamplingOptions(SkFilterMode.kLinear))
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kG, 40f, displ, null)
        drawClippedBitmap(c, 0, 400, paint)
        displ = SkImageFilters.Image(fLarge, SkSamplingOptions(SkFilterMode.kLinear))
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kB, SkColorChannel.kA, 40f, displ, null)
        drawClippedBitmap(c, 100, 400, paint)
        displ = SkImageFilters.Image(fLargeW, SkSamplingOptions(SkFilterMode.kLinear))
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kR, SkColorChannel.kB, 40f, displ, null)
        drawClippedBitmap(c, 200, 400, paint)
        displ = SkImageFilters.Image(fLargeH, SkSamplingOptions(SkFilterMode.kLinear))
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 40f, displ, null)
        drawClippedBitmap(c, 300, 400, paint)

        // No displacement input → fallback to the rasterised source.
        paint.imageFilter = SkImageFilters.DisplacementMap(SkColorChannel.kG, SkColorChannel.kA, 40f, null, null)
        drawClippedBitmap(c, 400, 400, paint)
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
}
