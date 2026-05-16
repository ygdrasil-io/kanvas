package org.skia.pathops.internal


import org.graphiks.math.AlmostBequalUlps
import org.graphiks.math.AlmostEqualUlps
import org.graphiks.math.DBL_EPSILON
import org.graphiks.math.DBL_EPSILON_ERR
import org.graphiks.math.FLT_EPSILON
import org.graphiks.math.RoughlyEqualUlps
import org.graphiks.math.SkDInterp
import org.graphiks.math.SkDSideBit
import org.graphiks.math.SkDSign
import org.graphiks.math.SkPinT
import org.graphiks.math.UlpsDistance
import org.graphiks.math.approximately_between
import org.graphiks.math.approximately_equal
import org.graphiks.math.approximately_zero
import org.graphiks.math.between
import org.graphiks.math.precisely_zero
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the ULPs comparators + approximate predicates ported
 * from `src/pathops/SkPathOpsTypes.{h,cpp}` (Phase D1.1.a).
 *
 * Coverage focuses on the boundary semantics : exact equality,
 * 1-ULP shift, FLT_EPSILON shift, and the asymmetric edges of the
 * "between" / "negative" / "positive" predicates.
 */
class SkPathOpsTypesTest {

    // ─── ULPs equality ───────────────────────────────────────────────

    @Test
    fun `AlmostEqualUlps is reflexive on simple values`() {
        assertTrue(AlmostEqualUlps(1.0f, 1.0f))
        assertTrue(AlmostEqualUlps(0.0f, 0.0f))
        assertTrue(AlmostEqualUlps(-1.0f, -1.0f))
        assertTrue(AlmostEqualUlps(1e6f, 1e6f))
    }

    @Test
    fun `AlmostEqualUlps tolerates 1-ULP shift but not 100-ULP shift`() {
        val a = 1.0f
        val b = Float.fromBits(a.toRawBits() + 1) // a + 1 ULP
        val far = Float.fromBits(a.toRawBits() + 100) // a + 100 ULPs
        assertTrue(AlmostEqualUlps(a, b))
        assertFalse(AlmostEqualUlps(a, far))
    }

    @Test
    fun `AlmostEqualUlps treats denormalized inputs as equal`() {
        assertTrue(AlmostEqualUlps(0.0f, FLT_EPSILON.toFloat() / 8))
        assertTrue(AlmostEqualUlps(-FLT_EPSILON.toFloat() / 8, FLT_EPSILON.toFloat() / 8))
    }

    @Test
    fun `AlmostBequalUlps is stricter (2 ULPs) than AlmostEqualUlps (16 ULPs)`() {
        val a = 1.0f
        val far = Float.fromBits(a.toRawBits() + 4) // 4 ULPs : within Almost (16) but outside Bequal (2)
        assertTrue(AlmostEqualUlps(a, far))
        assertFalse(AlmostBequalUlps(a, far))
    }

    @Test
    fun `RoughlyEqualUlps is looser (256 ULPs)`() {
        val a = 1.0f
        val rough = Float.fromBits(a.toRawBits() + 200)
        assertFalse(AlmostEqualUlps(a, rough))
        assertTrue(RoughlyEqualUlps(a, rough))
    }

    @Test
    fun `UlpsDistance returns positive distance`() {
        val a = 1.0f
        val b = Float.fromBits(a.toRawBits() + 5)
        assertEquals(5, UlpsDistance(a, b))
        assertEquals(5, UlpsDistance(b, a))
        assertEquals(0, UlpsDistance(a, a))
    }

    @Test
    fun `UlpsDistance returns MAX_VALUE for differently-signed values`() {
        assertEquals(Int.MAX_VALUE, UlpsDistance(-1.0f, 1.0f))
        // +0 == -0 special case.
        assertEquals(0, UlpsDistance(-0.0f, 0.0f))
    }

    // ─── Approximate predicates ──────────────────────────────────────

    @Test
    fun `approximately_zero accepts values smaller than FLT_EPSILON`() {
        assertTrue(approximately_zero(0.0))
        assertTrue(approximately_zero(FLT_EPSILON / 2))
        assertFalse(approximately_zero(FLT_EPSILON * 2))
    }

    @Test
    fun `precisely_zero is stricter (DBL_EPSILON_ERR threshold)`() {
        assertTrue(precisely_zero(DBL_EPSILON))
        assertFalse(precisely_zero(FLT_EPSILON / 2))
    }

    @Test
    fun `approximately_equal handles small differences in T-range values`() {
        assertTrue(approximately_equal(0.5, 0.5))
        assertTrue(approximately_equal(0.5, 0.5 + FLT_EPSILON / 4))
        assertFalse(approximately_equal(0.5, 0.5 + FLT_EPSILON * 8))
    }

    @Test
    fun `between is symmetric and accepts endpoints`() {
        assertTrue(between(0.0, 0.0, 1.0))
        assertTrue(between(0.0, 1.0, 1.0))
        assertTrue(between(0.0, 0.5, 1.0))
        assertTrue(between(1.0, 0.5, 0.0)) // reversed bounds also accepted
        assertFalse(between(0.0, 1.5, 1.0))
        assertFalse(between(0.0, -0.5, 1.0))
    }

    @Test
    fun `approximately_between accepts FLT_EPSILON slack outside the bounds`() {
        assertTrue(approximately_between(0.0, -FLT_EPSILON / 2, 1.0))
        assertTrue(approximately_between(0.0, 1.0 + FLT_EPSILON / 2, 1.0))
        assertFalse(approximately_between(0.0, -FLT_EPSILON * 8, 1.0))
    }

    @Test
    fun `SkPinT pins precisely-out-of-range to the bounds`() {
        assertEquals(0.0, SkPinT(-DBL_EPSILON))
        assertEquals(1.0, SkPinT(1.0 + DBL_EPSILON))
        assertEquals(0.5, SkPinT(0.5))
        // Just inside the boundary stays as-is.
        assertEquals(0.0001, SkPinT(0.0001))
        assertEquals(0.9999, SkPinT(0.9999))
    }

    @Test
    fun `SkDInterp linearly interpolates`() {
        assertEquals(5.0, SkDInterp(0.0, 10.0, 0.5))
        assertEquals(0.0, SkDInterp(0.0, 10.0, 0.0))
        assertEquals(10.0, SkDInterp(0.0, 10.0, 1.0))
        // outside [0, 1] is allowed (extrapolation)
        assertEquals(15.0, SkDInterp(0.0, 10.0, 1.5))
    }

    @Test
    fun `SkDSign returns -1, 0, 1`() {
        assertEquals(-1, SkDSign(-3.14))
        assertEquals(0, SkDSign(0.0))
        assertEquals(1, SkDSign(2.71))
    }

    @Test
    fun `SkDSideBit yields 1, 2, 4 for negative, zero, positive`() {
        assertEquals(1, SkDSideBit(-1.0))
        assertEquals(2, SkDSideBit(0.0))
        assertEquals(4, SkDSideBit(1.0))
    }
}
