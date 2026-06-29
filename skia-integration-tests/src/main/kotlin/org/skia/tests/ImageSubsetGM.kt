package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.graphiks.kanvas.codec.ImageCodecs
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkImage
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import java.nio.ByteBuffer

/**
 * Port of Skia's `gm/image.cpp::image_subset`
 * (`DEF_SIMPLE_GM_CAN_FAIL(image_subset, canvas, errorMsg, 440, 220)`).
 *
 * Creates a 200×200 lazy (picture-backed) image, takes a 100×100 centre
 * subset, and draws three columns:
 *  - `(10, 10)`       — the full 200×200 lazy image
 *  - `(220, 10)`      — the 100×100 subset
 *  - `(220+110, 10)`  — the subset after an encode→decode round-trip
 *                       (upstream's `serial_deserial` via
 *                       `SkBinaryWriteBuffer::writeImage`; kanvas-skia
 *                       substitutes a PNG encode → ImageCodecs decode).
 *
 * Upstream `serial_deserial` serialises an isolated `SkImage` via the
 * `SkBinaryWriteBuffer::writeImage` / `SkReadBuffer::readImage` path,
 * which is a private Skia format distinct from [org.skia.core.SkPicture.serialize].
 * kanvas-skia does not implement a standalone image binary-buffer
 * serialisation API; we approximate the round-trip with
 * `SkImage.encodeToData()` (PNG) + [ImageCodecs.DeferredFromEncodedData],
 * which exercises the same "encode, transmit, decode" intent while
 * keeping the test fully raster-portable.
 */
public class ImageSubsetGM : GM() {
    override fun getName(): String = "image_subset"
    override fun getISize(): SkISize = SkISize.Make(440, 220)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── make_lazy_image: 200×200 picture-backed image ───────────────
        val lazyImage = makeLazyImage() ?: return

        // ── Draw full lazy image at (10, 10). ────────────────────────────
        c.drawImage(lazyImage, 10f, 10f)

        // ── makeSubset: centre 100×100 of the 200×200 image. ────────────
        val subset = lazyImage.makeSubset(SkIRect.MakeLTRB(100, 100, 200, 200))
            ?: return

        // ── Draw subset at (220, 10). ────────────────────────────────────
        c.drawImage(subset, 220f, 10f)

        // ── serial_deserial approximation: PNG encode → decode. ──────────
        val roundTripped = serialDeserial(subset)
        if (roundTripped != null) {
            c.drawImage(roundTripped, 330f, 10f)
        }
    }

    private fun makeLazyImage(): SkImage? {
        val recorder = SkPictureRecorder()
        val recordCanvas = recorder.beginRecording(SkRect.MakeWH(200f, 200f))
        // C++: canvas->drawCircle(100, 100, 100, SkPaint())
        recordCanvas.drawCircle(100f, 100f, 100f, SkPaint())
        val picture = recorder.finishRecordingAsPicture()

        return SkImages.DeferredFromPicture(
            picture = picture,
            dimensions = SkISize.Make(200, 200),
            matrix = null,
            paint = null,
            bitDepth = SkImages.BitDepth.kU8,
            colorSpace = SkColorSpace.makeSRGB(),
        )
    }

    /**
     * Approximates C++'s `serial_deserial(SkImage*)` via PNG encode + decode.
     *
     * The upstream implementation serialises the image via
     * `SkBinaryWriteBuffer::writeImage` (Skia's internal binary format) and
     * reads it back via `SkReadBuffer::readImage`. kanvas-skia does not
     * expose that private format; we use PNG encode/decode instead, which
     * exercises the same "image → bytes → image" contract that the GM is
     * designed to test (lossless round-trip, subset survives re-materialisation).
     */
    private fun serialDeserial(img: SkImage?): SkImage? {
        if (img == null) return null
        val data = img.encodeToData() ?: return null
        val bytes = data.toByteArray()
        val buf = ByteBuffer.wrap(bytes)
        return ImageCodecs.DeferredFromEncodedData(buf)
    }
}
