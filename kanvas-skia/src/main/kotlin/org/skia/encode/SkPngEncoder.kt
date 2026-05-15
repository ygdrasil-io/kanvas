package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPixmap
import org.skia.foundation.stream.SkWStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.imageio.ImageIO

/**
 * PNG encoder — D3.5 implementation of upstream's
 * [`SkPngEncoder`](https://github.com/google/skia/blob/main/include/encode/SkPngEncoder.h).
 *
 * Mirrors the upstream namespace as a Kotlin `object` carrying the
 * static `Encode` entry points, plus a Kotlin-idiomatic [Options]
 * data class that maps onto the upstream `SkPngEncoder::Options`
 * struct. Like the rest of the D3 family, the actual bitstream
 * encode is delegated to `javax.imageio.ImageIO` ; this slice only
 * owns the Skia-shaped surface.
 *
 * **Honoured options** : none. Every [Options] field is plumbed for
 * source compatibility with upstream call sites, but the underlying
 * ImageIO PNG writer does not expose filter selection, zlib level,
 * or `tEXt` chunk control — passing non-default values does not
 * change the output. A future slice may either swap in a
 * configurable PNG writer (the JAI-ImageIO ext provides one) or
 * port the libpng filter heuristics directly. For D3.5 the goal is
 * just "produce a valid PNG round-trippable by [SkCodec]" ; no GM
 * test currently consumes encoded PNGs.
 *
 * **Colour space** : the caller's [SkBitmap.colorSpace] is **not**
 * embedded as an `iCCP` chunk in D3.5, so a non-sRGB bitmap loses
 * its working-space tag through an encode→decode round-trip. The
 * pixel values themselves are preserved (8-bit ARGB through
 * `BufferedImage.setRGB`). Embedding the profile is tracked as a
 * follow-up when a workflow needs it.
 */
public object SkPngEncoder {

    /**
     * Mirrors `SkPngEncoder::FilterFlag`. Each flag is a libpng
     * filter selector ; combining them tells libpng to pick the
     * smallest-encoded filter per row. [kAll] matches libpng's
     * default and also matches our actual behaviour (since the
     * underlying ImageIO writer doesn't honour the selection
     * either way).
     */
    public enum class FilterFlag(public val mask: Int) {
        kZero(0x00),
        kNone(0x08),
        kSub(0x10),
        kUp(0x20),
        kAvg(0x40),
        kPaeth(0x80),
        kAll(0x08 or 0x10 or 0x20 or 0x40 or 0x80),
    }

    /**
     * Mirrors `SkPngEncoder::Options`. **All fields are currently
     * advisory** — the ImageIO PNG writer does not honour them. They
     * are kept on the surface to match upstream call sites (so a
     * future Skia → kanvas-skia port doesn't have to remove the
     * argument) and to document what a future, configurable PNG
     * writer would look like.
     */
    public data class Options(
        /** Bitfield of [FilterFlag]s. */
        val filterFlags: Int = FilterFlag.kAll.mask,
        /** Must be in `[0, 9]` ; 9 = max compression. Advisory. */
        val zLibLevel: Int = 6,
        /**
         * `tEXt` keyword/value pairs. Even-indexed entries are
         * keywords ; odd-indexed entries are the corresponding text.
         * Currently advisory — ImageIO does not surface tEXt control
         * via the standard write API.
         */
        val comments: List<String> = emptyList(),
    ) {
        init {
            require(zLibLevel in 0..9) { "zLibLevel must be in [0, 9], got $zLibLevel" }
            require(comments.size % 2 == 0) {
                "comments must alternate keyword/text — got odd count ${comments.size}"
            }
        }
    }

    /** Default-options [Options] singleton — saves one allocation per call. */
    private val defaultOptions = Options()

    /**
     * Encode [src]'s pixels and return the PNG bytes, or `null` on
     * encoder failure. Mirrors `sk_sp<SkData>
     * SkPngEncoder::Encode(const SkPixmap&, const Options&)`.
     */
    public fun Encode(src: SkBitmap, options: Options = defaultOptions): ByteArray? {
        val baos = ByteArrayOutputStream()
        return if (Encode(baos, src, options)) baos.toByteArray() else null
    }

    /**
     * Encode [src]'s pixels into [dst]. Returns `true` on success.
     * Mirrors `bool SkPngEncoder::Encode(SkWStream*, const SkPixmap&,
     * const Options&)`. The caller retains ownership of [dst].
     */
    public fun Encode(dst: OutputStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        @Suppress("UNUSED_VARIABLE") val _ignored = options // see kdoc on Options
        val img = EncoderSupport.bitmapToBufferedImage(src)
        return try {
            ImageIO.write(img, "png", dst)
        } catch (_: Throwable) {
            false
        }
    }

    // ── R-final.6 overloads : SkPixmap + SkWStream ───────────────────────
    //
    // The plan calls for `SkPngEncoder.Encode(stream, pixmap, options)` to
    // mirror the upstream `SkWStream*` + `SkPixmap` signature. The Kotlin
    // implementation routes through the existing SkBitmap path : the pixmap
    // is materialised into a fresh 8888 bitmap via `getColor` (which honours
    // the source colour type), then re-uses the proven ImageIO write
    // pipeline. Slow for huge images but correct for the GM-sized inputs
    // that exercise this overload.

    /**
     * Encode [src]'s pixels into [stream]. Returns `true` on success.
     * Mirrors upstream's `bool SkPngEncoder::Encode(SkWStream*, const
     * SkPixmap&, const Options&)`. The caller retains ownership of
     * [stream]. See class kdoc for the list of honoured / advisory
     * options (none of [Options]' fields are wired to ImageIO today).
     */
    public fun Encode(stream: SkWStream, src: SkPixmap, options: Options = defaultOptions): Boolean {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return false
        return Encode(stream, bitmap, options)
    }

    /**
     * Encode [src]'s pixels into [bytes]. Returns `null` if encoding
     * fails. Convenience wrapper for the upstream `sk_sp<SkData>
     * SkPngEncoder::Encode(const SkPixmap&, const Options&)` shape.
     */
    public fun Encode(src: SkPixmap, options: Options = defaultOptions): ByteArray? {
        val bitmap = EncoderSupport.pixmapToBitmap(src) ?: return null
        return Encode(bitmap, options)
    }

    /**
     * Convenience overload — writes the encoded bytes into [stream] via
     * the [SkWStream.write] contract instead of an [OutputStream].
     * Useful for call sites already plumbing the foundation type.
     */
    public fun Encode(stream: SkWStream, src: SkBitmap, options: Options = defaultOptions): Boolean {
        val bytes = Encode(src, options) ?: return false
        return stream.write(bytes, bytes.size)
    }
}
