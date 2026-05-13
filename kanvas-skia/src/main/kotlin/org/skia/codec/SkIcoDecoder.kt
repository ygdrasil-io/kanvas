package org.skia.codec

import org.skia.foundation.SkData
import java.io.InputStream

/**
 * R3.10 stub of Skia's
 * [`SkIcoDecoder`](https://github.com/google/skia/blob/main/include/codec/SkIcoDecoder.h)
 * namespace.
 *
 * Mirrors the upstream `SkIcoDecoder::{IsIco, Decode}` factory shape
 * so call sites compile against the full API surface, but **the
 * actual decoder is not yet implemented** — every [Decode] overload
 * returns `null`. A real ICO decoder requires the multi-image ICO
 * directory parser (the format stores a directory of PNG / BMP
 * sub-images and the decoder picks the best size match), tracked as
 * **R-suivi.28** alongside the other extended codec back-ends.
 *
 * The [IsIco] signature check is real : the 6-byte ICONDIR header is
 * `00 00 01 00` (`reserved=0, type=ICO`) or `00 00 02 00`
 * (`reserved=0, type=CUR`), followed by a 2-byte little-endian count
 * of directory entries.
 */
public object SkIcoDecoder {

    /**
     * Stubbed ICO decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up the ICO directory
     * parser.
     */
    public fun Decode(data: SkData): SkCodec? = null

    /**
     * Stubbed ICO decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up the ICO directory
     * parser.
     */
    public fun Decode(data: ByteArray): SkCodec? = null

    /**
     * Stubbed ICO decode. Always returns `null`. See class kdoc for
     * the R-suivi.28 follow-up that wires up the ICO directory
     * parser.
     */
    public fun Decode(stream: InputStream): SkCodec? = null

    /**
     * Sniff the leading 6 bytes of [data] for the ICONDIR header.
     * Mirrors upstream's `IsIco(const void*, size_t)`.
     *
     * Accepts both the ICO (type=1) and CUR (type=2) variants —
     * upstream's `SkIcoCodec` handles both. The 2-byte directory
     * count (bytes 4..5, little-endian) must be ≥ 1 ; zero-entry
     * directories are rejected because they're malformed and would
     * cause the future real parser to early-return anyway.
     */
    public fun IsIco(data: ByteArray, length: Int = data.size): Boolean =
        matchesIco(data, length)

    /**
     * Read up to 6 leading bytes from [stream] and run the same
     * sniff as [IsIco]. See [sniffStream] for the stream-rewind
     * contract.
     */
    public fun IsIco(stream: InputStream): Boolean =
        sniffStream(stream, SNIFF_LEN, ::matchesIco)

    /** Number of leading bytes [IsIco] needs to evaluate the ICONDIR header. */
    private const val SNIFF_LEN = 6

    /**
     * R-suivi.47 — [SkCodec.Decoder] registration record for ICO/CUR.
     * Auto-installed into [SkCodec.Decoders] at class-init time. `make`
     * returns `null` until R-suivi.28 wires up the directory parser.
     */
    internal val RegistryEntry: SkCodec.Decoder = object : SkCodec.Decoder {
        override val name: String = "ico"
        override fun matches(data: ByteArray): Boolean = IsIco(data)
        override fun make(data: ByteArray): SkCodec? = Decode(data)
    }

    private fun matchesIco(data: ByteArray, length: Int): Boolean {
        if (length < SNIFF_LEN) return false
        // Reserved field bytes 0..1 must be zero.
        if (data[0] != 0x00.toByte() || data[1] != 0x00.toByte()) return false
        // Type field bytes 2..3 must be 01 00 (ICO) or 02 00 (CUR), little-endian.
        val type = (data[2].toInt() and 0xFF) or ((data[3].toInt() and 0xFF) shl 8)
        if (type != 1 && type != 2) return false
        // Directory entry count bytes 4..5 must be ≥ 1 (little-endian).
        val count = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        return count >= 1
    }
}
