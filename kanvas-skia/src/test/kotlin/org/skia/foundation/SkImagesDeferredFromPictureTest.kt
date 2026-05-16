package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * S7-A verification suite for [SkImages.DeferredFromPicture] — the
 * deferred-rasterisation factory that materialises an [org.skia.core.SkPicture]
 * into an immutable [SkImage] sized to a caller-supplied dimension.
 * Pins the contract that pixels rendered through the picture replay
 * match pixels drawn directly into a sibling bitmap.
 */
class SkImagesDeferredFromPictureTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    @Test
    fun `deferred picture rasterizes to the expected dimensions`() {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(SkRect.MakeLTRB(0f, 0f, 16f, 16f))
        canvas.clear(red)
        val paint = SkPaint().apply { color = blue }
        canvas.drawRect(SkRect.MakeLTRB(4f, 4f, 12f, 12f), paint)
        val picture = recorder.finishRecordingAsPicture()

        val image = SkImages.DeferredFromPicture(picture, SkISize.Make(16, 16))
        assertNotNull(image)
        image as SkImage
        assertEquals(16, image.width)
        assertEquals(16, image.height)
    }

    @Test
    fun `deferred picture pixels match a direct render`() {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(SkRect.MakeLTRB(0f, 0f, 8f, 8f))
        canvas.clear(red)
        val paint = SkPaint().apply { color = blue }
        canvas.drawRect(SkRect.MakeLTRB(2f, 2f, 6f, 6f), paint)
        val picture = recorder.finishRecordingAsPicture()

        // Direct render — paint the same ops into a fresh bitmap.
        val direct = SkBitmap(8, 8)
        val directCanvas = SkCanvas(direct)
        directCanvas.clear(red)
        directCanvas.drawRect(SkRect.MakeLTRB(2f, 2f, 6f, 6f), paint)

        // Deferred render via the factory.
        val image = SkImages.DeferredFromPicture(picture, SkISize.Make(8, 8))
        assertNotNull(image)
        image as SkImage

        for (y in 0 until 8) {
            for (x in 0 until 8) {
                assertEquals(
                    direct.getPixel(x, y),
                    image.peekPixel(x, y),
                    "pixel ($x, $y)",
                )
            }
        }
    }

    @Test
    fun `deferred picture rejects empty dimensions`() {
        val recorder = SkPictureRecorder()
        val canvas = recorder.beginRecording(SkRect.MakeLTRB(0f, 0f, 4f, 4f))
        canvas.clear(red)
        val picture = recorder.finishRecordingAsPicture()

        assertNull(SkImages.DeferredFromPicture(picture, SkISize.Make(0, 4)))
        assertNull(SkImages.DeferredFromPicture(picture, SkISize.Make(4, 0)))
        assertNull(SkImages.DeferredFromPicture(picture, SkISize.Make(-1, 4)))
    }
}
