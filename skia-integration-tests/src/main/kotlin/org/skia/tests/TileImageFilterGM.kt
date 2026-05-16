package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.math.SkColor
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/tileimagefilter.cpp::TileImageFilterGM` (400 × 200).
 *
 * Three blocks demonstrating [SkImageFilters.Tile] :
 *  1. **4-tile sweep** — each iteration `i ∈ 0..3` swaps between a
 *     50×50 string-image (`"e"`) and an 80×80 checkerboard, computes a
 *     `srcRect / dstRect` pair scaled by `i`, and draws the string
 *     image through `Tile(src, dst, Image(image, kLinear))`. A red
 *     outline marks `srcRect`, a blue outline marks `dstRect`.
 *  2. **Tile + identity color-matrix saveLayer** — a 100×100 string
 *     image tiled into a 200×200 dst, fed through an identity
 *     `SkColorFilters.Matrix` so the entire pipeline lives inside a
 *     `saveLayer`. Bounding outlines on top.
 *  3. **Crop-rect inheritance** — a green-fill color filter restricted
 *     to a `(5,5,40,40)` crop, fed into a `Tile(src=50, dst=100)`,
 *     drawing a red filled `dst` rect through the chained filter.
 *
 * **kanvas-skia adaptation** :
 *  - `SkImageFilters::ColorFilter(cf, input, cropRect*)` upstream
 *    accepts a separate `crop` pointer. The kanvas-skia surface only
 *    exposes `ColorFilter(cf, input)`, so we materialise the crop with
 *    an explicit `SkImageFilters.Crop(...)` wrapping a `null`-input
 *    color filter. Semantics match.
 *  - `ToolUtils::CreateStringImage` and `ToolUtils::create_checkerboard_image`
 *    are not yet on `ToolUtils` ; they are inlined here as private
 *    helpers (same recipe as `OffsetImageFilterGM`).
 *
 * C++ source : see `gm/tileimagefilter.cpp`. Reference: `tileimagefilter.png`.
 */
public class TileImageFilterGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
    }

    private lateinit var fBitmap: SkImage
    private lateinit var fCheckerboard: SkImage

    override fun getName(): String = "tileimagefilter"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onOnceBeforeDraw() {
        fBitmap = makeStringImage(50, 50, 0xD000D000.toInt(), 10, 45, 50, "e")
        fCheckerboard = makeCheckerboardImage(80, 80, 0xFFA0A0A0.toInt(), 0xFF404040.toInt(), 8)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val red = SkPaint().apply {
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
        }
        val blue = SkPaint().apply {
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
        }

        var x = 0
        var y = 0

        // Block 1 — 4-tile sweep.
        for (i in 0 until 4) {
            val image: SkImage = if ((i and 0x01) != 0) fCheckerboard else fBitmap
            val srcRect = SkRect.MakeXYWH(
                (image.width / 4).toFloat(),
                (image.height / 4).toFloat(),
                (image.width / (i + 1)).toFloat(),
                (image.height / (i + 1)).toFloat(),
            )
            val dstRect = SkRect.MakeXYWH(
                (i * 8).toFloat(),
                (i * 4).toFloat(),
                (image.width - i * 12).toFloat(),
                (image.height - i * 12).toFloat(),
            )
            val tileInput = SkImageFilters.Image(image, SkSamplingOptions(SkFilterMode.kLinear))
            val filter = SkImageFilters.Tile(srcRect, dstRect, tileInput)

            c.save()
            c.translate(x.toFloat(), y.toFloat())
            val paint = SkPaint().apply { imageFilter = filter }
            c.drawImage(fBitmap, 0f, 0f, SkSamplingOptions.Default, paint)
            c.drawRect(srcRect, red)
            c.drawRect(dstRect, blue)
            c.restore()
            x += image.width + MARGIN
            if (x + image.width > WIDTH) {
                x = 0
                y += image.height + MARGIN
            }
        }

        // Block 2 — Tile(null) → identity color-matrix → saveLayer +
        // drawImage of the string bitmap.
        run {
            val matrix = floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
            val srcRect = SkRect.MakeWH(fBitmap.width.toFloat(), fBitmap.height.toFloat())
            val dstRect = SkRect.MakeWH(
                (fBitmap.width * 2).toFloat(),
                (fBitmap.height * 2).toFloat(),
            )
            val tile = SkImageFilters.Tile(srcRect, dstRect, null)
            val cf = SkColorFilters.Matrix(matrix)
            val paint = SkPaint().apply {
                imageFilter = SkImageFilters.ColorFilter(cf, tile)
            }
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.clipRect(dstRect)
            c.saveLayer(dstRect, paint)
            c.drawImage(fBitmap, 0f, 0f)
            c.restore()
            c.drawRect(srcRect, red)
            c.drawRect(dstRect, blue)
            c.restore()
        }

        // Block 3 — crop rect applied to a green color-filter image
        // filter fed into a tile. The crop is materialised as
        // SkImageFilters.Crop because kanvas-skia's `ColorFilter`
        // surface doesn't accept a `cropRect` directly (see KDoc).
        run {
            c.translate(0f, 100f)

            val srcRect = SkRect.MakeXYWH(0f, 0f, 50f, 50f)
            val dstRect = SkRect.MakeXYWH(0f, 0f, 100f, 100f)
            val cropRect = SkIRect.MakeXYWH(5, 5, 40, 40)
            val greenCF = SkColorFilters.Blend(SK_ColorGREEN, SkBlendMode.kSrc)
            val coloured = SkImageFilters.ColorFilter(greenCF, null)
            val cropped = SkImageFilters.Crop(SkRect.Make(cropRect), coloured)
            val paint = SkPaint().apply {
                color = SK_ColorRED
                imageFilter = SkImageFilters.Tile(srcRect, dstRect, cropped)
            }
            c.drawRect(dstRect, paint)
        }
    }

    // Inlined `ToolUtils::CreateStringImage(w, h, color, x, y, textSize, str)`.
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

    // Inlined `ToolUtils::create_checkerboard_image(w, h, c1, c2, size)`.
    private fun makeCheckerboardImage(w: Int, h: Int, c1: Int, c2: Int, size: Int): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val canvas = surface.canvas
        canvas.clear(c1)
        val paint = SkPaint().apply { color = c2 }
        var yy = 0
        while (yy < h) {
            var xx = (yy / size) % 2 * size
            while (xx < w) {
                canvas.drawRect(
                    SkRect.MakeLTRB(xx.toFloat(), yy.toFloat(),
                        (xx + size).toFloat(), (yy + size).toFloat()),
                    paint,
                )
                xx += 2 * size
            }
            yy += size
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        const val WIDTH: Int = 400
        const val HEIGHT: Int = 200
        const val MARGIN: Int = 12
    }
}
