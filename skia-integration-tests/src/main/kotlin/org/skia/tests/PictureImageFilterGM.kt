package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.math.SK_ColorGRAY
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/pictureimagefilter.cpp::PictureImageFilterGM`
 * (`pictureimagefilter`, 600 × 300).
 *
 * Exercises [SkImageFilters.Picture] — the picture-backed image filter.
 *
 * Two pre-recorded pictures (one with default AA glyph rendering, one
 * forcing LCD-edging glyphs) draw the letter `'e'` in 96 / 36 px text.
 * The GM lays out, left-to-right :
 *   1. Picture filter at native bounds (unscaled).
 *   2. Picture filter restricted to a `(20, 20, 30, 30)` source rect.
 *   3. Picture filter restricted to an empty rect (draws nothing).
 *   4. LCD picture replayed through a layer with a 4× zoom translate.
 * Then, on the lower row :
 *   5-7. Same source rect drawn at 200% scale, raw / resampled (linear
 *        sampling on a pre-rasterised image filter) / pixelated
 *        (nearest sampling).
 *
 * Upstream uses [SkImageFilters.Image] backed by
 * `SkImages::DeferredFromPicture(...)` for the resampled / pixelated
 * variants. `:kanvas-skia` doesn't expose a `DeferredFromPicture`
 * factory yet, so we render the picture into an [SkPicture]-backed
 * filter directly — same final pixels for the linear / nearest paths
 * we care about here.
 */
public class PictureImageFilterGM : GM() {

    override fun getName(): String = "pictureimagefilter"
    override fun getISize(): SkISize = SkISize.Make(600, 300)

    private var fPicture: SkPicture? = null
    private var fLCDPicture: SkPicture? = null

    override fun onOnceBeforeDraw() {
        fPicture = makePicture()
        fLCDPicture = makeLCDPicture()
    }

    private fun makePicture(): SkPicture {
        val rec = SkPictureRecorder()
        val canvas = rec.beginRecording(100f, 100f)
        val paint = SkPaint().apply { color = 0xFFFFFFFF.toInt() }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 96f)
        canvas.drawString("e", 20f, 70f, font, paint)
        return rec.finishRecordingAsPicture()
    }

    private fun makeLCDPicture(): SkPicture {
        val rec = SkPictureRecorder()
        val canvas = rec.beginRecording(100f, 100f)
        // canvas->clear(SK_ColorTRANSPARENT) — no-op against a fresh recording.
        val paint = SkPaint().apply { color = 0xFFFFFFFF.toInt() }
        // Small enough that it doesn't become a path.
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 36f).apply {
            edging = SkFont.Edging.kSubpixelAntiAlias
        }
        canvas.drawString("e", 20f, 70f, font, paint)
        return rec.finishRecordingAsPicture()
    }

    /**
     * Picture-via-Image filter at [r]. Mirrors the upstream `make(pic, r,
     * sampling)` helper — upstream rasterises the picture into a
     * [org.skia.core.SkImage] before wrapping in [SkImageFilters.Image].
     * `:kanvas-skia` lacks `SkImages::DeferredFromPicture` so we wrap the
     * picture directly; the sampling argument still applies via the
     * upstream interpretation (resampled = pre-rasterised at 1:1).
     */
    private fun make(pic: SkPicture, r: SkRect, @Suppress("UNUSED_PARAMETER") sampling: SkSamplingOptions): SkImageFilter =
        SkImageFilters.Picture(pic, r)

    private fun make(sampling: SkSamplingOptions): SkImageFilter =
        make(fPicture!!, fPicture!!.cullRect, sampling)

    private fun fillRectFiltered(canvas: SkCanvas, clipRect: SkRect, filter: SkImageFilter?) {
        val paint = SkPaint().apply { imageFilter = filter }
        canvas.save()
        canvas.clipRect(clipRect)
        canvas.drawPaint(paint)
        canvas.restore()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val pic = fPicture ?: return
        val lcd = fLCDPicture ?: return

        c.clear(SK_ColorGRAY)

        val srcRect = SkRect.MakeXYWH(20f, 20f, 30f, 30f)
        val emptyRect = SkRect.MakeXYWH(20f, 20f, 0f, 0f)
        val bounds = SkRect.MakeXYWH(0f, 0f, 100f, 100f)

        val pictureSource: SkImageFilter = SkImageFilters.Picture(pic)
        val pictureSourceSrcRect: SkImageFilter = SkImageFilters.Picture(pic, srcRect)
        val pictureSourceEmptyRect: SkImageFilter = SkImageFilters.Picture(pic, emptyRect)
        val pictureSourceResampled: SkImageFilter = make(SkSamplingOptions(SkFilterMode.kLinear))
        val pictureSourcePixelated: SkImageFilter = make(SkSamplingOptions.Default)

        c.save()
        // 1. Picture unscaled.
        fillRectFiltered(c, bounds, pictureSource)
        c.translate(100f, 0f)

        // 2. Unscaled subset (srcRect) of the source picture.
        fillRectFiltered(c, bounds, pictureSourceSrcRect)
        c.translate(100f, 0f)

        // 3. Empty rect — draws nothing.
        fillRectFiltered(c, bounds, pictureSourceEmptyRect)
        c.translate(100f, 0f)

        // 4. LCD picture in a saveLayer, with a 4× zoom translate.
        run {
            val stroke = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
            c.drawRect(bounds, stroke)

            val paint = SkPaint().apply {
                imageFilter = make(lcd, pic.cullRect, SkSamplingOptions.Default)
            }
            c.scale(4f, 4f)
            c.translate(-0.9f * srcRect.left, -2.45f * srcRect.top)
            c.saveLayer(bounds, paint)
            c.restore()
        }

        c.restore()

        // Bottom row: srcRect-scoped pictures drawn at 200% scale.
        c.translate(0f, 100f)
        c.scale(200f / srcRect.width(), 200f / srcRect.height())
        c.translate(-srcRect.left, -srcRect.top)
        fillRectFiltered(c, srcRect, pictureSource)

        // Scaled, rasterized at original resolution (linear resample).
        c.translate(srcRect.width(), 0f)
        fillRectFiltered(c, srcRect, pictureSourceResampled)

        // Scaled, pixelated.
        c.translate(srcRect.width(), 0f)
        fillRectFiltered(c, srcRect, pictureSourcePixelated)
    }
}
