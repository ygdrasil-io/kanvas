package org.skia.codec

import org.skia.codec.jpeg.SkJpegCodec
import org.skia.codec.png.SkPngCodec
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.skcms.SkcmsICCProfile
import java.io.InputStream

/**
 * Mirrors Skia's
 * [`SkCodec`](https://github.com/google/skia/blob/main/include/codec/SkCodec.h)
 * — a stateless, type-erased decoder facade. A concrete [SkCodec] is
 * obtained from [MakeFromData] / [MakeFromStream] ; the factory sniffs
 * the encoded bytes, picks the matching format-specific subclass, and
 * the caller then drives the decode through [getPixels] (or its
 * convenience overloads).
 *
 * Slice **D3.1** ships the facade plus the PNG implementation — see
 * [SkPngCodec]. The plan ([MIGRATION_PLAN_RASTER_COMPLETION.md] § D3)
 * lays out the JPEG (D3.2), GIF/BMP/WBMP (D3.3), and WEBP (D3.4)
 * follow-ups ; each adds a sibling under `org.skia.codec.<format>` and
 * registers itself with [Decoders].
 *
 * **API surface** (mapped from upstream) :
 *  - [getInfo] — a "reasonable [SkImageInfo] to decode into" ; carries
 *    the encoded width/height, the natural [org.skia.foundation.SkColorType]
 *    (F16 for ≥16-bpc PNGs, 8888 otherwise), and the colour space derived
 *    from the embedded ICC profile (or sRGB if the file has none).
 *  - [getPixels] — fills a caller-allocated [SkBitmap], mirroring
 *    `Result SkCodec::getPixels(const SkImageInfo&, void*, size_t,
 *    const Options*)`. The Kotlin signature collapses `(void*, size_t)`
 *    into the bitmap, since [SkBitmap] already carries its row stride.
 *  - [getImage] — convenience that allocates a fresh bitmap matching
 *    [getInfo] and delegates to [getPixels]. Mirrors upstream's
 *    `getImage()` overload.
 *  - [getICCProfile] — the parsed ICC profile (`null` when the encoded
 *    file has no embedded profile). Used by the test harness to tag
 *    the reference bitmap with its true working colour space.
 *  - [getEncodedFormat] — the on-disk container format (PNG, JPEG, …).
 *
 * **Out of scope for D3.1** : [Options], scanline / incremental decode,
 * subsets, animated frames, EXIF orientation, YUV planes. None of the
 * GM tests need them ; they will land alongside the formats that
 * actually use them (e.g. animation with GIF in D3.3).
 */
public abstract class SkCodec internal constructor() {

    /**
     * Mirrors Skia's `SkCodec::Result`. Only [kSuccess] and the four
     * "input is bad" / "request is bad" variants are produced by the
     * D3.1 PNG path ; the rest are kept for parity so DM-style call
     * sites compile against the full enum.
     */
    public enum class Result {
        kSuccess,
        kIncompleteInput,
        kErrorInInput,
        kInvalidConversion,
        kInvalidScale,
        kInvalidParameters,
        kInvalidInput,
        kCouldNotRewind,
        kInternalError,
        kUnimplemented,
        kOutOfMemory,
    }

    /** Default decode target. See class kdoc. */
    public abstract fun getInfo(): SkImageInfo

    /** On-disk format of the encoded bytes this codec was created from. */
    public abstract fun getEncodedFormat(): SkEncodedImageFormat

    /**
     * The ICC profile parsed out of the encoded file, or `null` if it
     * carried none. Mirrors `SkCodec::getICCProfile()`. The returned
     * profile is the same object the codec used to populate
     * [getInfo]'s colour space, so equality is identity-cheap.
     */
    public abstract fun getICCProfile(): SkcmsICCProfile?

    public fun dimensions(): SkISize = getInfo().dimensions()
    public fun bounds(): SkIRect = getInfo().bounds()

    /**
     * Decode into [dst], whose [SkBitmap.width] / [SkBitmap.height] /
     * [SkBitmap.colorType] / [SkBitmap.colorSpace] **must** match the
     * `info` argument. Mirrors `SkCodec::getPixels(const SkImageInfo&,
     * void* dst, size_t rowBytes, const Options*)` — Kotlin folds the
     * `(dst, rowBytes)` pair into a single [SkBitmap] since our bitmap
     * already carries its stride.
     *
     * Returns [Result.kInvalidParameters] if the bitmap geometry
     * disagrees with `info`, [Result.kInvalidConversion] if the
     * concrete codec cannot satisfy the requested colour type, and
     * [Result.kSuccess] when the pixels were written.
     */
    public abstract fun getPixels(info: SkImageInfo, dst: SkBitmap): Result

    /** Default-info overload — equivalent to `getPixels(getInfo(), dst)`. */
    public fun getPixels(dst: SkBitmap): Result = getPixels(getInfo(), dst)

    /**
     * Allocate a fresh [SkBitmap] matching `info` and decode into it.
     * Mirrors upstream's `std::tuple<sk_sp<SkImage>, SkCodec::Result>
     * SkCodec::getImage(const SkImageInfo&)`. Returns `(null, result)`
     * if the decode failed for any reason other than [Result.kSuccess]
     * (the partial bitmap is dropped — no incremental decoding in
     * D3.1).
     */
    public fun getImage(info: SkImageInfo = getInfo()): Pair<SkBitmap?, Result> {
        val bitmap = SkBitmap(
            width = info.width,
            height = info.height,
            colorSpace = info.colorSpace,
            colorType = info.colorType,
        )
        val result = getPixels(info, bitmap)
        return if (result == Result.kSuccess) bitmap to result else null to result
    }

    public companion object {

        /**
         * Sniff the leading bytes of [data] and return a codec that can
         * decode it, or `null` if no registered format matches.
         *
         * Mirrors `SkCodec::MakeFromData(sk_sp<const SkData>, ...)` ; the
         * Kotlin overload takes a [ByteArray] for symmetry with
         * [InputStream.readBytes].
         */
        public fun MakeFromData(data: ByteArray): SkCodec? {
            for (decoder in Decoders) {
                if (decoder.matches(data)) {
                    return decoder.make(data)
                }
            }
            return null
        }

        /**
         * Read [stream] to completion and dispatch to [MakeFromData].
         * Mirrors `SkCodec::MakeFromStream(std::unique_ptr<SkStream>, …)`
         * ; the Kotlin port reads the whole stream eagerly because the
         * D3.1 facade does not yet support incremental decoding.
         *
         * The stream is **not** closed by this call — callers retain
         * ownership of the [InputStream] (idiomatic Kotlin), unlike
         * upstream which takes a `unique_ptr` and consumes it.
         */
        public fun MakeFromStream(stream: InputStream): SkCodec? =
            MakeFromData(stream.readBytes())

        /**
         * The set of registered concrete decoders. Order matters only
         * insofar as the first match wins ; in practice each format's
         * signature is non-overlapping (PNG `\x89PNG\r\n\x1a\n`, JPEG
         * `\xFF\xD8\xFF`, …) so we can keep the registry as a simple
         * list.
         *
         * `internal` — exposed to the per-format implementations under
         * `org.skia.codec.<format>` so they can plug themselves in here
         * without a separate registration step.
         */
        internal val Decoders: List<Decoder> = listOf(
            SkPngCodec.Decoder,
            SkJpegCodec.Decoder,
        )
    }

    /**
     * Mirrors `SkCodecs::Decoder` — the registration record a concrete
     * format publishes to the [SkCodec] dispatcher.
     */
    internal interface Decoder {
        /** Short identifier ("png", "jpeg", …) — useful for diagnostics. */
        val name: String

        /** True if [data]'s magic bytes match this decoder's format. */
        fun matches(data: ByteArray): Boolean

        /**
         * Build the concrete codec for [data]. May still return `null`
         * if the bytes pass [matches] but fail format-specific
         * validation (e.g. truncated PNG header).
         */
        fun make(data: ByteArray): SkCodec?
    }
}
