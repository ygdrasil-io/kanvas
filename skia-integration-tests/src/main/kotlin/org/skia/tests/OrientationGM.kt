package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.kanvas.codec.CodecImageGenerator
import org.graphiks.kanvas.codec.ImageGeneratorImages
import org.skia.foundation.SkImages
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/orientation.cpp`](https://github.com/google/skia/blob/main/gm/orientation.cpp)
 * — the `orientation_<subsample>` family + `respect_orientation_jpeg`
 * GM.
 *
 * Each GM tiles eight 100×80 JPEGs (orientations 1–8 per the EXIF
 * `Orientation` tag) into a 4×2 grid (`4*kImgW × 2*kImgH = 400 × 160`).
 * The eight images embed identical RGB-quadrant + corner-label content
 * with their pixels pre-transformed by the inverse of the EXIF matrix,
 * so a correctly-oriented decode yields visually identical output for
 * every tile (only the digit at the centre changes).
 *
 * **Subsampling variant.** Upstream defines six GMs (`orientation_410`
 * → `orientation_444`) one per JPEG chroma-subsampling profile.
 * kanvas-skia ships the `_444` resources only ; the other five are
 * pure resampling variants of the same content and add no new API
 * coverage beyond what `_444` already exercises.
 *
 * **R-final.5 wiring.** [RespectOrientationJpegGM] is the entry point
 * that explicitly funnels through [ImageGeneratorImages.DeferredFromGenerator] +
 * [CodecImageGenerator] — the API surface this sprint adds. The
 * sibling [Orientation444GM] uses the standard
 * [ToolUtils.GetResourceAsImage] path and ships alongside as a
 * smoke-test of the same eight JPEGs through the canonical decode.
 *
 * **EXIF (R-final.8 fix).** As of R-final.8, kanvas-skia's
 * The JPEG codec now parses the EXIF Orientation
 * tag (0x0112) out of the APP1 segment and applies the corresponding
 * rotation/flip to the decoded pixels via
 * [org.skia.utils.PixmapUtils.Orient] — surfaced through
 * [org.graphiks.kanvas.codec.Codec.getOrigin]. Both GMs are therefore expected
 * to render at high pixel-fidelity vs. the upstream PNG references
 * (each of the eight tiles displays the same RGB-quadrant + corner-
 * label layout, only the centre digit changes).
 */
public class Orientation444GM : GM() {

    override fun getName(): String = "orientation_444"
    override fun getISize(): SkISize = SkISize.Make(4 * IMG_W, 2 * IMG_H)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.save()
        for (i in 1..8) {
            val image = ToolUtils.GetResourceAsImage("images/orientation/${i}_444.jpg")
            if (image != null) {
                c.drawImage(image, 0f, 0f)
            }
            if (i == 4) {
                c.restore()
                c.translate(0f, IMG_H.toFloat())
            } else if (image != null) {
                c.translate(image.width.toFloat(), 0f)
            } else {
                c.translate(IMG_W.toFloat(), 0f)
            }
        }
    }

    private companion object {
        const val IMG_W: Int = 100
        const val IMG_H: Int = 80
    }
}

/**
 * Port of Skia's `respect_orientation_jpeg` GM (same `gm/orientation.cpp`
 * file). Loads each JPEG via [ImageGeneratorImages.DeferredFromGenerator]
 * + [CodecImageGenerator.MakeFromEncodedCodec] — the explicit
 * generator path the upstream `make_images` test exercise targets.
 *
 * The grid layout matches [Orientation444GM] (4×2, 400 × 160), with the
 * sole behavioural difference being the [ImageGeneratorImages.DeferredFromGenerator]
 * detour (which on upstream Skia threads the EXIF tag through
 * `CodecImageGenerator::onGetPixels`).
 */
public class RespectOrientationJpegGM : GM() {

    override fun getName(): String = "respect_orientation_jpeg"
    override fun getISize(): SkISize = SkISize.Make(4 * IMG_W, 2 * IMG_H)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.save()
        for (i in 1..8) {
            val data = ToolUtils.GetResourceAsData("images/orientation/${i}_444.jpg")
            val image = if (data != null) {
                val gen = CodecImageGenerator.MakeFromEncodedCodec(data.toByteArray())
                if (gen != null) ImageGeneratorImages.DeferredFromGenerator(gen) else null
            } else {
                null
            }
            if (image != null) {
                c.drawImage(image, 0f, 0f)
            }
            // Upstream wraps the row at i == 4 *and* i == 8 (the line
            // below the loop is `if ('4' == i || '8' == i)`).
            if (i == 4) {
                c.restore()
                c.translate(0f, IMG_H.toFloat())
            } else if (image != null) {
                c.translate(image.width.toFloat(), 0f)
            } else {
                c.translate(IMG_W.toFloat(), 0f)
            }
        }
    }

    private companion object {
        const val IMG_W: Int = 100
        const val IMG_H: Int = 80
    }
}
