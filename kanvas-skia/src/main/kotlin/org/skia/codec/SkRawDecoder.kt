package org.skia.codec

import org.skia.foundation.SkData
import java.io.InputStream

/**
 * R3.10 stub of Skia's
 * [`SkRawDecoder`](https://github.com/google/skia/blob/main/include/codec/SkRawDecoder.h)
 * namespace.
 *
 * Mirrors the upstream `SkRawDecoder::{IsRaw, Decode}` factory shape
 * so call sites compile against the full API surface, but **the
 * actual decoder is not yet implemented** — every [Decode] overload
 * returns `null`. A real RAW decoder requires a libraw (or piex +
 * dng_sdk) binding, tracked as **R-suivi.28** alongside the other
 * extended codec back-ends.
 *
 * Upstream's `IsRaw` is intentionally over-permissive (returns
 * `true` for *any* input) because raw camera signatures are messy and
 * the upstream registry resolves the false-positive at decode time by
 * checking RAW last. The Kotlin port narrows the sniff to **TIFF-like
 * signatures** (`II*\0` little-endian and `MM\0*` big-endian) so the
 * stub doesn't blanket-claim every byte buffer is RAW — this matches
 * the dominant raw container in the upstream test corpus (DNG, CR2,
 * NEF, ARW are all TIFF-based) without false-positively swallowing
 * unrelated data.
 */
public object SkRawDecoder {

    /**
     * Stubbed RAW decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libraw / dng_sdk.
     */
    public fun Decode(data: SkData): SkCodec? = null

    /**
     * Stubbed RAW decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libraw / dng_sdk.
     */
    public fun Decode(data: ByteArray): SkCodec? = null

    /**
     * Stubbed RAW decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up libraw / dng_sdk.
     */
    public fun Decode(stream: InputStream): SkCodec? = null

    /**
     * Sniff the leading bytes of [data] for a TIFF-like header.
     *
     * Upstream returns `true` unconditionally ; the R3.10 stub is
     * stricter (see class kdoc) and returns `true` only when the
     * first 4 bytes look like a TIFF endianness + magic-42 marker :
     *  - `II 2A 00` (little-endian TIFF / DNG / CR2 / NEF / ARW…)
     *  - `MM 00 2A` (big-endian TIFF)
     *
     * This is good enough to route the DM-style "is this a CR2 ?"
     * sniff to the [SkRawDecoder] slot without false-positively
     * stealing JPEG / PNG bytes. Once the real back-end lands, the
     * decode itself will reject any TIFF that isn't a known raw
     * variant.
     */
    public fun IsRaw(data: ByteArray, length: Int = data.size): Boolean =
        matchesRaw(data, length)

    /**
     * Read up to 4 leading bytes from [stream] and run the same
     * sniff as [IsRaw]. See [sniffStream] for the stream-rewind
     * contract.
     */
    public fun IsRaw(stream: InputStream): Boolean =
        sniffStream(stream, SNIFF_LEN, ::matchesRaw)

    /** Number of leading bytes [IsRaw] needs to evaluate the TIFF header. */
    private const val SNIFF_LEN = 4

    /**
     * R-suivi.47 — [SkCodec.Decoder] registration record for RAW. The
     * registry installs this **after** the more specific formats since
     * the TIFF-like signature overlaps with plain TIFF files. `make`
     * returns `null` until R-suivi.28 wires up libraw / dng_sdk.
     */
    internal val RegistryEntry: SkCodec.Decoder = object : SkCodec.Decoder {
        override val name: String = "raw"
        override fun matches(data: ByteArray): Boolean = IsRaw(data)
        override fun make(data: ByteArray): SkCodec? = Decode(data)
    }

    private fun matchesRaw(data: ByteArray, length: Int): Boolean {
        if (length < 4) return false
        // Little-endian TIFF : 'I' 'I' 0x2A 0x00.
        if (data[0] == 'I'.code.toByte() &&
            data[1] == 'I'.code.toByte() &&
            data[2] == 0x2A.toByte() &&
            data[3] == 0x00.toByte()
        ) return true
        // Big-endian TIFF : 'M' 'M' 0x00 0x2A.
        if (data[0] == 'M'.code.toByte() &&
            data[1] == 'M'.code.toByte() &&
            data[2] == 0x00.toByte() &&
            data[3] == 0x2A.toByte()
        ) return true
        return false
    }
}
