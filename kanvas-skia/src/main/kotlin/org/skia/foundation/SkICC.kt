package org.skia.foundation

import org.skia.foundation.skcms.SkcmsMatrix3x3
import org.skia.foundation.skcms.SkcmsTransferFunction
import java.nio.ByteBuffer

/**
 * R2.16 surface mirror of upstream's
 * [`SkICC`](https://github.com/google/skia/blob/main/include/encode/SkICC.h).
 *
 * Upstream `SkICC` exposes two free functions for round-tripping ICC
 * profiles : `SkWriteICCProfile` (encode an
 * `skcms_TransferFunction` + `skcms_Matrix3x3` pair to the byte form
 * of an ICC v4 profile) and a parsing entry point. The Kotlin port
 * keeps the surface in a single `object` so call sites can spell it
 * `SkICC.Make(...)` / `SkICC.WriteToICC(...)` — closer to the way
 * upstream's helper sits in tooling code than to a name-mangled
 * free function.
 *
 * **Status — R-suivi.21 enrichment**: [WriteToICC] now emits a
 * full ICC v4.3 RGB display profile with the mandatory tag table —
 * `desc`, `wtpt`, `rXYZ`, `gXYZ`, `bXYZ`, `rTRC`, `gTRC`, `bTRC`,
 * `cprt`. Bytes are accepted by ICC inspector tooling (header `acsp`
 * signature + tag count + s15Fixed16Number XYZ + parametric curve
 * type) and round-trippable as a CIE PCS-XYZ display profile. The
 * matrix columns become the RGB primary XYZ tags and the white
 * point is the sum of those primaries; the transfer function is
 * encoded as a `para` curve of type 4 (gAB CDEF, full sRGBish form).
 *
 *  - [Make] returns `null` — parsing is out of scope for R2.
 *  - [WriteToICC] returns a full ICC v4 byte blob, header + tag
 *    table + tag data.
 */
public object SkICC {

    /**
     * Mirror of upstream's `SkICC::Make(const void*, size_t)` parse
     * helper. The R2 surface returns `null` unconditionally — the
     * `skcms_ICCProfile` parser is not yet ported.
     *
     * @param profile read-only ICC bytes (any direction).
     * @param size byte count to consume from [profile].
     * @return `null` — parsing not implemented.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun Make(profile: ByteBuffer, size: Long): SkICC? = null

    /**
     * Mirror of upstream's free function
     *
     * ```
     * sk_sp<SkData> SkWriteICCProfile(
     *     const skcms_TransferFunction&,
     *     const skcms_Matrix3x3& toXYZD50);
     * ```
     *
     * Emits a complete ICC v4.3 RGB display profile with the
     * mandatory tag table — see the class-level KDoc for the tag
     * list. The byte layout follows the recipe in
     * `src/encode/SkICC.cpp` `SkWriteICCProfile` :
     *
     *  1. 128-byte header (size, version, `acsp`, D50 illuminant…).
     *  2. uint32 tag count.
     *  3. Tag table — 12 bytes per entry : { signature, offset, size }.
     *  4. Concatenated tag data blocks.
     *
     * @param transferFn sRGBish transfer function (g, a, b, c, d, e, f).
     * @param matrix 3×3 toXYZD50 matrix — columns are R/G/B primary XYZ.
     */
    @Suppress("FunctionName")
    public fun WriteToICC(
        transferFn: SkcmsTransferFunction,
        matrix: SkcmsMatrix3x3,
    ): ByteArray {
        // Build each tag's data block.
        val descData = writeTextTag("kanvas-skia profile")
        val cprtData = writeTextTag("kanvas-skia 2026")

        // RGB primary XYZ tags — matrix columns are R, G, B in PCS-XYZ.
        val rXYZData = writeXYZTag(matrix[0, 0], matrix[1, 0], matrix[2, 0])
        val gXYZData = writeXYZTag(matrix[0, 1], matrix[1, 1], matrix[2, 1])
        val bXYZData = writeXYZTag(matrix[0, 2], matrix[1, 2], matrix[2, 2])

        // White point = sum of R + G + B primaries (full RGB = white).
        val wX = matrix[0, 0] + matrix[0, 1] + matrix[0, 2]
        val wY = matrix[1, 0] + matrix[1, 1] + matrix[1, 2]
        val wZ = matrix[2, 0] + matrix[2, 1] + matrix[2, 2]
        val wtptData = writeXYZTag(wX, wY, wZ)

        // Transfer function — same for R/G/B in the simple sRGBish form.
        val trcData = writeParaCurveTag(transferFn)

        // Tag order (sig, data) — desc first matches upstream's convention.
        val tags: List<Pair<Int, ByteArray>> = listOf(
            TAG_desc to descData,
            TAG_wtpt to wtptData,
            TAG_rXYZ to rXYZData,
            TAG_gXYZ to gXYZData,
            TAG_bXYZ to bXYZData,
            TAG_rTRC to trcData,
            TAG_gTRC to trcData,
            TAG_bTRC to trcData,
            TAG_cprt to cprtData,
        )

        val tagCount = tags.size
        val tagTableSize = TAG_TABLE_ENTRY_SIZE * tagCount

        // Compute each tag's offset; align each block on a 4-byte boundary.
        // Two identical-data tag entries may share the same byte range —
        // mirror upstream by deduplicating by content reference.
        val tagOffsets = IntArray(tagCount)
        val tagSizes = IntArray(tagCount)
        var cursor = HEADER_SIZE + 4 /* tag_count */ + tagTableSize
        val seen = HashMap<ByteArray, Int>(tagCount * 2)
        for (i in 0 until tagCount) {
            val data = tags[i].second
            val cached = seen[data]
            if (cached != null) {
                tagOffsets[i] = tagOffsets[cached]
                tagSizes[i] = tagSizes[cached]
            } else {
                tagOffsets[i] = cursor
                tagSizes[i] = data.size
                seen[data] = i
                cursor += alignUp4(data.size)
            }
        }

        val profileSize = cursor
        val out = ByteArray(profileSize)

        // --- ICC header (128 bytes) ---
        // Profile size — big-endian uint32 at offset 0.
        writeUInt32BE(out, 0, profileSize.toLong())
        // Preferred CMM type — left at zero (advisory).
        // Profile version — `0x04300000` ("v4.3") at offset 8.
        writeUInt32BE(out, 8, 0x04300000L)
        // Profile/device class — `'mntr'` (display) at offset 12.
        writeAscii(out, 12, "mntr")
        // Colour space — `'RGB '` at offset 16.
        writeAscii(out, 16, "RGB ")
        // PCS — `'XYZ '` at offset 20 (display profiles use XYZ).
        writeAscii(out, 20, "XYZ ")
        // Date/time fields (offsets 24..35) — left at zero.
        // Profile file signature — `'acsp'` at offset 36 — REQUIRED.
        writeAscii(out, 36, "acsp")
        // Primary platform (offset 40), flags (44), manufacturer (48..55),
        // model (56..63), attributes (64..71) — all left at zero.
        // Rendering intent — relative colorimetric (1) at offset 64.
        writeUInt32BE(out, 64, 1L)
        // PCS illuminant — D50 (X, Y, Z) at offsets 68..79.
        writeS15Fixed16BE(out, 68, 0.96420f)
        writeS15Fixed16BE(out, 72, 1.00000f)
        writeS15Fixed16BE(out, 76, 0.82491f)
        // Profile creator signature (80..83) and id (84..99),
        // reserved (100..127) — all zero.

        // --- Tag count (uint32 BE) at offset 128 ---
        writeUInt32BE(out, HEADER_SIZE, tagCount.toLong())

        // --- Tag table — 12 bytes per entry at offset 132 ---
        var tablePos = HEADER_SIZE + 4
        for (i in 0 until tagCount) {
            writeUInt32BE(out, tablePos, tags[i].first.toLong() and 0xFFFFFFFFL)
            writeUInt32BE(out, tablePos + 4, tagOffsets[i].toLong())
            writeUInt32BE(out, tablePos + 8, tagSizes[i].toLong())
            tablePos += TAG_TABLE_ENTRY_SIZE
        }

        // --- Tag data blocks (deduplicated) ---
        val written = HashSet<Int>()
        for (i in 0 until tagCount) {
            if (!written.add(tagOffsets[i])) continue
            val data = tags[i].second
            System.arraycopy(data, 0, out, tagOffsets[i], data.size)
        }

        return out
    }

    /** ICC v4 header length, in bytes. Matches upstream's `kICCHeaderSize` (128). */
    public const val HEADER_SIZE: Int = 128

    /** Tag-table entry size : { sig (4), offset (4), size (4) }. */
    public const val TAG_TABLE_ENTRY_SIZE: Int = 12

    // --- Tag signatures (4-byte tags, packed big-endian) ---
    private const val TAG_desc: Int = 0x64657363.toInt() // 'desc'
    private const val TAG_wtpt: Int = 0x77747074.toInt() // 'wtpt'
    private const val TAG_rXYZ: Int = 0x7258595A.toInt() // 'rXYZ'
    private const val TAG_gXYZ: Int = 0x6758595A.toInt() // 'gXYZ'
    private const val TAG_bXYZ: Int = 0x6258595A.toInt() // 'bXYZ'
    private const val TAG_rTRC: Int = 0x72545243.toInt() // 'rTRC'
    private const val TAG_gTRC: Int = 0x67545243.toInt() // 'gTRC'
    private const val TAG_bTRC: Int = 0x62545243.toInt() // 'bTRC'
    private const val TAG_cprt: Int = 0x63707274.toInt() // 'cprt'

    // --- Tag-type signatures ---
    private const val TYPE_XYZ: Int = 0x58595A20.toInt() // 'XYZ '
    private const val TYPE_text: Int = 0x6D6C7563.toInt() // 'mluc'
    private const val TYPE_para: Int = 0x70617261.toInt() // 'para'

    /**
     * Encode an XYZ tag : type sig (4) + reserved (4) + 3× s15Fixed16Number.
     * Total = 20 bytes.
     */
    private fun writeXYZTag(x: Float, y: Float, z: Float): ByteArray {
        val out = ByteArray(20)
        writeUInt32BE(out, 0, TYPE_XYZ.toLong() and 0xFFFFFFFFL)
        // reserved (4..7) = 0
        writeS15Fixed16BE(out, 8, x)
        writeS15Fixed16BE(out, 12, y)
        writeS15Fixed16BE(out, 16, z)
        return out
    }

    /**
     * Encode a `mluc` (multi-localised unicode) text tag with a single
     * English-USA record. UTF-16BE encoding of the ASCII input string.
     */
    private fun writeTextTag(text: String): ByteArray {
        val textLength = text.length
        // Header is 28 bytes : type(4) + reserved(4) + nrec(4) + recsz(4)
        //                    + lang(4) + strlen(4) + stroff(4).
        val out = ByteArray(28 + 2 * textLength)
        writeUInt32BE(out, 0, TYPE_text.toLong() and 0xFFFFFFFFL)
        // reserved (4..7) = 0
        writeUInt32BE(out, 8, 1L) // record count
        writeUInt32BE(out, 12, 12L) // record size (must be 12 per spec)
        // Language : 'enUS'
        writeAscii(out, 16, "enUS")
        writeUInt32BE(out, 20, (2L * textLength)) // string length in bytes
        writeUInt32BE(out, 24, 28L) // string offset (right after the header)
        // UTF-16BE — ASCII bytes get a 0x00 high byte.
        for (i in 0 until textLength) {
            out[28 + 2 * i] = 0
            out[28 + 2 * i + 1] = text[i].code.toByte()
        }
        return out
    }

    /**
     * Encode a `para` (parametric curve) tag. Always emits the type-4
     * form (gAB CDEF, 7 s15Fixed16Number parameters) which is the full
     * sRGBish curve form upstream uses for non-trivial transfer fns.
     * Layout : type(4) + reserved(4) + funcType(2) + reserved(2) + 7×4.
     * Total = 40 bytes.
     */
    private fun writeParaCurveTag(fn: SkcmsTransferFunction): ByteArray {
        val out = ByteArray(40)
        writeUInt32BE(out, 0, TYPE_para.toLong() and 0xFFFFFFFFL)
        // reserved (4..7) = 0
        // Function type : 4 = gAB CDEF (full sRGBish form).
        out[8] = 0
        out[9] = 4
        // reserved (10..11) = 0
        writeS15Fixed16BE(out, 12, fn.g)
        writeS15Fixed16BE(out, 16, fn.a)
        writeS15Fixed16BE(out, 20, fn.b)
        writeS15Fixed16BE(out, 24, fn.c)
        writeS15Fixed16BE(out, 28, fn.d)
        writeS15Fixed16BE(out, 32, fn.e)
        writeS15Fixed16BE(out, 36, fn.f)
        return out
    }

    private fun alignUp4(n: Int): Int = (n + 3) and 3.inv()

    private fun writeUInt32BE(dst: ByteArray, off: Int, v: Long) {
        dst[off] = ((v ushr 24) and 0xFF).toByte()
        dst[off + 1] = ((v ushr 16) and 0xFF).toByte()
        dst[off + 2] = ((v ushr 8) and 0xFF).toByte()
        dst[off + 3] = (v and 0xFF).toByte()
    }

    private fun writeAscii(dst: ByteArray, off: Int, s: String) {
        for (i in s.indices) dst[off + i] = s[i].code.toByte()
    }

    private fun writeS15Fixed16BE(dst: ByteArray, off: Int, f: Float) {
        // Saturating round-to-nearest in double precision, matches
        // upstream's float_round_to_fixed in SkICC.cpp.
        val scaled = Math.floor(f.toDouble() * 65536.0 + 0.5).toLong()
        val clamped = when {
            scaled > Int.MAX_VALUE.toLong() -> Int.MAX_VALUE.toLong()
            scaled < Int.MIN_VALUE.toLong() -> Int.MIN_VALUE.toLong()
            else -> scaled
        }
        writeUInt32BE(dst, off, clamped and 0xFFFFFFFFL)
    }
}
