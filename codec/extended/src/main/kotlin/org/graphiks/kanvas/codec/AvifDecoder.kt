package org.graphiks.kanvas.codec

import org.skia.foundation.SkData
import java.io.InputStream

/**
 * R3.10 stub of Skia's
 * [`AvifDecoder`](https://github.com/google/skia/blob/main/include/codec/AvifDecoder.h)
 * namespace.
 *
 * Mirrors the upstream `AvifDecoder::{IsAvif, Decode}` factory shape so
 * call sites compile against the full API surface, but **the actual
 * decoder is not yet implemented** — every [Decode] overload returns
 * `null`. Wiring a real AVIF decoder requires a libavif (or crabby-avif)
 * binding, tracked as **R-suivi.28** alongside the other extended codec
 * back-ends.
 *
 * The [IsAvif] signature check is real : it sniffs the leading bytes for
 * the ISO-BMFF `ftypavif` / `ftypavis` (still / sequence) brand and is
 * suitable as a routing predicate for [Codec.MakeFromData] once the
 * full back-end lands. Upstream also accepts the AV1-Image-File-Format
 * `ftypheic`/`ftypmif1` brands when transcoded ; this stub limits the
 * sniff to the two AVIF-specific brands so it can't false-positive on
 * a plain HEIF file (HEIF gets its own decoder slot in the upstream
 * registry).
 *
 * Upstream layout : the C++ header exposes two sub-namespaces
 * (`AvifDecoder::LibAvif` and `AvifDecoder::CrabbyAvif`) for the
 * two competing back-ends. The Kotlin port collapses them into the
 * single outer object — until a real back-end lands there's nothing
 * to disambiguate.
 */
public object AvifDecoder {

    /**
     * Stubbed AVIF decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libavif.
     */
    public fun Decode(data: SkData): Codec? = null

    /**
     * Stubbed AVIF decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libavif.
     */
    public fun Decode(data: ByteArray): Codec? = null

    /**
     * Stubbed AVIF decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libavif.
     *
     * The stream is **not** closed by this call — callers retain
     * ownership of the [InputStream] (idiomatic Kotlin), matching the
     * convention used by [Codec.MakeFromStream].
     */
    public fun Decode(stream: InputStream): Codec? = null

    /**
     * Sniff the leading bytes of [data] for the ISO-BMFF `ftypavif` or
     * `ftypavis` major brand. Mirrors upstream's `IsAvif(const void*,
     * size_t)`.
     *
     * The signature lives at offsets 4..11 of the file (the 4-byte box
     * size precedes the 4-byte box type, followed by the 4-byte major
     * brand). `length` defaults to the full byte-array size — callers
     * holding a longer buffer can pass a shorter window to limit the
     * sniff to the first chunk read.
     */
    public fun IsAvif(data: ByteArray, length: Int = data.size): Boolean =
        matchesAvif(data, length)

    /**
     * Read up to 12 leading bytes from [stream] and run the same brand
     * sniff as [IsAvif]. The stream **must** support [InputStream.mark]
     * / [InputStream.reset] — this call rewinds the stream so the
     * caller can re-read the same bytes via [Decode] once the back-end
     * is wired up.
     */
    public fun IsAvif(stream: InputStream): Boolean = sniffStream(stream, SNIFF_LEN, ::matchesAvif)

    /** Number of leading bytes [IsAvif] needs to evaluate the ISO-BMFF brand. */
    private const val SNIFF_LEN = 12

    /**
     * R-suivi.47 — [Codec.Decoder] registration record for AVIF.
     * Auto-installed into [Codec.Decoders] at class-init time. Its
     * `make` returns `null` since the real libavif back-end is
     * R-suivi.28 ; the routing slot exists so future PRs can call
     * [Codec.Decoders.register] with a real entry of the same name.
     */
    internal val RegistryEntry: Codec.Decoder = object : Codec.Decoder {
        override val name: String = "avif"
        override fun matches(data: ByteArray): Boolean = IsAvif(data)
        override fun make(data: ByteArray): Codec? = Decode(data)
    }

    private fun matchesAvif(data: ByteArray, length: Int): Boolean {
        if (length < SNIFF_LEN) return false
        // Box-type bytes 4..7 must be 'ftyp'.
        if (data[4] != 'f'.code.toByte() ||
            data[5] != 't'.code.toByte() ||
            data[6] != 'y'.code.toByte() ||
            data[7] != 'p'.code.toByte()
        ) return false
        // Major-brand bytes 8..11 must be 'avif' or 'avis'.
        val b8 = data[8]; val b9 = data[9]; val b10 = data[10]; val b11 = data[11]
        if (b8 != 'a'.code.toByte() || b9 != 'v'.code.toByte() || b10 != 'i'.code.toByte()) {
            return false
        }
        return b11 == 'f'.code.toByte() || b11 == 's'.code.toByte()
    }
}
