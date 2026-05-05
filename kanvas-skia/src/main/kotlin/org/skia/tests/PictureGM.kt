package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/picture.cpp::PictureGM` (`pictures`, 450 × 120).
 *
 * Records a small picture (translucent blue square + red triangle +
 * green triangle + a `kPlus`-blended white square) and replays it
 * 4 times :
 *  1. Identity placement.
 *  2. `translate(110, 0)`.
 *  3. `translate(220, 0)` with default paint.
 *  4. `translate(330, 0)` with `paint.alpha = 0.5` (saveLayer).
 *
 * Upstream uses `canvas->drawPicture(picture, &matrix, &paint)` which
 * saveLayers if `paint != null`. We don't expose that overload, but
 * `picture.playback(canvas)` + an explicit `save/concat[/saveLayer]
 * /restore` sequence reproduces identical behaviour.
 */
public class PictureGM : GM() {

    override fun getName(): String = "pictures"
    override fun getISize(): SkISize = SkISize.Make(450, 120)

    private val picture: SkPicture by lazy { makePicture() }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        var matrix = SkMatrix.Identity

        // 1. Identity (no extra transform).
        picture.playback(c)

        // 2. translate(110, 0), no paint.
        matrix = matrix.postTranslate(110f, 0f)
        drawPictureWithMatrixAndPaint(c, picture, matrix, paint = null)

        // 3. translate(220, 0), default-ctor paint.
        matrix = matrix.postTranslate(110f, 0f)
        drawPictureWithMatrixAndPaint(c, picture, matrix, paint = SkPaint())

        // 4. translate(330, 0), paint.alpha = 0.5.
        matrix = matrix.postTranslate(110f, 0f)
        val alphaPaint = SkPaint().apply { alphaf = 0.5f }
        drawPictureWithMatrixAndPaint(c, picture, matrix, paint = alphaPaint)
    }

    /** Mirrors upstream's `canvas->drawPicture(picture, matrix, paint)`. */
    private fun drawPictureWithMatrixAndPaint(
        canvas: SkCanvas,
        picture: SkPicture,
        matrix: SkMatrix,
        paint: SkPaint?,
    ) {
        canvas.save()
        canvas.concat(matrix)
        if (paint != null) {
            canvas.saveLayer(picture.cullRect, paint)
            picture.playback(canvas)
            canvas.restore()
        } else {
            picture.playback(canvas)
        }
        canvas.restore()
    }

    private fun makePicture(): SkPicture {
        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(100f, 100f)

        val paint = SkPaint().apply { isAntiAlias = true }

        paint.color = 0x800000FF.toInt()
        recCanvas.drawRect(SkRect.MakeWH(100f, 100f), paint)

        paint.color = 0x80FF0000.toInt()
        recCanvas.drawPath(
            SkPath.Polygon(arrayOf(0f to 0f, 100f to 0f, 100f to 100f), isClosed = false),
            paint,
        )

        paint.color = 0x8000FF00.toInt()
        recCanvas.drawPath(
            SkPath.Polygon(arrayOf(0f to 0f, 100f to 0f, 0f to 100f), isClosed = false),
            paint,
        )

        paint.color = 0x80FFFFFF.toInt()
        paint.blendMode = SkBlendMode.kPlus
        recCanvas.drawRect(SkRect.MakeXYWH(25f, 25f, 50f, 50f), paint)

        return rec.finishRecordingAsPicture()
    }
}
