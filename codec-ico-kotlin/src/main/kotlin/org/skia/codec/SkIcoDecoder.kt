package org.skia.codec

import org.skia.foundation.SkData
import java.io.InputStream

/**
 * Pure-Kotlin Skia
 * [`SkIcoDecoder`](https://github.com/google/skia/blob/main/include/codec/SkIcoDecoder.h)
 * back-end. The ICO container is a tiny multi-image directory : a 6-byte
 * `ICONDIR` header (`reserved=0, type=1 ICO / 2 CUR, count`) followed by
 * `count` 16-byte `ICONDIRENTRY` records (width, height, palette,
 * reserved, planes, bpp, sizeInBytes, offset) ; each entry's payload is
 * either a self-contained PNG (sniff `89 50 4E 47`) or a "DIB" (a BMP
 * info-header + pixel data with no `BM` file header). We pick the
 * largest entry by area, prefer PNG when sizes tie, and delegate the
 * payload decode to the matching format-specific [SkCodec] :
 *  - **PNG payloads** route through [SkCodec.MakeFromData] which picks
 *    up [`org.skia.codec.png.SkPngCodec`] from the dispatch registry.
 *  - **BMP payloads** are wrapped in a synthetic 14-byte `BITMAPFILEHEADER`
 *    so the existing [`org.skia.codec.bmp.SkBmpCodec`] can decode them
 *    unchanged. The bfOffBits field points past the synthetic header
 *    plus the DIB header / palette into the pixel data, mirroring how
 *    Skia's `SkIcoCodec` synthesises a BMP stream for libpng-free
 *    decoding.
 *
 * Out of scope (matches Skia's `SkIcoCodec` exclusions) : the AND-mask
 * transparency channel that legacy 1bpp/4bpp/8bpp/24bpp DIBs append after
 * their pixel data is not synthesised back into the BMP payload — most
 * real-world icons newer than Windows XP are PNG-encoded or 32bpp BGRA
 * (which already carries alpha in the pixel data). Tests / GMs that
 * need 1bpp transparency masks should add an explicit follow-up.
 */
public object SkIcoDecoder {

    /** Decode the ICO bytes wrapped in [data]. */
    public fun Decode(data: SkData): SkCodec? = Decode(data.toByteArray())

    /**
     * Decode the ICO bytes in [data]. Returns the [SkCodec] for the
     * largest embedded image (PNG or BMP), or `null` if the bytes do
     * not parse as an ICONDIR or no embedded image is decodable.
     */
    public fun Decode(data: ByteArray): SkCodec? {
        if (!matchesIco(data, data.size)) return null
        val payload = pickBestPayload(data) ?: return null
        return SkCodec.MakeFromData(payload)
    }

    /** Decode the ICO bytes drained from [stream]. */
    public fun Decode(stream: InputStream): SkCodec? = Decode(stream.readBytes())

    /**
     * Sniff the leading 6 bytes of [data] for the ICONDIR header.
     * Mirrors upstream's `IsIco(const void*, size_t)`.
     *
     * Accepts both the ICO (type=1) and CUR (type=2) variants —
     * upstream's `SkIcoCodec` handles both. The 2-byte directory
     * count (bytes 4..5, little-endian) must be ≥ 1 ; zero-entry
     * directories are rejected because they're malformed and would
     * cause the parser to early-return anyway.
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

    /** Size of an ICONDIRENTRY record (always 16 bytes per Win32 spec). */
    private const val DIR_ENTRY_LEN = 16

    /**
     * Walk every ICONDIRENTRY in [data] and return the payload bytes
     * (PNG-as-is or BMP-with-synthesized-file-header) for the largest
     * embedded image by `width × height`. Returns `null` if the
     * directory is truncated, every entry's offset/size runs past
     * the buffer, or no entry validates as PNG / BMP.
     *
     * **Sizing convention** : the ICO directory stores a `0` byte for
     * dimensions of 256, so we widen `0 → 256` per the Win32 spec.
     * Ties are resolved deterministically — first PNG wins, then
     * the lowest dictionary order (entry index).
     */
    private fun pickBestPayload(data: ByteArray): ByteArray? {
        val count = (data[4].toInt() and 0xFF) or ((data[5].toInt() and 0xFF) shl 8)
        if (count < 1) return null
        val firstEntryOff = SNIFF_LEN
        if (firstEntryOff + count * DIR_ENTRY_LEN > data.size) return null

        var bestArea = -1
        var bestPayload: ByteArray? = null
        var bestIsPng = false
        for (i in 0 until count) {
            val off = firstEntryOff + i * DIR_ENTRY_LEN
            // Per Microsoft's spec, a stored width/height of 0 means 256.
            val w = (data[off].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val h = (data[off + 1].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val sizeInBytes = readIntLE(data, off + 8)
            val payloadOff = readIntLE(data, off + 12)
            if (sizeInBytes <= 0 || payloadOff < 0) continue
            if (payloadOff.toLong() + sizeInBytes.toLong() > data.size.toLong()) continue

            val rawPayload = data.copyOfRange(payloadOff, payloadOff + sizeInBytes)
            val isPng = isPngSignature(rawPayload)
            val payload = if (isPng) rawPayload else synthesiseBmpFileHeader(rawPayload)
                ?: continue
            val area = w * h
            // Prefer larger area. Ties → prefer PNG (already-encoded
            // image is easier on the bmp synth path). Same-format ties
            // → first encountered wins (lowest dictionary index).
            val better = when {
                area > bestArea -> true
                area == bestArea && isPng && !bestIsPng -> true
                else -> false
            }
            if (better) {
                bestArea = area
                bestPayload = payload
                bestIsPng = isPng
            }
        }
        return bestPayload
    }

    /** PNG magic bytes : `89 50 4E 47 0D 0A 1A 0A`. */
    private fun isPngSignature(data: ByteArray): Boolean {
        if (data.size < 8) return false
        return data[0] == 0x89.toByte() &&
            data[1] == 0x50.toByte() &&
            data[2] == 0x4E.toByte() &&
            data[3] == 0x47.toByte() &&
            data[4] == 0x0D.toByte() &&
            data[5] == 0x0A.toByte() &&
            data[6] == 0x1A.toByte() &&
            data[7] == 0x0A.toByte()
    }

    /**
     * Synthesise a 14-byte `BITMAPFILEHEADER` in front of an embedded
     * DIB so [SkBmpCodec] can decode it. Returns `null` when the
     * payload doesn't begin with a parseable BITMAPINFOHEADER.
     *
     * The BMP file header layout is :
     * ```
     *   bfType        2 : 'B' 'M'
     *   bfSize        4 : total file size (header + DIB)
     *   bfReserved1   2 : 0
     *   bfReserved2   2 : 0
     *   bfOffBits     4 : offset from start of file to pixel data
     * ```
     * The DIB starts with a `biSize` (4 bytes) — typically 40 for
     * a `BITMAPINFOHEADER`. Pixel-data offset is
     * `14 + biSize + paletteBytes` ; for 24/32-bpp DIBs
     * (`biBitCount ≥ 16`) the palette is empty unless
     * `biClrUsed > 0` — we trust `biClrUsed` if non-zero, else infer
     * from `biBitCount` (`1 << biBitCount` palette entries for ≤ 8 bpp
     * variants). Each palette entry is a 4-byte RGBA quad.
     */
    private fun synthesiseBmpFileHeader(dib: ByteArray): ByteArray? {
        if (dib.size < 4) return null
        val biSize = readIntLE(dib, 0)
        if (biSize < 12 || biSize > 124) return null  // 12=BITMAPCOREHEADER … 124=BITMAPV5HEADER
        if (dib.size < biSize) return null

        // BITMAPINFOHEADER (40-byte) and its V2/V3/V4/V5 supersets share
        // their first 16 bytes : biSize, biWidth, biHeight, biPlanes,
        // biBitCount, biCompression, biSizeImage, biXPelsPerMeter, ...
        // The ICO-DIB convention doubles `biHeight` to encode the AND
        // mask ; we keep the value as-is — SkBmpCodec / ImageIO interpret
        // the stored value as the pixel-data height, so the synthesised
        // BMP renders the colour plane only (the AND mask trails after
        // and is ignored).
        val bitCount = if (biSize >= 16) {
            (dib[14].toInt() and 0xFF) or ((dib[15].toInt() and 0xFF) shl 8)
        } else 0
        var clrUsed = 0
        if (biSize >= 36) clrUsed = readIntLE(dib, 32)
        val paletteEntries = when {
            clrUsed > 0 -> clrUsed
            bitCount in 1..8 -> 1 shl bitCount
            else -> 0
        }
        // Each palette entry is 4 bytes (B, G, R, reserved). Older
        // BITMAPCOREHEADER variants use 3-byte entries — ICO files
        // overwhelmingly use the 40-byte BITMAPINFOHEADER, so we
        // assume 4-byte entries here.
        val paletteBytes = paletteEntries * 4
        val pixelOffset = 14 + biSize + paletteBytes
        val totalSize = 14 + dib.size
        // Sanity : pixel offset must land inside or at the end of the
        // synthesised file (DIBs with truncated palettes would fail
        // the BMP decoder anyway).
        if (pixelOffset > totalSize) return null

        val out = ByteArray(totalSize)
        out[0] = 'B'.code.toByte()
        out[1] = 'M'.code.toByte()
        writeIntLE(out, 2, totalSize)
        // bfReserved1 / bfReserved2 are already zero (ByteArray default).
        writeIntLE(out, 10, pixelOffset)
        System.arraycopy(dib, 0, out, 14, dib.size)
        if (biSize >= 24) {
            val storedHeight = readIntLE(out, 14 + 8)
            if (storedHeight > 1) writeIntLE(out, 14 + 8, storedHeight / 2)
        }
        return out
    }

    /**
     * R-suivi.47 — [SkCodec.Decoder] registration record for ICO/CUR.
     * Auto-installed into [SkCodec.Decoders] at class-init time.
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

    private fun readIntLE(b: ByteArray, off: Int): Int {
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }

    private fun writeIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = v.toByte()
        b[off + 1] = (v ushr 8).toByte()
        b[off + 2] = (v ushr 16).toByte()
        b[off + 3] = (v ushr 24).toByte()
    }
}
