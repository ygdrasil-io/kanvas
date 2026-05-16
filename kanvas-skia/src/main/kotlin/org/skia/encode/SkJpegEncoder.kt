package org.skia.encode

import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkPixmap
import org.skia.foundation.stream.SkWStream
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

/**
 * JPEG encoder — D3.5 implementation of upstream's
 * [`SkJpegEncoder`](https://github.com/google/skia/blob/main/include/encode/SkJpegEncoder.h).
 *
 * Mirrors the upstream namespace as a Kotlin `object` carrying the
 * static `Encode` entry points and a Kotlin-idiomatic [Options]
 * data class that maps onto upstream's `SkJpegEncoder::Options`.
 * The actual bitstream encode is delegated to ImageIO's
 * `JPEGImageWriter`.
 *
 * **Honoured options** : [Options.quality] is wired through to
 * [ImageWriteParam.setCompressionQuality] (re-mapped from the
 * upstream `[0, 100]` integer scale to ImageIO's `[0, 1]` float).
 * Quality `100` produces a near-lossless encode ; quality `50`
 * produces a noticeably smaller file with a small visible drift —
 * both are exercised by the matching test.
 *
 * **Advisory** : [Options.downsample] (upstream's chroma subsample
 * selector — 4:2:0 / 4:2:2 / 4:4:4) and [Options.alphaOption] (how
 * to handle alpha-bearing input pixmaps — JPEG itself is opaque) are
 * plumbed for source-compatibility with upstream call sites but the
 * underlying `JPEGImageWriter` does not expose them through its
 * public API ; values are accepted but ignored. Documented here so
 * a future port that needs e.g. 4:4:4 chroma can swap in a
 * different `ImageWriter` without changing call sites.
 *
 * **Alpha handling** : JPEG cannot carry alpha. ImageIO's writer
 * rejects ARGB inputs ; we work around that by projecting the
 * source bitmap onto an opaque `TYPE_INT_RGB` `BufferedImage`
 * before writing. The projection respects [Options.alphaOption] :
 *  - [AlphaOption.kIgnore] drops alpha — RGB pixels are emitted as
 *    if the source were opaque. Matches upstream's "ignore" path.
 *  - [AlphaOption.kBlendOnBlack] composites the source onto a black
 *    background — `(R, G, B) → (R*α/255, G*α/255, B*α/255)`. Matches
 *    upstream's libjpeg-turbo "blend on black" path used by the
 *    `encode-alpha-jpeg` GM.
 */
public object SkJpegEncoder {

    /** Mirrors `SkJpegEncoder::AlphaOption`. */
    public enum class AlphaOption {
        /** Drop the alpha channel entirely. Default ; matches upstream. */
        kIgnore,

        /**
         * Composite the source pixmap onto a black background before
         * encoding. RGB channels are scaled by `alpha / 255` so a
         * fully-transparent pixel encodes to black and a fully-opaque
         * pixel preserves its colour exactly. Honoured by both the
         * SkBitmap and SkPixmap entry points.
         */
        kBlendOnBlack,
    }

    /** Mirrors `SkJpegEncoder::Downsample`. */
    public enum class Downsample {
        /** 4:2:0 — chroma reduced by 2 in both directions. Default. */
        k420,
        /** 4:2:2 — chroma reduced by 2 horizontally. */
        k422,
        /** 4:4:4 — no chroma reduction. */
        k444,
    }

    /**
     * Mirrors `SkJpegEncoder::Options`. Only [quality] is currently
     * honoured ; see class kdoc for what is advisory.
     */
    public data class Options(
        /** Encoder quality in `[0, 100]`. 100 = near-lossless. */
        val quality: Int = 100,
        /** Chroma subsampling. **Advisory** — see class kdoc. */
        val downsample: Downsample = Downsample.k420,
        /** Alpha handling. **Advisory** — alpha is always dropped. */
        val alphaOption: AlphaOption = AlphaOption.kIgnore,
    ) {
        init {
            require(quality in 0..100) { "quality must be in [0, 100], got $quality" }
        }
    }

    private val defaultOptions = Options()

    /**
     * Encode [src]'s pixels and return the JPEG bytes, or `null` on
     * encoder failure. Mirrors `sk_sp<SkData>
     * SkJpegEncoder::Encode(const SkPixmap&, const Options&)`.
     */
    public fun Encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (Encode(baos, src, options)) baos.toByteArray() else null
    }

    /**
     * Encode [src]'s pixels into [dst]. Returns `true` on success.
     * Mirrors `bool SkJpegEncoder::Encode(SkWStream*, const SkPixmap&,
     * const Options&)`. The caller retains ownership of [dst].
     */
    public fun Encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        // Project the bitmap onto an opaque TYPE_INT_RGB image —
        // ImageIO's JPEG writer rejects alpha-bearing buffered images.
        // The projection respects [Options.alphaOption] : kIgnore drops
        // alpha (Graphics2D draws over a black canvas, but the source
        // is rendered without compositing-by-alpha because the input is
        // already laid out as packed ARGB), kBlendOnBlack scales RGB by
        // alpha/255 before the channel-drop.
        val opaque = projectToOpaque(src, options.alphaOption)
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        if (!writers.hasNext()) return false
        val writer = writers.next()
        return try {
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = options.quality / 100f
            }
            MemoryCacheImageOutputStream(dst).use { ios ->
                writer.output = ios
                writer.write(null, IIOImage(opaque, null, null), params)
            }
            true
        } catch (_: Throwable) {
            false
        } finally {
            writer.dispose()
        }
    }

    /**
     * Project [src] into an opaque `TYPE_INT_RGB` [BufferedImage],
     * honouring [alphaOption]. Each pixel is read as non-premultiplied
     * 8-bit ARGB via [SkBitmap.getPixel] (which already handles every
     * supported colour type — 8888 / BGRA / 4444 / 565 / Alpha8 / F16),
     * then the RGB channels are emitted into the destination either
     * verbatim ([AlphaOption.kIgnore]) or scaled by `alpha / 255`
     * ([AlphaOption.kBlendOnBlack]).
     */
    private fun projectToOpaque(src: SkBitmap, alphaOption: AlphaOption): BufferedImage {
        val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val rgb = IntArray(src.width * src.height)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                val argb = src.getPixel(x, y)
                val a = SkColorGetA(argb)
                val r = SkColorGetR(argb)
                val g = SkColorGetG(argb)
                val b = SkColorGetB(argb)
                rgb[y * src.width + x] = when (alphaOption) {
                    AlphaOption.kIgnore -> SkColorSetARGB(0xFF, r, g, b)
                    AlphaOption.kBlendOnBlack -> {
                        val rr = (r * a + 127) / 255
                        val gg = (g * a + 127) / 255
                        val bb = (b * a + 127) / 255
                        SkColorSetARGB(0xFF, rr, gg, bb)
                    }
                }
            }
        }
        out.setRGB(0, 0, src.width, src.height, rgb, 0, src.width)
        return out
    }

    // ── R-final.6 overloads : SkPixmap + SkWStream ───────────────────────

    /**
     * Encode [src]'s pixels into [stream]. Returns `true` on success.
     * Mirrors upstream's `bool SkJpegEncoder::Encode(SkWStream*, const
     * SkPixmap&, const Options&)`. See class kdoc for the list of
     * honoured options ([Options.quality] and [Options.alphaOption])
     * and the advisory ones ([Options.downsample]).
     */
    public fun Encode(stream: SkWStream, src: SkPixmap, options: Options = defaultOptions): Boolean {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return false
        return Encode(stream, bitmap, options)
    }

    /**
     * Encode [src]'s pixels and return the JPEG bytes, or `null` on
     * encoder failure. Convenience wrapper for upstream's
     * `sk_sp<SkData> SkJpegEncoder::Encode(const SkPixmap&, const
     * Options&)`.
     */
    public fun Encode(src: SkPixmap, options: Options = defaultOptions): ByteArray? {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return null
        return Encode(bitmap, options)
    }

    /**
     * Convenience overload — writes the encoded bytes into [stream] via
     * the [SkWStream.write] contract instead of an [OutputStream].
     */
    public fun Encode(stream: SkWStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        val bytes = Encode(src, options) ?: return false
        return stream.write(bytes, bytes.size)
    }
}
