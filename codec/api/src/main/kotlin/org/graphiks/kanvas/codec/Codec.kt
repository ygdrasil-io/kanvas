package org.graphiks.kanvas.codec

import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.skia.foundation.skcms.SkcmsICCProfile
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.ServiceLoader

public interface CodecDecoder {
    public val name: String
    public fun matches(data: ByteArray): Boolean
    public fun make(data: ByteArray): Codec?
}

public interface CodecRegistry {
    public fun all(): List<Codec.Decoder>
    public fun register(decoder: Codec.Decoder)
    public fun unregister(name: String): Boolean
    public fun dispatch(data: ByteArray): Codec?
}

public interface CodecDecoderProvider {
    public fun decoders(): List<Codec.Decoder>
}

/**
 * Mirrors Skia's
 * [`Codec`](https://github.com/google/skia/blob/main/include/codec/Codec.h)
 * — a stateless, type-erased decoder facade. A concrete [Codec] is
 * obtained from [MakeFromData] / [MakeFromStream] ; the factory sniffs
 * the encoded bytes, picks the matching format-specific subclass, and
 * the caller then drives the decode through [getPixels] (or its
 * convenience overloads).
 *
 * The facade is implemented by format-specific codecs under
 * `org.graphiks.kanvas.codec.<format>` and each decoder registers itself with
 * [Decoders].
 *
 * **API surface** (mapped from upstream) :
 *  - [getInfo] — a "reasonable [SkImageInfo] to decode into" ; carries
 *    the encoded width/height, the natural [org.skia.foundation.SkColorType]
 *    (F16 for ≥16-bpc PNGs, 8888 otherwise), and the colour space derived
 *    from the embedded ICC profile (or sRGB if the file has none).
 *  - [getPixels] — fills a caller-allocated [SkBitmap], mirroring
 *    `Result Codec::getPixels(const SkImageInfo&, void*, size_t,
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
public abstract class Codec protected constructor() {

    /**
     * Mirrors Skia's `Codec::Result`. Only [kSuccess] and the four
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
     * carried none. Mirrors `Codec::getICCProfile()`. This reports
     * encoded ICC provenance only; a codec may resolve [getInfo]'s
     * colour space from non-ICC container metadata without exposing a
     * fabricated ICC profile here.
     */
    public abstract fun getICCProfile(): SkcmsICCProfile?

    /**
     * The EXIF Orientation tag carried by the encoded stream — i.e. how
     * the source pixels were stored relative to the scene's intended
     * top-left. Mirrors `SkEncodedOrigin Codec::getOrigin() const`.
     *
     * **Default :** [SkEncodedOrigin.kTopLeft] — the encoded grid is
     * already upright. Format-specific subclasses with EXIF-aware
     * decoders override to surface
     * the parsed value ; callers that wish to materialise the rotation
     * post-decode can compose [SkEncodedOrigin.toMatrix] /
     * [org.skia.utils.PixmapUtils.Orient].
     */
    public open fun getOrigin(): SkEncodedOrigin = SkEncodedOrigin.kTopLeft

    public fun dimensions(): SkISize = getInfo().dimensions()
    public fun bounds(): SkIRect = getInfo().bounds()

    /**
     * Decode into [dst], whose [SkBitmap.width] / [SkBitmap.height] /
     * [SkBitmap.colorType] / [SkBitmap.colorSpace] **must** match the
     * `info` argument. Mirrors `Codec::getPixels(const SkImageInfo&,
     * void* dst, size_t rowBytes, const Options*)` — Kotlin folds the
     * `(dst, rowBytes)` pair into a single [SkBitmap] since our bitmap
     * already carries its stride.
     *
     * A codec may additionally require `info` to match its source geometry,
     * alpha representation, and colour space when it does not implement the
     * requested scale or transforms. Returns [Result.kInvalidParameters] if
     * the bitmap disagrees with `info`, [Result.kInvalidScale] or
     * [Result.kInvalidConversion] for unsupported source requests, and
     * [Result.kSuccess] when the pixels were written.
     */
    public abstract fun getPixels(info: SkImageInfo, dst: SkBitmap): Result

    /** Default-info overload — equivalent to `getPixels(getInfo(), dst)`. */
    public fun getPixels(dst: SkBitmap): Result = getPixels(getInfo(), dst)

    /**
     * Mirrors `Codec::Result Codec::getPixels(const SkImageInfo&,
     * void*, size_t, const Options*)`. The Kotlin signature folds
     * `(dst, rowBytes)` into [SkBitmap] (see [getPixels] above) and
     * adds the [opts] hook for animated decoders. Default base-class
     * behaviour ignores [opts] and dispatches to the single-frame path
     * — only multi-frame codecs (GIF, animated WebP) override.
     */
    public open fun getPixels(info: SkImageInfo, dst: SkBitmap, opts: Options): Result =
        getPixels(info, dst)

    /**
     * Mirrors `Codec::Options`
     * ([include/codec/Codec.h](https://github.com/google/skia/blob/main/include/codec/Codec.h#L336)).
     *
     * Drives a single decode call. Only the per-frame fields that the
     * raster facade understands are surfaced ; subset / zero-init
     * /priorFrame-cache size / scanline arguments stay collapsed onto
     * sensible defaults until a GM consumer needs them.
     *
     * **Fields** :
     *  - [frameIndex] — which frame of an animated codec to decode.
     *    Default `0` matches the upstream "first frame" contract for
     *    static codecs.
     *  - [priorFrame] — index of the frame whose pixels are already
     *    present in the destination bitmap, used by the codec to skip
     *    decoding the dependency chain. [kNoFrame] (the default) tells
     *    the codec it must reconstruct the prior frames itself.
     */
    public data class Options(
        val frameIndex: Int = 0,
        val priorFrame: Int = kNoFrame,
    )

    /**
     * Mirrors `Codec::FrameInfo`
     * ([include/codec/Codec.h](https://github.com/google/skia/blob/main/include/codec/Codec.h#L684)).
     *
     * Per-frame metadata returned by [getFrameInfo]. The codec
     * surface keeps the four fields the GM consumers (`AnimatedGifGM`,
     * `AnimCodecPlayerExifGM`) actually look at — required-frame back-
     * reference, frame duration in milliseconds, alpha type, and the
     * dirty rectangle. Disposal / blend / fully-received flags stay
     * elided until a downstream consumer needs them.
     */
    public data class FrameInfo(
        val requiredFrame: Int = kNoFrame,
        val durationMs: Int = 0,
        val alphaType: SkAlphaType = SkAlphaType.kUnpremul,
        val frameRect: SkIRect = SkIRect.MakeEmpty(),
    )

    /**
     * Mirrors `Codec::getFrameCount()` — number of frames in the
     * encoded stream. Static formats return `1` ; multi-frame formats
     * (GIF, animated WebP) return their actual frame count.
     */
    public open fun getFrameCount(): Int = 1

    /**
     * Mirrors `Codec::getFrameInfo()` (vector overload). Static
     * codecs return a single-element list describing the lone frame ;
     * multi-frame codecs override with the real per-frame metadata.
     */
    public open fun getFrameInfo(): List<FrameInfo> = listOf(
        FrameInfo(
            requiredFrame = kNoFrame,
            durationMs = 0,
            alphaType = getInfo().alphaType,
            frameRect = SkIRect.MakeWH(getInfo().width, getInfo().height),
        ),
    )

    /**
     * Mirrors `Codec::getRepetitionCount()` for animated formats.
     *
     * `0` means the animation should play once and stop,
     * [kRepetitionCountInfinite] means loop forever, and positive values
     * are extra full passes after the first. Static formats return `0`.
     */
    public open fun getRepetitionCount(): Int = 0

    /**
     * Allocate a fresh [SkBitmap] matching `info` and decode into it.
     * Mirrors upstream's `std::tuple<sk_sp<SkImage>, Codec::Result>
     * Codec::getImage(const SkImageInfo&)`. Returns `(null, result)`
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
         * Mirrors `Codec::kNoFrame` — sentinel for [Options.priorFrame]
         * and [FrameInfo.requiredFrame] indicating the absence of a
         * back-reference / dependency frame.
         */
        public const val kNoFrame: Int = -1

        /**
         * Sentinel returned by [getRepetitionCount] for animations whose
         * container requests infinite playback.
         */
        public const val kRepetitionCountInfinite: Int = -1

        /**
         * Sniff the leading bytes of [data] and return a codec that can
         * decode it, or `null` if no registered format matches.
         *
         * Mirrors `Codec::MakeFromData(sk_sp<const SkData>, ...)` ; the
         * Kotlin overload takes a [ByteArray] for symmetry with
         * [InputStream.readBytes].
         */
        public fun MakeFromData(data: ByteArray): Codec? {
            for (decoder in Decoders.all()) {
                if (decoder.matches(data)) {
                    return decoder.make(data)
                }
            }
            return null
        }

        /**
         * Read [stream] to completion and dispatch to [MakeFromData].
         * Mirrors `Codec::MakeFromStream(std::unique_ptr<SkStream>, …)`
         * ; the Kotlin port reads the whole stream eagerly because the
         * D3.1 facade does not yet support incremental decoding.
         *
         * The stream is **not** closed by this call — callers retain
         * ownership of the [InputStream] (idiomatic Kotlin), unlike
         * upstream which takes a `unique_ptr` and consumes it.
         */
        public fun MakeFromStream(stream: InputStream): Codec? =
            MakeFromData(stream.readBytes())

        /** Read [stream] up to [maxEncodedBytes] and dispatch to [MakeFromData]. */
        public fun MakeFromStream(
            stream: InputStream,
            maxEncodedBytes: Long,
        ): Codec? = readStreamWithinLimit(stream, maxEncodedBytes)?.let(::MakeFromData)

        private fun readStreamWithinLimit(stream: InputStream, maxEncodedBytes: Long): ByteArray? {
            if (maxEncodedBytes !in 0..MAX_STREAM_BYTES_WITH_SENTINEL) return null
            val readLimit = maxEncodedBytes + 1
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(STREAM_READ_BUFFER_SIZE)
            var readBytes = 0L
            while (readBytes < readLimit) {
                val requested = minOf(buffer.size.toLong(), readLimit - readBytes).toInt()
                val count = stream.read(buffer, 0, requested)
                if (count < 0) break
                if (count == 0) {
                    val byte = stream.read()
                    if (byte < 0) break
                    output.write(byte)
                    readBytes++
                } else {
                    output.write(buffer, 0, count)
                    readBytes += count.toLong()
                }
            }
            return output.toByteArray().takeIf { it.size.toLong() <= maxEncodedBytes }
        }

        public const val DEFAULT_MAX_STREAM_BYTES: Long = 64L * 1024 * 1024

        private const val STREAM_READ_BUFFER_SIZE: Int = 8 * 1024
        private const val MAX_MATERIALIZABLE_STREAM_BYTES: Long = Int.MAX_VALUE.toLong() - 8L
        private const val MAX_STREAM_BYTES_WITH_SENTINEL: Long = MAX_MATERIALIZABLE_STREAM_BYTES - 1L
    }

    /**
     * Registry of concrete decoders consulted by [MakeFromData].
     *
     * **R-suivi.47** — the registry exposes a public [Decoders.register]
     * entry-point so additional formats wired up after the initial
     * D3.1–D3.4 batch (AVIF, JPEG-XL, RAW, ICO — see the stubs under
     * `org.graphiks.kanvas.codec.{AvifDecoder, JpegxlDecoder, RawDecoder,
     * IcoDecoder}`) can plug themselves in without editing this
     * file. Built-in decoders self-register at class-init time via
     * the eager-initialised list below.
     *
     * Order matters only insofar as the first match wins ; in
     * practice each format's signature is non-overlapping (PNG
     * `\x89PNG\r\n\x1a\n`, JPEG `\xFF\xD8\xFF`, …) so we can keep
     * the registry as a simple list. Late-registered decoders are
     * appended to the end ; the WBMP entry intentionally stays
     * last because its signature is the loosest of the built-ins.
     *
     * **Lifetime** : the registry is a process-wide singleton ; a
     * `register` call survives for the lifetime of the JVM. Tests that
     * install custom decoders should call [unregister] (or restore the
     * original entry via a second [register]) in `@AfterEach` to keep
     * sibling tests deterministic.
     */
    public object Decoders : CodecRegistry {
        private val entries: MutableList<Decoder> = mutableListOf()
        private var providersLoaded: Boolean = false
        private val defaultOrder: Map<String, Int> = listOf(
            "png",
            "jpeg-ls",
            "jpeg",
            "jpeg2000",
            "jpegxl",
            "gif",
            "bmp",
            "webp",
            "wbmp",
            "avif",
            "ico",
            "raw",
        ).withIndex().associate { it.value to it.index }

        /**
         * Snapshot of the registered decoders, in dispatch order.
         * Returned as a read-only list — mutate the registry via
         * [register] / [unregister].
         */
        @Synchronized
        override fun all(): List<Decoder> {
            ensureProvidersLoaded()
            return entries.toList()
        }

        /**
         * Append a [decoder] to the dispatch list. Mirrors upstream's
         * `Codecs::Register` — call once at static-init time per
         * format. Calling [register] with the same [Decoder.name]
         * twice replaces the prior registration (so a real back-end
         * can transparently supersede its stub).
         */
        @Synchronized
        override fun register(decoder: Decoder) {
            val existing = entries.indexOfFirst { it.name == decoder.name }
            if (existing >= 0) {
                entries[existing] = decoder
            } else {
                entries.add(decoder)
            }
        }

        /**
         * Drop the decoder named [name]. No-op if no such decoder is
         * registered. Returns `true` when an entry was removed.
         */
        @Synchronized
        override fun unregister(name: String): Boolean {
            ensureProvidersLoaded()
            return entries.removeAll { it.name == name }
        }

        /** True if any decoder named [name] is currently registered. */
        @Synchronized
        public fun contains(name: String): Boolean {
            ensureProvidersLoaded()
            return entries.any { it.name == name }
        }

        /**
         * Lookup and dispatch helper — sniffs [data] against every
         * registered decoder in order and returns the first match's
         * `make` result (or `null` if no signature matches / the
         * matched decoder's `make` returns `null`). Equivalent to
         * [Codec.MakeFromData].
         */
        override fun dispatch(data: ByteArray): Codec? = MakeFromData(data)

        @Synchronized
        private fun ensureProvidersLoaded() {
            if (providersLoaded) return
            ServiceLoader.load(CodecDecoderProvider::class.java).forEach { provider ->
                provider.decoders().forEach(::register)
            }
            entries.sortBy { defaultOrder[it.name] ?: Int.MAX_VALUE }
            providersLoaded = true
        }
    }

    /**
     * Mirrors `Codecs::Decoder` — the registration record a concrete
     * format publishes to the [Codec] dispatcher.
     *
     * R-suivi.47 — promoted from `internal` to `public` so external
     * codec back-ends (libavif, libjxl, libraw, ICO directory parser…)
     * can build their own [Decoder] and plug it into [Codec.Decoders]
     * without sitting under `org.graphiks.kanvas.codec.<format>`.
     */
    public interface Decoder : CodecDecoder {
        /** Short identifier ("png", "jpeg", …) — useful for diagnostics. */
        override val name: String

        /** True if [data]'s magic bytes match this decoder's format. */
        override fun matches(data: ByteArray): Boolean

        /**
         * Build the concrete codec for [data]. May still return `null`
         * if the bytes pass [matches] but fail format-specific
         * validation (e.g. truncated PNG header).
         */
        override fun make(data: ByteArray): Codec?
    }
}
