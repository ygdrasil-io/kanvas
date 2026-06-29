package org.graphiks.kanvas.codec

import org.skia.foundation.SkData
import java.io.InputStream

/**
 * R3.10 stub of Skia's
 * [`JpegxlDecoder`](https://github.com/google/skia/blob/main/include/codec/JpegxlDecoder.h)
 * namespace.
 *
 * Mirrors the upstream `JpegxlDecoder::{IsJpegxl, Decode}` factory
 * shape so call sites compile against the full API surface, but **the
 * actual decoder is not yet implemented** — every [Decode] overload
 * returns `null`. Wiring a real JPEG-XL decoder requires a libjxl
 * binding, tracked as **R-suivi.28** alongside the other extended
 * codec back-ends.
 *
 * The [IsJpegxl] signature check is real : it sniffs the leading
 * bytes for either the raw codestream marker (`FF 0A`) or the
 * ISO-BMFF box wrapper (`00 00 00 0C 4A 58 4C 20 0D 0A 87 0A`).
 */
public object JpegxlDecoder {

    /**
     * Stubbed JPEG-XL decode. Always returns `null`. See class kdoc
     * for the R-suivi.28 follow-up that wires up libjxl.
     */
    public fun Decode(data: SkData): Codec? = null

    /**
     * Stubbed JPEG-XL decode. Always returns `null`. See class kdoc
     * for the R-suivi.28 follow-up that wires up libjxl.
     */
    public fun Decode(data: ByteArray): Codec? = null

    /**
     * Stubbed JPEG-XL decode. Always returns `null`. See class kdoc
     * for the R-suivi.28 follow-up that wires up libjxl.
     */
    public fun Decode(stream: InputStream): Codec? = null

    /**
     * Sniff the leading bytes of [data] for a JPEG-XL signature.
     * Mirrors upstream's `IsJpegxl(const void*, size_t)`.
     *
     * Accepts both forms emitted by libjxl :
     *  - **raw codestream** : `FF 0A` at offset 0.
     *  - **ISO-BMFF wrapper** :
     *    `00 00 00 0C 4A 58 4C 20 0D 0A 87 0A` at offset 0 (a 12-byte
     *    box `JXL ` signature followed by `\r\n\x87\n`).
     */
    public fun IsJpegxl(data: ByteArray, length: Int = data.size): Boolean =
        matchesJpegxl(data, length)

    /**
     * Read up to 12 leading bytes from [stream] and run the same
     * sniff as [IsJpegxl]. See [sniffStream] for the stream-rewind
     * contract.
     */
    public fun IsJpegxl(stream: InputStream): Boolean =
        sniffStream(stream, SNIFF_LEN, ::matchesJpegxl)

    /** Number of leading bytes [IsJpegxl] needs to evaluate both signatures. */
    private const val SNIFF_LEN = 12

    /** ISO-BMFF JXL signature box : `00 00 00 0C 4A 58 4C 20 0D 0A 87 0A`. */
    private val ISO_BMFF_SIGNATURE = byteArrayOf(
        0x00, 0x00, 0x00, 0x0C,
        0x4A, 0x58, 0x4C, 0x20,
        0x0D, 0x0A, 0x87.toByte(), 0x0A,
    )

    /**
     * R-suivi.47 — [Codec.Decoder] registration record for JPEG-XL.
     * Auto-installed into [Codec.Decoders] at class-init time. `make`
     * returns `null` until R-suivi.28 wires up libjxl.
     */
    internal val RegistryEntry: Codec.Decoder = object : Codec.Decoder {
        override val name: String = "jpegxl"
        override fun matches(data: ByteArray): Boolean = IsJpegxl(data)
        override fun make(data: ByteArray): Codec? = Decode(data)
    }

    private fun matchesJpegxl(data: ByteArray, length: Int): Boolean {
        // Raw codestream : two-byte marker FF 0A.
        if (length >= 2 &&
            data[0] == 0xFF.toByte() &&
            data[1] == 0x0A.toByte()
        ) return true
        // ISO-BMFF wrapper : 12-byte JXL signature box.
        if (length < ISO_BMFF_SIGNATURE.size) return false
        for (i in ISO_BMFF_SIGNATURE.indices) {
            if (data[i] != ISO_BMFF_SIGNATURE[i]) return false
        }
        return true
    }
}
