package org.graphiks.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the iso-aligned `SkScalar` helpers.
 * Pure unit tests — no rasteriser, no canvas. Each block exercises one
 * upstream Skia primitive (constants / predicates / snap / sign / interp).
 */
class SkScalarTest {

    @Test
    fun `constants match Skia values`() {
        assertEquals(1.0f, SK_Scalar1)
        assertEquals(0.5f, SK_ScalarHalf)
        assertEquals(1.41421356f, SK_ScalarSqrt2)
        assertEquals(0.414213562f, SK_ScalarTanPIOver8)
        assertEquals(0.707106781f, SK_ScalarRoot2Over2)
        assertEquals(3.402823466e+38f, SK_ScalarMax)
        assertEquals(-SK_ScalarMax, SK_ScalarMin)
        assertEquals(Float.POSITIVE_INFINITY, SK_ScalarInfinity)
        assertEquals(Float.NEGATIVE_INFINITY, SK_ScalarNegativeInfinity)
        assertTrue(SK_ScalarNaN.isNaN())
    }

    @Test
    fun `nearly-zero constants match Skia exactly`() {
        // SK_ScalarNearlyZero = 1f / (1 << 12) = 1/4096
        assertEquals(1.0f / 4096f, SK_ScalarNearlyZero)
        // SK_ScalarSinCosNearlyZero = 1f / (1 << 16) = 1/65536
        assertEquals(1.0f / 65536f, SK_ScalarSinCosNearlyZero)
    }

    @Test
    fun `SkScalarNearlyZero accepts default tolerance`() {
        assertTrue(SkScalarNearlyZero(0f))
        assertTrue(SkScalarNearlyZero(SK_ScalarNearlyZero))     // boundary inclusive
        assertTrue(SkScalarNearlyZero(-SK_ScalarNearlyZero))
        assertFalse(SkScalarNearlyZero(SK_ScalarNearlyZero * 2f))
    }

    @Test
    fun `SkScalarNearlyZero respects custom tolerance`() {
        assertTrue(SkScalarNearlyZero(1e-7f, 1e-6f))
        assertFalse(SkScalarNearlyZero(1e-5f, 1e-6f))
    }

    @Test
    fun `SkScalarNearlyEqual default tolerance`() {
        assertTrue(SkScalarNearlyEqual(1f, 1f))
        assertTrue(SkScalarNearlyEqual(1f, 1f + SK_ScalarNearlyZero))
        assertFalse(SkScalarNearlyEqual(1f, 1f + SK_ScalarNearlyZero * 2f))
    }

    @Test
    fun `SkScalarSinSnapToZero snaps cardinal angles`() {
        // sin(0) = 0 trivially, sin(PI) ≈ 1.22e-16 → snaps, sin(PI/2) = 1 stays.
        assertEquals(0f, SkScalarSinSnapToZero(0f))
        assertEquals(0f, SkScalarSinSnapToZero(SK_ScalarPI))         // sin(π) ~ 0
        assertEquals(1f, SkScalarSinSnapToZero(SK_ScalarPIOver2))    // sin(π/2) = 1
    }

    @Test
    fun `SkScalarCosSnapToZero snaps cardinal angles`() {
        assertEquals(1f, SkScalarCosSnapToZero(0f))
        assertEquals(0f, SkScalarCosSnapToZero(SK_ScalarPIOver2))    // cos(π/2) ~ 0
        assertEquals(-1f, SkScalarCosSnapToZero(SK_ScalarPI))        // cos(π) = -1
    }

    @Test
    fun `SkScalarInterp endpoints and midpoint`() {
        assertEquals(2f, SkScalarInterp(2f, 8f, 0f))
        assertEquals(8f, SkScalarInterp(2f, 8f, 1f))
        assertEquals(5f, SkScalarInterp(2f, 8f, 0.5f))
    }

    @Test
    fun `SkScalarFraction returns x minus trunc`() {
        assertEquals(0.25f, SkScalarFraction(3.25f), 1e-6f)
        assertEquals(-0.25f, SkScalarFraction(-3.25f), 1e-6f)
        assertEquals(0f, SkScalarFraction(7f))
    }

    @Test
    fun `SkScalarSquare`() {
        assertEquals(9f, SkScalarSquare(3f))
        assertEquals(9f, SkScalarSquare(-3f))
        assertEquals(0f, SkScalarSquare(0f))
    }

    @Test
    fun `SkScalarInvert and Half`() {
        assertEquals(0.25f, SkScalarInvert(4f))
        assertEquals(2f, SkScalarHalf(4f))
    }

    @Test
    fun `SkScalarAve midpoint`() {
        assertEquals(5f, SkScalarAve(2f, 8f))
        assertEquals(0f, SkScalarAve(-3f, 3f))
    }

    @Test
    fun `SkDegreesToRadians and SkRadiansToDegrees round-trip`() {
        for (deg in floatArrayOf(0f, 30f, 90f, 180f, -45f, 720f)) {
            val round = SkRadiansToDegrees(SkDegreesToRadians(deg))
            assertTrue(SkScalarNearlyEqual(deg, round), "round-trip failed for $deg")
        }
        // Specific values
        assertEquals(SK_ScalarPIOver2, SkDegreesToRadians(90f), 1e-6f)
    }

    @Test
    fun `SkScalarIsInt`() {
        assertTrue(SkScalarIsInt(0f))
        assertTrue(SkScalarIsInt(7f))
        assertTrue(SkScalarIsInt(-3f))
        assertFalse(SkScalarIsInt(0.5f))
        assertFalse(SkScalarIsInt(-1.25f))
    }

    @Test
    fun `SkScalarSignAsInt and SignAsScalar`() {
        assertEquals(-1, SkScalarSignAsInt(-7f))
        assertEquals(0, SkScalarSignAsInt(0f))
        assertEquals(1, SkScalarSignAsInt(42f))
        assertEquals(-1f, SkScalarSignAsScalar(-7f))
        assertEquals(0f, SkScalarSignAsScalar(0f))
        assertEquals(1f, SkScalarSignAsScalar(42f))
    }

    @Test
    fun `SkScalarCopySign`() {
        assertEquals(3f, SkScalarCopySign(3f, 5f))
        assertEquals(-3f, SkScalarCopySign(3f, -5f))
        assertEquals(3f, SkScalarCopySign(-3f, 5f))
    }

    @Test
    fun `SkScalarMod`() {
        assertEquals(1f, SkScalarMod(7f, 3f), 1e-6f)
        assertEquals(0f, SkScalarMod(6f, 3f))
    }

    @Test
    fun `SkScalarPow Exp Log Log2`() {
        assertEquals(8f, SkScalarPow(2f, 3f), 1e-5f)
        assertEquals(1f, SkScalarExp(0f), 1e-6f)
        assertEquals(0f, SkScalarLog(1f), 1e-6f)
        assertEquals(3f, SkScalarLog2(8f), 1e-6f)
    }

    @Test
    fun `SkScalarTruncToScalar and TruncToInt`() {
        assertEquals(3f, SkScalarTruncToScalar(3.7f))
        assertEquals(-3f, SkScalarTruncToScalar(-3.7f))
        assertEquals(3, SkScalarTruncToInt(3.7f))
        assertEquals(-3, SkScalarTruncToInt(-3.7f))
    }

    @Test
    fun `SkDoubleToScalar narrows to float`() {
        assertEquals(0.1f, SkDoubleToScalar(0.1))
        // Round-trip via toFloat
        assertEquals(SK_ScalarPI, SkDoubleToScalar(SK_ScalarPI.toDouble()))
    }

    @Test
    fun `SkScalarsEqual element-wise`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f, 3f)
        val c = floatArrayOf(1f, 2f, 4f)
        assertTrue(SkScalarsEqual(a, b, 3))
        assertFalse(SkScalarsEqual(a, c, 3))
        // Prefix-only comparison.
        assertTrue(SkScalarsEqual(a, c, 2))
    }
}
