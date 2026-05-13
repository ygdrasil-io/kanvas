package org.skia.foundation

import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTransferFunction
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
 * **Status — R2 stub** : the actual ICC parser / writer is several
 * thousand lines of byte-tag bookkeeping (see upstream
 * `src/encode/SkICC.cpp`). The R2 batch ships the public surface so
 * downstream call sites compile, and writes a **minimal 128-byte ICC
 * v4 header** for [WriteToICC] — enough that the returned bytes are
 * recognised as an ICC profile by tools that only sniff the header
 * magic, but **not** a fully-valid profile : the body lacks the
 * `'rTRC' / 'gTRC' / 'bTRC' / 'rXYZ' / 'gXYZ' / 'bXYZ' / 'wtpt'`
 * tags upstream emits.
 *
 * Tracked as a follow-up under "R-suivi : SkICC bodies" — when a
 * workflow needs round-trippable ICC bytes (e.g. tagging a PNG
 * `iCCP` chunk with a working-space profile), the body emission will
 * be ported in a dedicated slice.
 *
 *  - [Make] returns `null` — parsing is out of scope for R2.
 *  - [WriteToICC] returns a header-only ICC v4 byte blob.
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
     * The R2 stub emits a **minimal 128-byte ICC v4 header** : the
     * `acsp` signature at offset 36 is the bit a sniffer looks at,
     * the size field at offset 0 is the byte count, and the profile
     * version at offset 8 is `0x04300000` ("v4.3"). All other bytes
     * are zero. The [transferFn] and [matrix] arguments are accepted
     * for API parity but **not yet serialised** — the body tags
     * (`rTRC`/`gTRC`/`bTRC`, `rXYZ`/`gXYZ`/`bXYZ`, `wtpt`, `desc`,
     * `cprt`) are pending the R-suivi follow-up.
     */
    @Suppress("UNUSED_PARAMETER", "FunctionName")
    public fun WriteToICC(
        transferFn: SkcmsTransferFunction,
        matrix: SkcmsMatrix3x3,
    ): ByteArray {
        val out = ByteArray(HEADER_SIZE)
        // Profile size — big-endian uint32 at offset 0.
        writeUInt32BE(out, 0, HEADER_SIZE.toLong())
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
        // Primary platform (offset 40) and flags (44) left at zero.
        // Device manufacturer/model (48..55), attributes (56..63),
        // rendering intent (64..67) — all left at zero.
        // PCS illuminant — D50 (X, Y, Z) at offsets 68..79.
        // s15Fixed16Number representation : X=0.9642 Y=1.0000 Z=0.8249.
        writeS15Fixed16BE(out, 68, 0.96420f)
        writeS15Fixed16BE(out, 72, 1.00000f)
        writeS15Fixed16BE(out, 76, 0.82491f)
        // Profile creator signature (80..83) and id (84..99),
        // reserved (100..127) — all zero.
        return out
    }

    /** ICC v4 header length, in bytes. Matches upstream's `kICCHeaderSize`. */
    public const val HEADER_SIZE: Int = 128

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
        val scaled = (f * 65536.0f).toLong()
        writeUInt32BE(dst, off, scaled and 0xFFFFFFFFL)
    }
}
