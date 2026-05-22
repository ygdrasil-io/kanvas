package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint

/**
 * Port of Skia's
 * [`gm/readpixels.cpp::ReadPixelsPictureGM`](https://github.com/google/skia/blob/main/gm/readpixels.cpp)
 * — 192 × 768, variant of [ReadPixelsGM] that drives an [SkImage]
 * snapshot of an [org.skia.core.SkPicture] as the source for the
 * `(dstColorType, dstAlphaType, dstColorSpace, cachingHint)` matrix.
 *
 * Upstream early-skips when `canvas->imageInfo().colorSpace()` is null.
 * The kanvas-skia raster surface always carries a tagged colour space,
 * so the early-skip never fires here.
 *
 * ## Port status — LAZY_PORT
 *
 * Body fully ported against the live
 * [org.skia.foundation.SkImage.readPixels] /
 * [SkImages.DeferredFromPicture] surface. No source fixture required —
 * the picture is recorded in memory.
 */
public class ReadPixelsPictureGM : GM() {

    override fun getName(): String = "readpixelspicture"

    override fun getISize(): SkISize = SkISize.Make(
        3 * ReadPixelsHelpers.kWidth,
        12 * ReadPixelsHelpers.kHeight,
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val image = makePictureImage() ?: return
        val images = arrayOf(image)
        val alphaTypes = arrayOf(SkAlphaType.kUnpremul, SkAlphaType.kPremul)
        val colorTypes = arrayOf(
            SkColorType.kRGBA_8888,
            SkColorType.kBGRA_8888,
            SkColorType.kRGBA_F16,
        )
        val colorSpaces = arrayOf(
            ReadPixelsHelpers.makeWideGamut(),
            SkColorSpace.makeSRGB(),
            ReadPixelsHelpers.makeSmallGamut(),
        )
        val hints = 2

        for (src in images) {
            for (dstColorSpace in colorSpaces) {
                c.save()
                for (dstColorType in colorTypes) {
                    for (dstAlphaType in alphaTypes) {
                        for (h in 0 until hints) {
                            ReadPixelsHelpers.drawImage(
                                c, src, dstColorType, dstAlphaType, dstColorSpace,
                            )
                            c.translate(0f, ReadPixelsHelpers.kHeight.toFloat())
                        }
                    }
                }
                c.restore()
                c.translate(ReadPixelsHelpers.kWidth.toFloat(), 0f)
            }
        }
    }

    /**
     * Mirrors upstream's `draw_contents(canvas)` — three overlapping
     * stroked circles in primary-channel colours.
     */
    private fun drawContents(canvas: SkCanvas) {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
        }
        paint.color = 0xFF800000.toInt()
        canvas.drawCircle(40f, 40f, 35f, paint)
        paint.color = 0xFF008000.toInt()
        canvas.drawCircle(50f, 50f, 35f, paint)
        paint.color = 0xFF000080.toInt()
        canvas.drawCircle(60f, 60f, 35f, paint)
    }

    /**
     * Mirrors upstream's `make_picture_image()` — record [drawContents]
     * into an [org.skia.core.SkPicture], snapshot it into an [SkImage]
     * sized `kWidth × kHeight` with sRGB tagging.
     */
    private fun makePictureImage(): SkImage? {
        val recorder = SkPictureRecorder()
        val recordingCanvas = recorder.beginRecording(
            SkRect.MakeIWH(ReadPixelsHelpers.kWidth, ReadPixelsHelpers.kHeight),
        )
        drawContents(recordingCanvas)
        val picture = recorder.finishRecordingAsPicture()
        return SkImages.DeferredFromPicture(
            picture,
            org.graphiks.math.SkISize.Make(ReadPixelsHelpers.kWidth, ReadPixelsHelpers.kHeight),
            matrix = null,
            paint = null,
            bitDepth = SkImages.BitDepth.kU8,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }
}
