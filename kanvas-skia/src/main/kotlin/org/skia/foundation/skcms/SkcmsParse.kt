@file:JvmName("SkcmsParse")

package org.skia.foundation.skcms
import org.skia.math.SkcmsTransferFunction
import org.skia.math.SkcmsMatrix3x3

import kotlin.math.abs

/**
 * Bit-compatible port of `skcms_Parse` and helpers
 * ([modules/skcms/skcms.cc:379-1508](file:///Users/chaos/workspace/kanvas-forge/skia-main/modules/skcms/skcms.cc)).
 *
 * Phase F2 of MIGRATION_PLAN_COLORSPACE_PORT.md. Scope: ICC v2/v4 header
 * + tag table + 'rXYZ'/'gXYZ'/'bXYZ' (XYZ type) + 'rTRC'/'gTRC'/'bTRC'
 * (parametric or curv types) + 'cicp' tag. A2B / B2A LUTs are deferred
 * to Phase F4.
 */

private const val HEADER_SIZE: Int = 132 // 128-byte ICC header + 4-byte tag count
private const val TAG_ENTRY_SIZE: Int = 12 // signature(4) + offset(4) + size(4)

// -----------------------------------------------------------------------
// Big-endian readers (ICC bytes are big-endian; JVM Float bit layout is
// platform-native, so we byte-swap explicitly).
// -----------------------------------------------------------------------

internal fun readBigU16(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

internal fun readBigU32(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 24) or
        ((b[off + 1].toInt() and 0xFF) shl 16) or
        ((b[off + 2].toInt() and 0xFF) shl 8) or
        (b[off + 3].toInt() and 0xFF)

/** Read a big-endian s15.16 fixed-point value as a float. */
internal fun readBigFixed(b: ByteArray, off: Int): Float =
    readBigU32(b, off).toFloat() / 65536f

// -----------------------------------------------------------------------
// Tag table
// -----------------------------------------------------------------------

/**
 * Walk the tag table looking for [sig]. Returns the [SkcmsICCTag] with
 * resolved [type] (the first 4 bytes at the tag offset), or `null` if
 * the tag isn't present or its declared range is out of buffer bounds.
 */
internal fun getTagBySignature(
    buffer: ByteArray, profileSize: Int, tagCount: Int, sig: Int,
): SkcmsICCTag? {
    for (i in 0 until tagCount) {
        val entry = HEADER_SIZE + i * TAG_ENTRY_SIZE
        if (entry + TAG_ENTRY_SIZE > buffer.size) return null
        val tagSig = readBigU32(buffer, entry)
        if (tagSig != sig) continue
        val tagOff = readBigU32(buffer, entry + 4)
        val tagSize = readBigU32(buffer, entry + 8)
        if (tagSize < 4 || tagOff < 0 || tagOff.toLong() + tagSize.toLong() > profileSize) {
            return null
        }
        val type = readBigU32(buffer, tagOff)
        return SkcmsICCTag(sig, type, tagOff, tagSize)
    }
    return null
}

// -----------------------------------------------------------------------
// XYZ-type tag reader
//   layout: type(4) reserved(4) X(4) Y(4) Z(4)  -- 20 bytes minimum
// -----------------------------------------------------------------------

private fun readXyzTag(buffer: ByteArray, tag: SkcmsICCTag): FloatArray? {
    if (tag.type != SkcmsSignature.XYZ.value || tag.size < 20) return null
    return floatArrayOf(
        readBigFixed(buffer, tag.offset + 8),
        readBigFixed(buffer, tag.offset + 12),
        readBigFixed(buffer, tag.offset + 16),
    )
}

private fun readToXYZD50(
    buffer: ByteArray, rXYZ: SkcmsICCTag, gXYZ: SkcmsICCTag, bXYZ: SkcmsICCTag,
): SkcmsMatrix3x3? {
    val r = readXyzTag(buffer, rXYZ) ?: return null
    val g = readXyzTag(buffer, gXYZ) ?: return null
    val b = readXyzTag(buffer, bXYZ) ?: return null
    // Columns of the 3x3 matrix: r is column 0, g is column 1, b is column 2.
    return SkcmsMatrix3x3.of(
        r[0], g[0], b[0],
        r[1], g[1], b[1],
        r[2], g[2], b[2],
    )
}

// -----------------------------------------------------------------------
// Curve readers — para and curv variants. Mirrors
// `read_curve_para` (skcms.cc:577-644) and `read_curve_curv` (:653-692).
// -----------------------------------------------------------------------

private const val PARA_HEADER_SIZE: Int = 12 // type(4) reserved_a(4) function_type(2) reserved_b(2)

/**
 * Decoded sizes for each `function_type`: kG=4, kGAB=12, kGABC=16,
 * kGABCD=20, kGABCDEF=28 bytes of `variable` payload after the header.
 */
private val PARA_VAR_SIZE: IntArray = intArrayOf(4, 12, 16, 20, 28)

private fun readCurvePara(buffer: ByteArray, tagOff: Int, tagSize: Int): SkcmsCurve.Parametric? {
    if (tagSize < PARA_HEADER_SIZE) return null
    val functionType = readBigU16(buffer, tagOff + 8)
    if (functionType > 4) return null
    if (tagSize < PARA_HEADER_SIZE + PARA_VAR_SIZE[functionType]) return null

    val varOff = tagOff + PARA_HEADER_SIZE
    var g = readBigFixed(buffer, varOff)
    var a = 1f
    var b = 0f
    var c = 0f
    var d = 0f
    var e = 0f
    var f = 0f

    when (functionType) {
        1 -> { // kGAB: y = (a*x + b)^g, both branches
            a = readBigFixed(buffer, varOff + 4)
            b = readBigFixed(buffer, varOff + 8)
            if (a == 0f) return null
            d = -b / a
        }
        2 -> { // kGABC: y = (a*x + b)^g + e, both branches
            a = readBigFixed(buffer, varOff + 4)
            b = readBigFixed(buffer, varOff + 8)
            e = readBigFixed(buffer, varOff + 12)
            if (a == 0f) return null
            d = -b / a
            f = e
        }
        3 -> { // kGABCD: piecewise (a*x+b)^g vs c*x
            a = readBigFixed(buffer, varOff + 4)
            b = readBigFixed(buffer, varOff + 8)
            c = readBigFixed(buffer, varOff + 12)
            d = readBigFixed(buffer, varOff + 16)
        }
        4 -> { // kGABCDEF: full 7-param
            a = readBigFixed(buffer, varOff + 4)
            b = readBigFixed(buffer, varOff + 8)
            c = readBigFixed(buffer, varOff + 12)
            d = readBigFixed(buffer, varOff + 16)
            e = readBigFixed(buffer, varOff + 20)
            f = readBigFixed(buffer, varOff + 24)
        }
        // 0 → kG: y = x^g (g already read)
    }

    val tf = SkcmsTransferFunction(g, a, b, c, d, e, f)
    return if (classify(tf) == SkcmsTFType.sRGBish) SkcmsCurve.Parametric(tf) else null
}

private const val CURV_HEADER_SIZE: Int = 12 // type(4) reserved(4) value_count(4)

private fun readCurveCurv(buffer: ByteArray, tagOff: Int, tagSize: Int): SkcmsCurve? {
    if (tagSize < CURV_HEADER_SIZE) return null
    val valueCount = readBigU32(buffer, tagOff + 8)
    if (valueCount < 0 || tagSize.toLong() < CURV_HEADER_SIZE + 2L * valueCount) return null

    return when (valueCount) {
        0 -> {
            // Empty table: identity curve.
            SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        }
        1 -> {
            // Single-entry table: simple gamma. The 16-bit value is read
            // and divided by 256 to recover the s8.8 fixed-point gamma.
            val gamma = readBigU16(buffer, tagOff + 12).toFloat() / 256f
            SkcmsCurve.Parametric(
                SkcmsTransferFunction(g = gamma, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f),
            )
        }
        else -> {
            // Multi-entry LUT. We slice the big-endian uint16 payload out
            // of the buffer; Phase F3 handles byte-swapping at eval time.
            val table = buffer.copyOfRange(tagOff + 12, tagOff + 12 + 2 * valueCount)
            SkcmsCurve.Table(tableEntries = valueCount, table16 = table)
        }
    }
}

private fun readCurve(buffer: ByteArray, tagOff: Int, tagSize: Int): SkcmsCurve? {
    if (tagSize < 4) return null
    return when (val type = readBigU32(buffer, tagOff)) {
        SkcmsTagSignature.para -> readCurvePara(buffer, tagOff, tagSize)
        SkcmsTagSignature.curv -> readCurveCurv(buffer, tagOff, tagSize)
        else -> {
            @Suppress("UNUSED_VARIABLE")
            val unused = type
            null
        }
    }
}

// -----------------------------------------------------------------------
// CICP tag reader — 4 trailing bytes after type(4)+reserved(4).
// -----------------------------------------------------------------------

private fun readCicp(buffer: ByteArray, tag: SkcmsICCTag): SkcmsCICP? {
    if (tag.type != SkcmsTagSignature.CICP || tag.size < 12) return null
    val base = tag.offset + 8
    return SkcmsCICP(
        colorPrimaries = buffer[base].toInt() and 0xFF,
        transferCharacteristics = buffer[base + 1].toInt() and 0xFF,
        matrixCoefficients = buffer[base + 2].toInt() and 0xFF,
        videoFullRangeFlag = buffer[base + 3].toInt() and 0xFF,
    )
}

// -----------------------------------------------------------------------
// skcmsParse — public entry point
// -----------------------------------------------------------------------

/**
 * Parse an ICC profile from [buffer]. Returns `null` if the buffer is
 * malformed or describes an unsupported profile (e.g. PCS != XYZ/Lab,
 * version > 4, illuminant not ~D50, RGB profile without a usable TRC or
 * gamut). Mirrors `skcms_Parse` upstream (skcms.cc:1359-1508), restricted
 * to the subset needed for SDR RGB profiles. A2B/B2A LUTs are skipped
 * (they will be handled by Phase F4).
 *
 * The caller retains ownership of [buffer]; the returned profile holds a
 * reference to it for tag-table re-traversal at evaluation time.
 */
public fun skcmsParse(buffer: ByteArray): SkcmsICCProfile? {
    if (buffer.size < HEADER_SIZE) return null

    // Header
    val size = readBigU32(buffer, 0)
    val version = readBigU32(buffer, 8)
    val dataColorSpace = readBigU32(buffer, 16)
    val pcs = readBigU32(buffer, 20)
    val signature = readBigU32(buffer, 36)
    val illuminantX = readBigFixed(buffer, 68)
    val illuminantY = readBigFixed(buffer, 72)
    val illuminantZ = readBigFixed(buffer, 76)
    val tagCount = readBigU32(buffer, 128)

    // Validation
    if (signature != SkcmsTagSignature.acsp) return null
    if (size > buffer.size) return null
    val tagTableSize = tagCount.toLong() * TAG_ENTRY_SIZE
    if (size.toLong() < HEADER_SIZE + tagTableSize) return null
    if ((version ushr 24) > 4) return null

    // Illuminant must be D50 (0.9642, 1.0000, 0.8249) within 0.01.
    if (abs(illuminantX - 0.9642f) > 0.01f ||
        abs(illuminantY - 1.0000f) > 0.01f ||
        abs(illuminantZ - 0.8249f) > 0.01f) return null

    // Pre-validate tag entries.
    for (i in 0 until tagCount) {
        val entry = HEADER_SIZE + i * TAG_ENTRY_SIZE
        if (entry + TAG_ENTRY_SIZE > buffer.size) return null
        val tagOff = readBigU32(buffer, entry + 4)
        val tagSize = readBigU32(buffer, entry + 8)
        if (tagSize < 4 || tagOff.toLong() + tagSize.toLong() > size) return null
    }

    // Only XYZ or Lab PCS supported (we only consume XYZ today).
    if (pcs != SkcmsSignature.XYZ.value && pcs != SkcmsSignature.Lab.value) return null
    val pcsIsXyz = pcs == SkcmsSignature.XYZ.value

    val trc = arrayOfNulls<SkcmsCurve>(3)
    var hasTrc = false
    var toXYZD50: SkcmsMatrix3x3 = SkcmsMatrix3x3.IDENTITY
    var hasToXYZD50 = false

    // Gray profile uses kTRC for all three channels.
    if (dataColorSpace == SkcmsSignature.Gray.value) {
        val kTrc = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.kTRC)
        if (kTrc != null) {
            val curve = readCurve(buffer, kTrc.offset, kTrc.size) ?: return null
            trc[0] = curve; trc[1] = curve; trc[2] = curve
            hasTrc = true
            if (pcsIsXyz) {
                toXYZD50 = SkcmsMatrix3x3.of(
                    illuminantX, 0f, 0f,
                    0f, illuminantY, 0f,
                    0f, 0f, illuminantZ,
                )
                hasToXYZD50 = true
            }
        }
    } else {
        val rTrc = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.rTRC)
        val gTrc = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.gTRC)
        val bTrc = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.bTRC)
        if (rTrc != null && gTrc != null && bTrc != null) {
            trc[0] = readCurve(buffer, rTrc.offset, rTrc.size) ?: return null
            trc[1] = readCurve(buffer, gTrc.offset, gTrc.size) ?: return null
            trc[2] = readCurve(buffer, bTrc.offset, bTrc.size) ?: return null
            hasTrc = true
        }

        val rXyz = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.rXYZ)
        val gXyz = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.gXYZ)
        val bXyz = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.bXYZ)
        if (rXyz != null && gXyz != null && bXyz != null) {
            toXYZD50 = readToXYZD50(buffer, rXyz, gXyz, bXyz) ?: return null
            hasToXYZD50 = true
        }
    }

    // CICP tag
    var cicp = SkcmsCICP(0, 0, 0, 0)
    var hasCICP = false
    val cicpTag = getTagBySignature(buffer, size, tagCount, SkcmsTagSignature.CICP)
    if (cicpTag != null) {
        cicp = readCicp(buffer, cicpTag) ?: return null
        hasCICP = true
    }

    // Profile is "usable as src" if it has a TRC + matrix or A2B (deferred).
    if (!hasTrc || !hasToXYZD50) return null

    return SkcmsICCProfile(
        buffer = buffer,
        size = size,
        dataColorSpace = dataColorSpace,
        pcs = pcs,
        tagCount = tagCount,
        trc = trc,
        toXYZD50 = toXYZD50,
        cicp = cicp,
        hasTrc = hasTrc,
        hasToXYZD50 = hasToXYZD50,
        hasCICP = hasCICP,
    )
}
