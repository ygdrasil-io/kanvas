package org.skia.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coverage for the Phase 4b [SkMatrix] data class — affine 2 × 3 matrix
 * operations: factories, point/rect mapping, pre-* composition,
 * `computeMaxScale`, `isAxisAligned`.
 */
class SkMatrixTest {

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") {
        assertTrue(kotlin.math.abs(expected - actual) <= eps,
            "$msg expected ≈ $expected, got $actual (Δ=${actual - expected})")
    }

    @Test
    fun `Identity is identity`() {
        val m = SkMatrix.Identity
        assertTrue(m.isIdentity)
        assertTrue(m.isAxisAligned)
        val (x, y) = m.mapXY(7f, 11f)
        assertEquals(7f, x); assertEquals(11f, y)
    }

    @Test
    fun `MakeTrans translates points`() {
        val m = SkMatrix.MakeTrans(3f, 5f)
        assertFalse(m.isIdentity)
        assertTrue(m.isAxisAligned)
        val (x, y) = m.mapXY(1f, 2f)
        assertEquals(4f, x); assertEquals(7f, y)
    }

    @Test
    fun `MakeScale scales points`() {
        val m = SkMatrix.MakeScale(2f, 3f)
        val (x, y) = m.mapXY(4f, 5f)
        assertEquals(8f, x); assertEquals(15f, y)
    }

    @Test
    fun `MakeRotate(90) maps (1,0) to (0,1)`() {
        // Skia's positive rotation is clockwise in screen-space (y-down),
        // which means (1, 0) rotates to (cos90, sin90) = (0, 1).
        val m = SkMatrix.MakeRotate(90f)
        val (x, y) = m.mapXY(1f, 0f)
        assertNear(0f, x); assertNear(1f, y)
        // And (0, 1) goes to (-1, 0).
        val (x2, y2) = m.mapXY(0f, 1f)
        assertNear(-1f, x2); assertNear(0f, y2)
        assertFalse(m.isAxisAligned)
    }

    @Test
    fun `MakeRotate(deg, px, py) rotates around the pivot`() {
        // Rotate (3, 0) by 90° around (1, 0) should give (1, 2).
        val m = SkMatrix.MakeRotate(90f, 1f, 0f)
        val (x, y) = m.mapXY(3f, 0f)
        assertNear(1f, x); assertNear(2f, y)
    }

    @Test
    fun `MakeSkew skews points`() {
        // skew x by 0.5 means: x' = x + 0.5 * y. y' = y.
        val m = SkMatrix.MakeSkew(0.5f, 0f)
        val (x, y) = m.mapXY(1f, 4f)
        assertEquals(3f, x)         // 1 + 0.5 * 4 = 3
        assertEquals(4f, y)
        assertFalse(m.isAxisAligned)
    }

    @Test
    fun `concat applies right matrix first`() {
        // M = T(10, 0) * S(2, 2). A point (1, 2) goes through S first
        // (→ (2, 4)), then T (→ (12, 4)).
        val m = SkMatrix.concat(SkMatrix.MakeTrans(10f, 0f), SkMatrix.MakeScale(2f, 2f))
        val (x, y) = m.mapXY(1f, 2f)
        assertEquals(12f, x); assertEquals(4f, y)
    }

    @Test
    fun `preTranslate matches concat-with-Translate`() {
        val base = SkMatrix.MakeScale(3f, 5f)
        val viaPre = base.preTranslate(7f, 11f)
        val viaConcat = SkMatrix.concat(base, SkMatrix.MakeTrans(7f, 11f))
        // Both transforms applied to a point should match within sub-pixel.
        val (a, b) = viaPre.mapXY(2f, 3f)
        val (c, d) = viaConcat.mapXY(2f, 3f)
        assertEquals(c, a, 1e-5f); assertEquals(d, b, 1e-5f)
    }

    @Test
    fun `preScale composes scales`() {
        val m = SkMatrix.MakeScale(2f, 3f).preScale(5f, 7f)
        // Effective scale: (10, 21).
        val (x, y) = m.mapXY(1f, 1f)
        assertEquals(10f, x); assertEquals(21f, y)
    }

    @Test
    fun `mapRect returns axis-aligned bbox of rotated rect`() {
        val m = SkMatrix.MakeRotate(45f)
        val r = m.mapRect(SkRect.MakeLTRB(0f, 0f, 1f, 1f))
        // 1x1 unit square rotated 45° has bbox spanning [-sin45, 0] in X
        // (the (0,1) corner goes to (-sin45, cos45) ≈ (-.707, .707)) and
        // [0, sin45 + cos45] ≈ [0, sqrt(2)] in Y.
        val s = (sin(PI / 4)).toFloat()
        assertNear(-s, r.left)
        assertNear(0f, r.top)
        assertNear(s, r.right)
        assertNear(s + cos(PI / 4).toFloat(), r.bottom)
    }

    @Test
    fun `computeMaxScale of pure scale returns max abs scale`() {
        assertNear(2f, SkMatrix.MakeScale(2f, 1f).computeMaxScale())
        assertNear(3f, SkMatrix.MakeScale(1f, 3f).computeMaxScale())
        assertNear(2f, SkMatrix.MakeScale(-2f, 1f).computeMaxScale())
        assertNear(5f, SkMatrix.MakeScale(5f, 5f).computeMaxScale())
    }

    @Test
    fun `computeMaxScale of pure rotation is 1`() {
        for (deg in listOf(0f, 30f, 45f, 90f, 180f, -135f)) {
            assertNear(1f, SkMatrix.MakeRotate(deg).computeMaxScale(),
                msg = "rotation $deg deg max scale")
        }
    }

    @Test
    fun `computeMaxScale of rotated scale equals scale`() {
        // Rotate then scale: max scale should still be the scale magnitude.
        val m = SkMatrix.MakeRotate(30f).preScale(4f, 4f)
        assertNear(4f, m.computeMaxScale())
    }

    @Test
    fun `computeMaxScale of identity is 1`() {
        assertNear(1f, SkMatrix.Identity.computeMaxScale())
    }

    @Test
    fun `computeMaxScale never returns NaN for typical inputs`() {
        // Worst case: extreme aspect ratio + skew.
        val m = SkMatrix(sx = 1000f, kx = 0.5f, tx = 0f, ky = 1f, sy = 0.001f, ty = 0f)
        val s = m.computeMaxScale()
        assertTrue(!s.isNaN() && s > 0f, "got NaN/non-positive scale=$s")
        // Should be at least max(|sx|, |sy|) = 1000.
        assertTrue(s >= 1000f, "expected ≥ 1000, got $s")
    }

    @Test
    fun `concat with Identity is identity`() {
        val m = SkMatrix.MakeScale(3f, 5f).preTranslate(7f, 11f)
        assertEquals(m, SkMatrix.concat(m, SkMatrix.Identity))
        assertEquals(m, SkMatrix.concat(SkMatrix.Identity, m))
    }

    @Test
    fun `isAxisAligned distinguishes pure scale from rotation`() {
        assertTrue(SkMatrix.Identity.isAxisAligned)
        assertTrue(SkMatrix.MakeTrans(5f, 7f).isAxisAligned)
        assertTrue(SkMatrix.MakeScale(2f, 3f).isAxisAligned)
        assertFalse(SkMatrix.MakeRotate(45f).isAxisAligned)
        assertFalse(SkMatrix.MakeSkew(0.5f, 0f).isAxisAligned)
    }

    @Test
    fun `MakeAll constructs raw matrix`() {
        val m = SkMatrix.MakeAll(2f, 0f, 10f, 0f, 3f, 20f)
        val (x, y) = m.mapXY(1f, 1f)
        assertEquals(12f, x)   // 2*1 + 0*1 + 10
        assertEquals(23f, y)   // 0*1 + 3*1 + 20
    }

    @Test
    fun `mapXY and mapRect agree for axis-aligned matrices`() {
        val m = SkMatrix.MakeScale(2f, 3f).preTranslate(1f, 1f)
        val r = m.mapRect(SkRect.MakeLTRB(0f, 0f, 1f, 1f))
        val (lx, ly) = m.mapXY(0f, 0f)
        val (rx, ry) = m.mapXY(1f, 1f)
        // (0,0) → (2*0 + 2*1 = 2, 3*0 + 3*1 = 3) — wait we did preTranslate
        // so the point (0,0) is first translated to (1,1) then scaled to (2,3).
        assertEquals(minOf(lx, rx), r.left)
        assertEquals(minOf(ly, ry), r.top)
        assertEquals(maxOf(lx, rx), r.right)
        assertEquals(maxOf(ly, ry), r.bottom)
    }

    @Test
    fun `singular value matches sqrt(eigenvalue) sanity check`() {
        // For pure horizontal stretch + 90° rotation: maps (1,0) to (0, k).
        // Singular values should be {k, 1}. computeMaxScale returns k.
        val k = 7f
        val m = SkMatrix.MakeScale(k, 1f).preRotate(90f)
        assertNear(k, m.computeMaxScale(), eps = 1e-3f)
    }

    @Test
    fun `negative scale matches Skia behaviour`() {
        val m = SkMatrix.MakeScale(1f, -1f)
        val (x, y) = m.mapXY(3f, 4f)
        assertEquals(3f, x)
        assertEquals(-4f, y)
        assertTrue(m.isAxisAligned)
        assertNear(1f, m.computeMaxScale(), msg = "negative-uniform-flip max scale")
    }

    @Test
    fun `singular values are positive`() {
        // Random affine matrix should have positive max scale.
        val m = SkMatrix(sx = 1.5f, kx = 0.7f, tx = 100f, ky = -0.3f, sy = 2f, ty = -50f)
        val s = m.computeMaxScale()
        assertTrue(s > 0f, "max scale must be positive (got $s)")
        // Sanity: should be at least norm of first row.
        val rowNorm = sqrt(1.5f * 1.5f + 0.7f * 0.7f)
        assertTrue(s >= rowNorm * 0.9f, "max scale $s < first-row norm $rowNorm")
    }
}
