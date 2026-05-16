package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * R-suivi.50 — verify the default [SkCanvas.drawPicture] virtual replays
 * the picture via [SkPicture.playback], honours [matrix] (when supplied),
 * and brackets the replay with `save / restoreToCount` so the caller's
 * state is preserved.
 */
class SkCanvasDrawPictureTest {

    @Test
    fun `drawPicture replays recorded ops into the canvas`() {
        // Build a picture that paints a red rect at (10, 10, 30, 30).
        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeXYWH(0f, 0f, 40f, 40f))
        recCanvas.drawRect(SkRect.MakeXYWH(10f, 10f, 20f, 20f), SkPaint(SK_ColorRED))
        val picture = rec.finishRecordingAsPicture()

        val bm = SkBitmap(40, 40).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.drawPicture(picture)

        // Pixels inside the recorded rect should be red ; everywhere else white.
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                val expected = if (x in 10 until 30 && y in 10 until 30) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `drawPicture with matrix offsets the playback`() {
        // Picture paints a red rect at (0, 0, 10, 10) in its local space.
        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeXYWH(0f, 0f, 10f, 10f))
        recCanvas.drawRect(SkRect.MakeWH(10f, 10f), SkPaint(SK_ColorRED))
        val picture = rec.finishRecordingAsPicture()

        val bm = SkBitmap(40, 40).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.drawPicture(picture, SkMatrix.MakeTrans(20f, 20f))

        // The rect should land at (20, 20, 30, 30).
        for (y in 0 until 40) {
            for (x in 0 until 40) {
                val expected = if (x in 20 until 30 && y in 20 until 30) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `drawPicture preserves caller saveCount`() {
        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeWH(10f, 10f))
        recCanvas.save()
        recCanvas.translate(5f, 5f)
        recCanvas.drawRect(SkRect.MakeWH(2f, 2f), SkPaint(SK_ColorRED))
        // Intentionally leave the picture's save dangling — the playback
        // must still restore the outer canvas to its original depth.
        val picture = rec.finishRecordingAsPicture()

        val bm = SkBitmap(20, 20).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        val beforeCount = canvas.getSaveCount()
        canvas.drawPicture(picture)
        assertEquals(beforeCount, canvas.getSaveCount(), "Picture playback must not leak save() depth")
    }

    @Test
    fun `drawPicture is open and overridable to count calls`() {
        var calls = 0
        class CountingCanvas(bm: SkBitmap) : SkCanvas(bm) {
            override fun drawPicture(
                picture: SkPicture,
                matrix: SkMatrix?,
                paint: SkPaint?,
            ) {
                calls++
                // Don't replay — confirm the override is reached.
            }
        }

        val rec = SkPictureRecorder()
        val recCanvas = rec.beginRecording(SkRect.MakeWH(8f, 8f))
        recCanvas.drawRect(SkRect.MakeWH(8f, 8f), SkPaint(SK_ColorRED))
        val picture = rec.finishRecordingAsPicture()

        val bm = SkBitmap(8, 8).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = CountingCanvas(bm)
        canvas.drawPicture(picture)
        canvas.drawPicture(picture, SkMatrix.MakeTrans(1f, 1f))

        assertEquals(2, calls)
        // Picture playback was skipped — every pixel should be untouched.
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(SK_ColorWHITE, bm.getPixel(x, y))
            }
        }
        assertTrue(true)
    }
}
