package org.skia.encode

import org.skia.foundation.SkBitmap
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
 * before writing, which is equivalent to upstream's
 * [Options.AlphaOption.kIgnore] (alpha channel is dropped).
 */
public object SkJpegEncoder {

    /** Mirrors `SkJpegEncoder::AlphaOption`. */
    public enum class AlphaOption {
        /** Drop the alpha channel entirely. Default ; matches upstream. */
        kIgnore,

        /**
         * Composite the source pixmap onto a black background before
         * encoding. Currently advisory — see class kdoc.
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
        // ImageIO's JPEG writer rejects alpha-bearing buffered
        // images, so this is equivalent to upstream's
        // AlphaOption.kIgnore (the alpha byte from the unpremul 8888
        // source is dropped on copy).
        val argb = EncoderSupport.bitmapToBufferedImage(src)
        val opaque = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        val g = opaque.createGraphics()
        try {
            g.drawImage(argb, 0, 0, null)
        } finally {
            g.dispose()
        }
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
}
