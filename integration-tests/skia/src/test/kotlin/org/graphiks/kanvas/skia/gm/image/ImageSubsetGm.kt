/**
 * Port of Skia's `gm/imagesubset.cpp`.
 * Tests deferred image materialization + subset extraction + serialization
 * round-trip. [SkImages.DeferredFromPicture] replaced with eager [Surface],
 * [SkImage.makeSubset] replaced with pixel-array slicing, image
 * serialization replaced with [Picture.toByteArray] round-trip.
 * @see https://github.com/google/skia/blob/main/gm/imagesubset.cpp
 */
package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ImageSubsetGm : SkiaGm {
    override val name = "image_subset"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 440
    override val height = 220

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val lazyImage = makeLazyImage()

        canvas.drawImage(lazyImage, Rect.fromXYWH(10f, 10f, 200f, 200f))

        val subset = makeSubsetImage(lazyImage, 100, 100, 100, 100)
        canvas.drawImage(subset, Rect.fromXYWH(220f, 10f, 100f, 100f))

        val roundTripped = pictureSerialDeserial()
        roundTripped?.let {
            canvas.drawImage(it, Rect.fromXYWH(330f, 10f, 100f, 100f))
        }
    }

    private fun makeLazyImage(): Image {
        val recorder = PictureRecorder()
        val recordCanvas = recorder.beginRecording(Rect.fromXYWH(0f, 0f, 200f, 200f))
        recordCanvas.drawPath(Path { }.apply { addCircle(100f, 100f, 100f) }, Paint())
        val picture = recorder.finishRecordingAsPicture()

        val surface = Surface(200, 200)
        surface.canvas {
            clear(Color.TRANSPARENT)
            picture.playback(this)
        }
        return surface.makeImageSnapshot()
    }

    private fun makeSubsetImage(source: Image, x: Int, y: Int, w: Int, h: Int): Image {
        val pixels = source.pixels ?: return source
        val result = ByteArray(w * h * 4)
        for (row in 0 until h) {
            val srcOff = ((y + row) * source.width + x) * 4
            val dstOff = row * w * 4
            pixels.copyInto(result, dstOff, srcOff, srcOff + w * 4)
        }
        return Image(w, h, source.colorType, "subset", result, source.colorSpace)
    }

    private fun pictureSerialDeserial(): Image? {
        val recorder = PictureRecorder()
        val recordCanvas = recorder.beginRecording(Rect.fromXYWH(0f, 0f, 100f, 100f))
        recordCanvas.drawPath(Path { }.apply { addCircle(50f, 50f, 50f) }, Paint())
        val picture = recorder.finishRecordingAsPicture()

        val bytes = picture.toByteArray()
        val restored = Picture.fromByteArray(bytes) ?: return null

        val surface = Surface(100, 100)
        surface.canvas {
            clear(Color.TRANSPARENT)
            restored.playback(this)
        }
        return surface.makeImageSnapshot()
    }
}
