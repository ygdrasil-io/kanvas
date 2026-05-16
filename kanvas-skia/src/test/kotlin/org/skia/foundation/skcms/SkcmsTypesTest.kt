package org.skia.foundation.skcms
import org.graphiks.math.SkcmsMatrix3x4

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase F1 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the ICC data
 * types (SkcmsSignature, SkcmsCICP, SkcmsCurve sealed hierarchy,
 * SkcmsMatrix3x4, SkcmsICCProfile defaults).
 */
class SkcmsTypesTest {

    @Test
    fun `SkcmsSignature values match ASCII big-endian encoding`() {
        // 'RGB ' as big-endian uint32: 0x52, 0x47, 0x42, 0x20
        assertEquals(0x52474220, SkcmsSignature.RGB.value)
        assertEquals(0x58595A20, SkcmsSignature.XYZ.value)
        assertEquals(0x434D594B, SkcmsSignature.CMYK.value)
        assertEquals(0x47524159, SkcmsSignature.Gray.value)
        assertEquals(0x4C616220, SkcmsSignature.Lab.value)
    }

    @Test
    fun `SkcmsSignature_fromValue resolves known signatures`() {
        assertEquals(SkcmsSignature.RGB, SkcmsSignature.fromValue(0x52474220))
        assertEquals(SkcmsSignature.XYZ, SkcmsSignature.fromValue(0x58595A20))
        assertNull(SkcmsSignature.fromValue(0xDEADBEEF.toInt()))
    }

    @Test
    fun `SkcmsCICP holds 4 unsigned-byte fields`() {
        val cicp = SkcmsCICP(
            colorPrimaries = 9,             // kRec2020
            transferCharacteristics = 16,   // kPQ
            matrixCoefficients = 0,
            videoFullRangeFlag = 1,
        )
        assertEquals(9, cicp.colorPrimaries)
        assertEquals(16, cicp.transferCharacteristics)
        assertEquals(0, cicp.matrixCoefficients)
        assertEquals(1, cicp.videoFullRangeFlag)
    }

    @Test
    fun `SkcmsCurve_Parametric reports tableEntries == 0`() {
        val c = SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB)
        assertEquals(0, c.tableEntries)
    }

    @Test
    fun `SkcmsCurve_Table requires exactly one of table8 or table16`() {
        val c8 = SkcmsCurve.Table(tableEntries = 256, table8 = ByteArray(256))
        assertEquals(256, c8.tableEntries)

        val c16 = SkcmsCurve.Table(tableEntries = 1024, table16 = ByteArray(2 * 1024))
        assertEquals(1024, c16.tableEntries)

        // Both null is rejected.
        try {
            SkcmsCurve.Table(tableEntries = 256)
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}

        // Both set is rejected.
        try {
            SkcmsCurve.Table(tableEntries = 256, table8 = ByteArray(1), table16 = ByteArray(1))
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}

        // Zero entries is rejected.
        try {
            SkcmsCurve.Table(tableEntries = 0, table8 = ByteArray(0))
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `SkcmsCurve_Table equality is content-based`() {
        val a = SkcmsCurve.Table(tableEntries = 4, table8 = byteArrayOf(0, 64, 127, -1))
        val b = SkcmsCurve.Table(tableEntries = 4, table8 = byteArrayOf(0, 64, 127, -1))
        val c = SkcmsCurve.Table(tableEntries = 4, table8 = byteArrayOf(0, 0, 0, 0))
        assertTrue(a == b, "matching content equals")
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c, "different content not equal")
    }

    @Test
    fun `SkcmsMatrix3x4 cell-by-cell equals`() {
        val a = SkcmsMatrix3x4(arrayOf(
            floatArrayOf(1f, 2f, 3f, 4f),
            floatArrayOf(5f, 6f, 7f, 8f),
            floatArrayOf(9f, 10f, 11f, 12f),
        ))
        val b = SkcmsMatrix3x4(arrayOf(
            floatArrayOf(1f, 2f, 3f, 4f),
            floatArrayOf(5f, 6f, 7f, 8f),
            floatArrayOf(9f, 10f, 11f, 12f),
        ))
        val c = SkcmsMatrix3x4(arrayOf(
            floatArrayOf(0f, 2f, 3f, 4f),
            floatArrayOf(5f, 6f, 7f, 8f),
            floatArrayOf(9f, 10f, 11f, 12f),
        ))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a != c)

        // Wrong dimensions rejected
        try {
            SkcmsMatrix3x4(arrayOf(floatArrayOf(1f, 2f, 3f)))
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `SkcmsICCProfile default has all has-flags false`() {
        val p = SkcmsICCProfile()
        assertEquals(false, p.hasTrc)
        assertEquals(false, p.hasToXYZD50)
        assertEquals(false, p.hasA2B)
        assertEquals(false, p.hasB2A)
        assertEquals(false, p.hasCICP)
        assertEquals(0, p.size)
        assertEquals(0, p.tagCount)
    }
}
