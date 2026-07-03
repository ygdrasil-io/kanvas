package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pictureimagefilter.cpp`.
 * Exercises [ImageFilter.Picture] — the picture-backed image filter.
 *
 * Two pre-recorded pictures (one with AA fill, one with stroke) draw a
 * circle. The GM lays out, left-to-right:
 *  1. Picture filter at native bounds (unscaled).
 *  2. Picture filter restricted to a `(20, 20, 30, 30)` source rect.
 *  3. Picture filter restricted to an empty rect (draws nothing).
 *  4. Stroke picture replayed through a layer with a 4x zoom translate.
 *
 * Bottom row: same source rect drawn at 200% scale.
 *
 * **Adaptation:** Text "e" glyph replaced with a filled circle; LCD
 * subpixel edging replaced with a stroked circle. Sampling variants
 * (resampled/pixelated) use the same Picture filter since Kanvas
 * lacks `DeferredFromPicture`.
 *
 * @see https://github.com/google/skia/blob/main/gm/pictureimagefilter.cpp
 */
class PictureImageFilterGm : SkiaGm {

    override val name = "pictureimagefilter"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 300

    private var initialized = false
    private lateinit var fPicture: Picture
    private lateinit var fStrokePicture: Picture

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        if (!initialized) {
            fPicture = makePicture()
            fStrokePicture = makeStrokePicture()
            initialized = true
        }

        val pic = fPicture
        val stroke = fStrokePicture

        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val srcRect = Rect.fromXYWH(20f, 20f, 30f, 30f)
        val emptyRect = Rect.fromXYWH(20f, 20f, 0f, 0f)
        val bounds = Rect.fromLTRB(0f, 0f, 100f, 100f)

        val pictureSource: ImageFilter = ImageFilter.Picture(pic)
        val pictureSourceSrcRect: ImageFilter = ImageFilter.Picture(pic, srcRect)
        val pictureSourceEmptyRect: ImageFilter = ImageFilter.Picture(pic, emptyRect)

        canvas.save()

        // 1. Picture unscaled.
        fillRectFiltered(canvas, bounds, pictureSource)
        canvas.translate(100f, 0f)

        // 2. Unscaled subset (srcRect) of the source picture.
        fillRectFiltered(canvas, bounds, pictureSourceSrcRect)
        canvas.translate(100f, 0f)

        // 3. Empty rect — draws nothing.
        fillRectFiltered(canvas, bounds, pictureSourceEmptyRect)
        canvas.translate(100f, 0f)

        // 4. Stroke picture in a saveLayer, with a 4x zoom translate.
        var strokePaint = Paint(style = PaintStyle.STROKE)
        canvas.drawRect(bounds, strokePaint)

        val layerPaint = Paint(imageFilter = ImageFilter.Picture(stroke, pic.cullRect))
        canvas.scale(4f, 4f)
        canvas.translate(-0.9f * srcRect.left, -2.45f * srcRect.top)
        canvas.saveLayer(bounds, layerPaint)
        canvas.restore()

        canvas.restore()

        // Bottom row: srcRect-scoped pictures drawn at 200% scale.
        canvas.translate(0f, 100f)
        canvas.scale(200f / srcRect.width, 200f / srcRect.height)
        canvas.translate(-srcRect.left, -srcRect.top)
        fillRectFiltered(canvas, srcRect, pictureSource)

        // Scaled (same filter — sampling variants not applicable).
        canvas.translate(srcRect.width, 0f)
        fillRectFiltered(canvas, srcRect, pictureSource)

        // Pixelated (same filter).
        canvas.translate(srcRect.width, 0f)
        fillRectFiltered(canvas, srcRect, pictureSource)
    }

    private fun makePicture(): Picture {
        val rec = PictureRecorder()
        val canvas = rec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        val paint = Paint(color = Color.WHITE)
        val circle = Path { }.apply { addCircle(50f, 50f, 35f) }
        canvas.drawPath(circle, paint)
        return rec.finishRecordingAsPicture()
    }

    private fun makeStrokePicture(): Picture {
        val rec = PictureRecorder()
        val canvas = rec.beginRecording(Rect.fromLTRB(0f, 0f, 100f, 100f))
        val paint = Paint(color = Color.WHITE, style = PaintStyle.STROKE, strokeWidth = 3f)
        val circle = Path { }.apply { addCircle(50f, 50f, 15f) }
        canvas.drawPath(circle, paint)
        return rec.finishRecordingAsPicture()
    }

    private fun fillRectFiltered(canvas: GmCanvas, clipRect: Rect, filter: ImageFilter?) {
        canvas.save()
        canvas.clipRect(clipRect)
        val paint = Paint(imageFilter = filter)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, clipRect.width, clipRect.height), paint)
        canvas.restore()
    }
}
