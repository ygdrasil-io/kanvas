package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.kanvas.codec.ImageCodecs
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.tools.ToolUtils
import java.nio.ByteBuffer

/**
 * Port of Skia's
 * [`gm/readpixels.cpp::ReadPixelsCodecGM`](https://github.com/google/skia/blob/main/gm/readpixels.cpp)
 * — 27 × 108 (`3 * (8+1)` × `12 * (8+1)`), variant of [ReadPixelsGM]
 * that drives a PNG-decoded source image (`images/randPixels.png`,
 * `8×8`) instead of a raster bitmap.
 *
 * Adds a [org.skia.foundation.SkImage] caching-hint dimension on top
 * of the colour-space / colour-type / alpha-type matrix — the raster
 * facade has no codec-image pixel cache to bypass, so both
 * `kAllow_CachingHint` and `kDisallow_CachingHint` columns produce
 * identical pixels.
 *
 * Upstream early-skips when `canvas->imageInfo().colorSpace()` is
 * null. The kanvas-skia raster surface always carries a tagged
 * colour space (see [org.skia.testing.TestUtils.DM_REFERENCE_COLOR_SPACE]),
 * so the early-skip never fires here.
 *
 * ## Port status — LAZY_PORT
 *
 * Body fully ported against the live
 * [org.skia.foundation.SkImage.readPixels] surface. Source fixture
 * `randPixels.png` is shipped with this module's test resources, so this
 * test can activate as soon as the cross-backend ratchet accepts the
 * rendered pixels.
 */
public class ReadPixelsCodecGM : GM() {

    override fun getName(): String = "readpixelscodec"

    override fun getISize(): SkISize = SkISize.Make(
        3 * (kEncodedWidth + 1),
        12 * (kEncodedHeight + 1),
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

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
        // Upstream loops over { kAllow_CachingHint, kDisallow_CachingHint }.
        // Raster has no codec-image pixel cache to bypass — both columns
        // yield identical pixels — but we still drive the loop twice so
        // the canvas translate matches the upstream `27 × 108` layout.
        val hints = 2

        val image = makeCodecImage() ?: return

        for (dstColorSpace in colorSpaces) {
            c.save()
            for (dstColorType in colorTypes) {
                for (dstAlphaType in alphaTypes) {
                    for (h in 0 until hints) {
                        ReadPixelsHelpers.drawImage(
                            c, image, dstColorType, dstAlphaType, dstColorSpace,
                        )
                        c.translate(0f, (kEncodedHeight + 1).toFloat())
                    }
                }
            }
            c.restore()
            c.translate((kEncodedWidth + 1).toFloat(), 0f)
        }
    }

    /**
     * Mirrors upstream's `make_codec_image()` — load the encoded
     * `images/randPixels.png` bytes via [ToolUtils.GetResourceAsData],
     * then materialise as a deferred-decoded raster [SkImage] via
     * [ImageCodecs.DeferredFromEncodedData]. Returns `null` when the
     * fixture isn't on the classpath ; the GM body short-circuits and
     * leaves the canvas blank.
     */
    private fun makeCodecImage(): SkImage? {
        val data = ToolUtils.GetResourceAsData("images/randPixels.png") ?: return null
        val buffer: ByteBuffer = ByteBuffer.wrap(data.toByteArray())
        return ImageCodecs.DeferredFromEncodedData(buffer)
    }

    private companion object {
        const val kEncodedWidth = 8
        const val kEncodedHeight = 8
    }
}
