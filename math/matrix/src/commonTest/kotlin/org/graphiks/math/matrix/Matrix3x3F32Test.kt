package org.graphiks.math.matrix

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32

/**
 * Coverage for the Phase 4b [Matrix3x3F32] data class — affine 2 × 3 matrix
 * operations: factories, point/rect mapping, pre-* composition,
 * `computeMaxScale`, `isAxisAligned`.
 */
class Matrix3x3F32Test {

    private fun assertNear(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") {
        assertTrue(kotlin.math.abs(expected - actual) <= eps,
            "$msg expected ≈ $expected, got $actual (Δ=${actual - expected})")
    }

    @Test
    fun `Identity is identity`() {
        val m = Matrix3x3F32.Identity
        assertTrue(m.isIdentity)
        assertTrue(m.isAxisAligned)
        val (x, y) = m.mapXY(7f, 11f)
        assertEquals(7f, x); assertEquals(11f, y)
    }

    @Test
    fun `MakeTrans translates points`() {
        val m = Matrix3x3F32.MakeTrans(3f, 5f)
        assertFalse(m.isIdentity)
        assertTrue(m.isAxisAligned)
        val (x, y) = m.mapXY(1f, 2f)
        assertEquals(4f, x); assertEquals(7f, y)
    }

    @Test
    fun `MakeScale scales points`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f)
        val (x, y) = m.mapXY(4f, 5f)
        assertEquals(8f, x); assertEquals(15f, y)
    }

    @Test
    fun `MakeRotate(90) maps (1,0) to (0,1)`() {
        // Skia's positive rotation is clockwise in screen-space (y-down),
        // which means (1, 0) rotates to (cos90, sin90) = (0, 1).
        val m = Matrix3x3F32.MakeRotate(90f)
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
        val m = Matrix3x3F32.MakeRotate(90f, 1f, 0f)
        val (x, y) = m.mapXY(3f, 0f)
        assertNear(1f, x); assertNear(2f, y)
    }

    @Test
    fun `MakeSkew skews points`() {
        // skew x by 0.5 means: x' = x + 0.5 * y. y' = y.
        val m = Matrix3x3F32.MakeSkew(0.5f, 0f)
        val (x, y) = m.mapXY(1f, 4f)
        assertEquals(3f, x)         // 1 + 0.5 * 4 = 3
        assertEquals(4f, y)
        assertFalse(m.isAxisAligned)
    }

    @Test
    fun `concat applies right matrix first`() {
        // M = T(10, 0) * S(2, 2). A point (1, 2) goes through S first
        // (→ (2, 4)), then T (→ (12, 4)).
        val m = Matrix3x3F32.concat(Matrix3x3F32.MakeTrans(10f, 0f), Matrix3x3F32.MakeScale(2f, 2f))
        val (x, y) = m.mapXY(1f, 2f)
        assertEquals(12f, x); assertEquals(4f, y)
    }

    @Test
    fun `preTranslate matches concat-with-Translate`() {
        val base = Matrix3x3F32.MakeScale(3f, 5f)
        val viaPre = base.preTranslate(7f, 11f)
        val viaConcat = Matrix3x3F32.concat(base, Matrix3x3F32.MakeTrans(7f, 11f))
        // Both transforms applied to a point should match within sub-pixel.
        val (a, b) = viaPre.mapXY(2f, 3f)
        val (c, d) = viaConcat.mapXY(2f, 3f)
        assertEquals(c, a, 1e-5f); assertEquals(d, b, 1e-5f)
    }

    @Test
    fun `preScale composes scales`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preScale(5f, 7f)
        // Effective scale: (10, 21).
        val (x, y) = m.mapXY(1f, 1f)
        assertEquals(10f, x); assertEquals(21f, y)
    }

    @Test
    fun `mapRect returns axis-aligned bbox of rotated rect`() {
        val m = Matrix3x3F32.MakeRotate(45f)
        val r = m.mapRect(RectF32.MakeLTRB(0f, 0f, 1f, 1f))
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
        assertNear(2f, Matrix3x3F32.MakeScale(2f, 1f).getMaxScale())
        assertNear(3f, Matrix3x3F32.MakeScale(1f, 3f).getMaxScale())
        assertNear(2f, Matrix3x3F32.MakeScale(-2f, 1f).getMaxScale())
        assertNear(5f, Matrix3x3F32.MakeScale(5f, 5f).getMaxScale())
    }

    @Test
    fun `computeMaxScale of pure rotation is 1`() {
        for (deg in listOf(0f, 30f, 45f, 90f, 180f, -135f)) {
            assertNear(1f, Matrix3x3F32.MakeRotate(deg).getMaxScale(),
                msg = "rotation $deg deg max scale")
        }
    }

    @Test
    fun `computeMaxScale of rotated scale equals scale`() {
        // Rotate then scale: max scale should still be the scale magnitude.
        val m = Matrix3x3F32.MakeRotate(30f).preScale(4f, 4f)
        assertNear(4f, m.getMaxScale())
    }

    @Test
    fun `computeMaxScale of identity is 1`() {
        assertNear(1f, Matrix3x3F32.Identity.getMaxScale())
    }

    @Test
    fun `computeMaxScale never returns NaN for typical inputs`() {
        // Worst case: extreme aspect ratio + skew.
        val m = Matrix3x3F32(sx = 1000f, kx = 0.5f, tx = 0f, ky = 1f, sy = 0.001f, ty = 0f)
        val s = m.getMaxScale()
        assertTrue(!s.isNaN() && s > 0f, "got NaN/non-positive scale=$s")
        // Should be at least max(|sx|, |sy|) = 1000.
        assertTrue(s >= 1000f, "expected ≥ 1000, got $s")
    }

    @Test
    fun `concat with Identity is identity`() {
        val m = Matrix3x3F32.MakeScale(3f, 5f).preTranslate(7f, 11f)
        assertEquals(m, Matrix3x3F32.concat(m, Matrix3x3F32.Identity))
        assertEquals(m, Matrix3x3F32.concat(Matrix3x3F32.Identity, m))
    }

    @Test
    fun `isAxisAligned distinguishes pure scale from rotation`() {
        assertTrue(Matrix3x3F32.Identity.isAxisAligned)
        assertTrue(Matrix3x3F32.MakeTrans(5f, 7f).isAxisAligned)
        assertTrue(Matrix3x3F32.MakeScale(2f, 3f).isAxisAligned)
        assertFalse(Matrix3x3F32.MakeRotate(45f).isAxisAligned)
        assertFalse(Matrix3x3F32.MakeSkew(0.5f, 0f).isAxisAligned)
    }

    @Test
    fun `MakeAll constructs raw matrix`() {
        val m = Matrix3x3F32.MakeAll(2f, 0f, 10f, 0f, 3f, 20f)
        val (x, y) = m.mapXY(1f, 1f)
        assertEquals(12f, x)   // 2*1 + 0*1 + 10
        assertEquals(23f, y)   // 0*1 + 3*1 + 20
    }

    @Test
    fun `mapXY and mapRect agree for axis-aligned matrices`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(1f, 1f)
        val r = m.mapRect(RectF32.MakeLTRB(0f, 0f, 1f, 1f))
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
        val m = Matrix3x3F32.MakeScale(k, 1f).preRotate(90f)
        assertNear(k, m.getMaxScale(), eps = 1e-3f)
    }

    @Test
    fun `negative scale matches Skia behaviour`() {
        val m = Matrix3x3F32.MakeScale(1f, -1f)
        val (x, y) = m.mapXY(3f, 4f)
        assertEquals(3f, x)
        assertEquals(-4f, y)
        assertTrue(m.isAxisAligned)
        assertNear(1f, m.getMaxScale(), msg = "negative-uniform-flip max scale")
    }

    @Test
    fun `pivoted MakeScale leaves pivot fixed`() {
        // Scale by (2, 3) around (1, 1) — point (1, 1) should be fixed.
        val m = Matrix3x3F32.MakeScale(2f, 3f, 1f, 1f)
        val (x, y) = m.mapXY(1f, 1f)
        assertNear(1f, x); assertNear(1f, y)
        // And (2, 1) should map to (1 + 2*1, 1) = (3, 1).
        val (x2, y2) = m.mapXY(2f, 1f)
        assertNear(3f, x2); assertNear(1f, y2)
    }

    @Test
    fun `pivoted MakeScale degenerates to identity for unit scale`() {
        val m = Matrix3x3F32.MakeScale(1f, 1f, 7f, 11f)
        assertTrue(m.isIdentity)
    }

    @Test
    fun `pivoted MakeSkew leaves pivot fixed`() {
        // Skew x by 0.5 around (10, 4) — point (10, 4) should be fixed.
        val m = Matrix3x3F32.MakeSkew(0.5f, 0f, 10f, 4f)
        val (x, y) = m.mapXY(10f, 4f)
        assertNear(10f, x); assertNear(4f, y)
    }

    @Test
    fun `MakeRotate cardinal angles produce bit-exact axis-aligned matrices`() {
        // After Phase 3 snap-to-zero, sin/cos near-zero residues are snapped to 0f,
        // so cardinal angles produce exactly axis-aligned matrices (sx, sy, kx, ky
        // ∈ {-1, 0, 1} bit-exact).
        for ((deg, expected) in listOf(
            0f   to floatArrayOf( 1f,  0f,  0f,  1f),  // (sx, kx, ky, sy)
            90f  to floatArrayOf( 0f, -1f,  1f,  0f),
            180f to floatArrayOf(-1f,  0f,  0f, -1f),
            270f to floatArrayOf( 0f,  1f, -1f,  0f),
            -90f to floatArrayOf( 0f,  1f, -1f,  0f),
            -180f to floatArrayOf(-1f, 0f,  0f, -1f),
        )) {
            val m = Matrix3x3F32.MakeRotate(deg)
            assertEquals(expected[0], m.sx, "sx at $deg deg")
            assertEquals(expected[1], m.kx, "kx at $deg deg")
            assertEquals(expected[2], m.ky, "ky at $deg deg")
            assertEquals(expected[3], m.sy, "sy at $deg deg")
        }
    }

    @Test
    fun `MakeAll round-trips through fields`() {
        val m = Matrix3x3F32.MakeAll(2f, 3f, 5f, 7f, 11f, 13f)
        assertEquals(2f, m.sx); assertEquals(3f, m.kx); assertEquals(5f, m.tx)
        assertEquals(7f, m.ky); assertEquals(11f, m.sy); assertEquals(13f, m.ty)
    }

    @Test
    fun `MakeRotate snap does not leak negative zero`() {
        // Phase 3: snapToZero should normalize -0f → 0f via the explicit
        // negation guard. Use Float.toRawBits to assert bit-exact.
        for (deg in listOf(0f, 90f, 180f, 270f, -90f, -180f, 360f)) {
            val m = Matrix3x3F32.MakeRotate(deg)
            for ((name, v) in listOf("sx" to m.sx, "kx" to m.kx, "ky" to m.ky, "sy" to m.sy)) {
                if (v == 0f) {
                    assertEquals(0, v.toRawBits(),
                        "$name at $deg deg leaked -0f")
                }
            }
        }
    }

    @Test
    fun `invert exactly-singular returns null`() {
        // Two equal rows ⇒ det = 0 exactly.
        val m = Matrix3x3F32(sx = 2f, kx = 4f, tx = 1f, ky = 1f, sy = 2f, ty = 0f)
        assertEquals(null, m.invert())
    }

    @Test
    fun `invert near-singular returns null`() {
        // Tiny scale on both axes yields det ≈ 1e-12, below the SK_DetNearlyZero
        // threshold of 1.46e-11. Without the threshold check, the inverse would
        // produce ~1e6-magnitude finite garbage values.
        val m = Matrix3x3F32(sx = 1e-6f, kx = 0f, tx = 0f, ky = 0f, sy = 1e-6f, ty = 0f)
        assertEquals(null, m.invert(), "det ≈ 1e-12 should be treated as singular")
    }

    @Test
    fun `invert non-singular returns valid inverse`() {
        // Sanity check the threshold doesn't bite a normal matrix.
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val inv = m.invert()!!
        // M · M⁻¹ should be identity (within float precision).
        val composed = Matrix3x3F32.concat(m, inv)
        assertNear(1f, composed.sx); assertNear(0f, composed.kx); assertNear(0f, composed.tx)
        assertNear(0f, composed.ky); assertNear(1f, composed.sy); assertNear(0f, composed.ty)
    }

    @Test
    fun `MakeRotate(0) and MakeRotate(180) are isAxisAligned`() {
        // 0° and 180° preserve `kx == ky == 0` after snap — they fit our
        // strict isAxisAligned (translate + scale only) definition.
        // 90/270/-90 swap axes (kx, ky != 0) so they're NOT isAxisAligned;
        // matching Skia, that's `rectStaysRect`, a looser predicate we don't expose yet.
        assertTrue(Matrix3x3F32.MakeRotate(0f).isAxisAligned)
        assertTrue(Matrix3x3F32.MakeRotate(180f).isAxisAligned)
        assertTrue(Matrix3x3F32.MakeRotate(-180f).isAxisAligned)
        assertFalse(Matrix3x3F32.MakeRotate(90f).isAxisAligned)
        assertFalse(Matrix3x3F32.MakeRotate(-90f).isAxisAligned)
    }

    @Test
    fun `concat 10-step chain stable to 1 ulp vs double reference`() {
        // Build a 10-step CTM chain (translate → scale → rotate → skew, repeated).
        // The Kotlin float chain must agree with a double-precision reference
        // implementation to within ~1 ulp at the float level (~ 1e-4 in unit space).
        var m = Matrix3x3F32.Identity
        // Reference matrix as 6 doubles, computed without any float intermediate.
        var rsx = 1.0; var rkx = 0.0; var rtx = 0.0
        var rky = 0.0; var rsy = 1.0; var rty = 0.0
        fun refConcat(asx: Double, akx: Double, atx: Double, aky: Double, asy: Double, aty: Double) {
            // M_new = M · A (pre-concat).
            val nsx = rsx * asx + rkx * aky
            val nkx = rsx * akx + rkx * asy
            val ntx = rsx * atx + rkx * aty + rtx
            val nky = rky * asx + rsy * aky
            val nsy = rky * akx + rsy * asy
            val nty = rky * atx + rsy * aty + rty
            rsx = nsx; rkx = nkx; rtx = ntx
            rky = nky; rsy = nsy; rty = nty
        }
        for (i in 1..10) {
            val angle = (i * 17).toFloat()
            val tx = (i * 0.3f); val ty = (i * 0.7f)
            val sx = 1f + i * 0.05f; val sy = 1f - i * 0.04f
            val kx = i * 0.02f; val ky = -i * 0.03f
            // Apply same operations to the float matrix and the double reference.
            m = m.preTranslate(tx, ty)
            refConcat(1.0, 0.0, tx.toDouble(), 0.0, 1.0, ty.toDouble())
            m = m.preScale(sx, sy)
            refConcat(sx.toDouble(), 0.0, 0.0, 0.0, sy.toDouble(), 0.0)
            val rad = angle.toDouble() * kotlin.math.PI / 180.0
            val c = kotlin.math.cos(rad); val s = kotlin.math.sin(rad)
            m = m.preRotate(angle)
            refConcat(c, -s, 0.0, s, c, 0.0)
            m = m.preSkew(kx, ky)
            refConcat(1.0, kx.toDouble(), 0.0, ky.toDouble(), 1.0, 0.0)
        }
        // Compare each cell. Tolerance: 5e-3 absolute is comfortable for a chain of
        // 40 transforms with float intermediate; tighten if Phase 2 buys more.
        val eps = 5e-3f
        assertEquals(rsx.toFloat(), m.sx, eps)
        assertEquals(rkx.toFloat(), m.kx, eps)
        assertEquals(rtx.toFloat(), m.tx, eps)
        assertEquals(rky.toFloat(), m.ky, eps)
        assertEquals(rsy.toFloat(), m.sy, eps)
        assertEquals(rty.toFloat(), m.ty, eps)
    }

    @Test
    fun `pivoted preScale matches preConcat-with-MakeScale-pivoted`() {
        val base = Matrix3x3F32.MakeRotate(30f).preTranslate(7f, 11f)
        val viaPre = base.preScale(2f, 3f, 5f, 5f)
        val viaConcat = Matrix3x3F32.concat(base, Matrix3x3F32.MakeScale(2f, 3f, 5f, 5f))
        // Apply both to a probe point — must agree to sub-pixel.
        val (a, b) = viaPre.mapXY(2f, 3f)
        val (c, d) = viaConcat.mapXY(2f, 3f)
        assertEquals(c, a, 1e-4f); assertEquals(d, b, 1e-4f)
    }

    @Test
    fun `singular values are positive`() {
        // Random affine matrix should have positive max scale.
        val m = Matrix3x3F32(sx = 1.5f, kx = 0.7f, tx = 100f, ky = -0.3f, sy = 2f, ty = -50f)
        val s = m.getMaxScale()
        assertTrue(s > 0f, "max scale must be positive (got $s)")
        // Sanity: should be at least norm of first row.
        val rowNorm = sqrt(1.5f * 1.5f + 0.7f * 0.7f)
        assertTrue(s >= rowNorm * 0.9f, "max scale $s < first-row norm $rowNorm")
    }

    // ─── Phase 1: type-mask system ──────────────────────────────────────

    @Test
    fun `getType identity matrix`() {
        assertEquals(Matrix3x3F32.kIdentity_Mask, Matrix3x3F32.Identity.getType())
        assertTrue(Matrix3x3F32.Identity.isIdentity)
        assertTrue(Matrix3x3F32.Identity.rectStaysRect())
        assertFalse(Matrix3x3F32.Identity.hasPerspective())
    }

    @Test
    fun `getType pure translate`() {
        val m = Matrix3x3F32.MakeTrans(5f, 7f)
        assertEquals(Matrix3x3F32.kTranslate_Mask, m.getType())
        assertTrue(m.isTranslate())
        assertTrue(m.isScaleTranslate())
        assertTrue(m.rectStaysRect())
        assertFalse(m.isIdentity)
    }

    @Test
    fun `getType pure scale sets translate too when off-origin`() {
        val pureScale = Matrix3x3F32.MakeScale(2f, 3f)
        assertEquals(Matrix3x3F32.kScale_Mask, pureScale.getType())
        assertTrue(pureScale.isScaleTranslate())
        assertTrue(pureScale.rectStaysRect())

        val pivotScale = Matrix3x3F32.MakeScale(2f, 3f, 1f, 1f)
        // pivotScale has tx, ty != 0 so kTranslate_Mask is also set.
        assertEquals(Matrix3x3F32.kScale_Mask or Matrix3x3F32.kTranslate_Mask, pivotScale.getType())
    }

    @Test
    fun `getType cardinal rotate sets affine plus rectStaysRect`() {
        val m = Matrix3x3F32.MakeRotate(90f)
        // 90° rotation has kx=-1, ky=1, sx=sy=0 — Skia flags as Affine|Scale
        // and rectStaysRect since the 2x2 swaps axes cleanly.
        assertEquals(Matrix3x3F32.kAffine_Mask or Matrix3x3F32.kScale_Mask, m.getType())
        assertFalse(m.isScaleTranslate())   // skew/rotation present
        assertTrue(m.rectStaysRect(), "90° rotation maps rect to rect")
    }

    @Test
    fun `getType arbitrary rotate clears rectStaysRect`() {
        val m = Matrix3x3F32.MakeRotate(45f)
        assertEquals(Matrix3x3F32.kAffine_Mask or Matrix3x3F32.kScale_Mask, m.getType())
        assertFalse(m.rectStaysRect(), "45° rotation does not preserve axis alignment")
    }

    @Test
    fun `hasPerspective is always false in this affine port`() {
        for (m in listOf(
            Matrix3x3F32.Identity,
            Matrix3x3F32.MakeTrans(1f, 1f),
            Matrix3x3F32.MakeScale(2f, 3f),
            Matrix3x3F32.MakeRotate(45f),
            Matrix3x3F32.MakeSkew(0.5f, 0.7f),
        )) {
            assertFalse(m.hasPerspective(), "hasPerspective should be false for $m")
        }
    }

    @Test
    fun `isSimilarity identity translate uniform-scale rotate`() {
        assertTrue(Matrix3x3F32.Identity.isSimilarity())
        assertTrue(Matrix3x3F32.MakeTrans(5f, 7f).isSimilarity())
        // Uniform scale ⇒ similarity.
        assertTrue(Matrix3x3F32.MakeScale(2f, 2f).isSimilarity())
        // Pure rotation ⇒ similarity.
        assertTrue(Matrix3x3F32.MakeRotate(30f).isSimilarity())
        // Non-uniform scale ⇒ NOT a similarity.
        assertFalse(Matrix3x3F32.MakeScale(2f, 3f).isSimilarity())
        // Skew ⇒ NOT a similarity (preserves neither angle nor uniform scale).
        assertFalse(Matrix3x3F32.MakeSkew(0.5f, 0f).isSimilarity())
    }

    @Test
    fun `preservesRightAngles identity translate scale rotate skew`() {
        assertTrue(Matrix3x3F32.Identity.preservesRightAngles())
        assertTrue(Matrix3x3F32.MakeTrans(3f, 5f).preservesRightAngles())
        // Pure scale (even non-uniform) preserves right angles.
        assertTrue(Matrix3x3F32.MakeScale(2f, 5f).preservesRightAngles())
        // Pure rotation preserves right angles.
        assertTrue(Matrix3x3F32.MakeRotate(30f).preservesRightAngles())
        // Skew does NOT.
        assertFalse(Matrix3x3F32.MakeSkew(0.5f, 0f).preservesRightAngles())
    }

    @Test
    fun `cheapEqualTo is IEEE-strict on NaN`() {
        val a = Matrix3x3F32(sx = Float.NaN)
        val b = Matrix3x3F32(sx = Float.NaN)
        // data class equals is NaN-friendly...
        assertEquals(a, b)
        // ...cheapEqualTo is IEEE-strict (NaN != NaN).
        assertFalse(a.cheapEqualTo(b))
        // For finite values, both forms agree.
        val m = Matrix3x3F32.MakeScale(2f, 3f)
        assertTrue(m.cheapEqualTo(Matrix3x3F32.MakeScale(2f, 3f)))
    }

    @Test
    fun `cheapEqualTo compares perspective fields`() {
        val a = Matrix3x3F32.MakeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0.25f, 0f, 1f,
        )
        val b = Matrix3x3F32.MakeAll(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0.5f, 0f, 1f,
        )

        assertFalse(a.cheapEqualTo(b))
    }

    // ─── Phase 2: mapping API ──────────────────────────────────────────

    @Test
    fun `mapXY Vector2F32 overload matches mapXY x y`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val (x, y) = m.mapXY(2f, 3f)
        val p = m.mapXY(Vector2F32.of(2f, 3f))
        assertEquals(x, p.x); assertEquals(y, p.y)
    }

    @Test
    fun `mapVector ignores translation`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(100f, 200f)
        // Linear part for a (1, 0) vector: (sx, ky) = (2, 0). Translation
        // dropped — even though tx/ty are large, vector mapping shouldn't add them.
        val v = m.mapVector(1f, 0f)
        assertEquals(2f, v.x); assertEquals(0f, v.y)
    }

    @Test
    fun `mapVector with perspective matches mapPoint minus origin`() {
        val m = Matrix3x3F32.MakeAll(
            1.2f, 0.3f, 5f,
            0.1f, 1.5f, 7f,
            0.002f, 0.003f, 1f,
        )
        val origin = m.mapXY(Vector2F32.of(0f, 0f))
        val point = m.mapXY(Vector2F32.of(2.5f, -1.5f))
        val vector = m.mapVector(2.5f, -1.5f)

        assertNear(point.x - origin.x, vector.x, eps = 1e-4f)
        assertNear(point.y - origin.y, vector.y, eps = 1e-4f)
    }

    @Test
    fun `mapPoints identity is straight copy`() {
        val src = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f), Vector2F32.of(5f, 6f))
        val dst = Array(3) { Vector2F32.of(0f, 0f) }
        Matrix3x3F32.Identity.mapPoints(dst, src, 3)
        for (i in 0 until 3) {
            assertEquals(src[i].x, dst[i].x); assertEquals(src[i].y, dst[i].y)
        }
    }

    @Test
    fun `mapPoints translate adds tx ty`() {
        val src = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f))
        val dst = Array(2) { Vector2F32.Zero }
        Matrix3x3F32.MakeTrans(10f, 20f).mapPoints(dst, src, 2)
        assertEquals(11f, dst[0].x); assertEquals(22f, dst[0].y)
        assertEquals(13f, dst[1].x); assertEquals(24f, dst[1].y)
    }

    @Test
    fun `mapPoints scale-translate fast path`() {
        val src = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f))
        val dst = Array(2) { Vector2F32.Zero }
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        m.mapPoints(dst, src, 2)
        // Each src point: (2*x + tx_eff, 3*y + ty_eff). preTranslate:
        // base = MakeScale, then preTranslate ⇒ tx = 2*5 = 10, ty = 3*7 = 21.
        assertEquals(2 * 1 + 10f, dst[0].x); assertEquals(3 * 2 + 21f, dst[0].y)
        assertEquals(2 * 3 + 10f, dst[1].x); assertEquals(3 * 4 + 21f, dst[1].y)
    }

    @Test
    fun `mapPoints full affine matches mapXY per point`() {
        val m = Matrix3x3F32.MakeRotate(30f).preTranslate(5f, 7f)
        val src = arrayOf(Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f), Vector2F32.of(2f, 3f))
        val dst = Array(3) { Vector2F32.Zero }
        m.mapPoints(dst, src, 3)
        for (i in 0 until 3) {
            val expected = m.mapXY(src[i])
            assertNear(expected.x, dst[i].x)
            assertNear(expected.y, dst[i].y)
        }
    }

    @Test
    fun `mapPoints in-place overload matches dst-form`() {
        val pts = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f))
        val src = arrayOf(Vector2F32.of(1f, 2f), Vector2F32.of(3f, 4f))
        val expected = Array(2) { Vector2F32.Zero }
        val m = Matrix3x3F32.MakeRotate(30f)
        m.mapPoints(expected, src, 2)
        m.mapPoints(pts, 2)
        for (i in 0 until 2) {
            assertNear(expected[i].x, pts[i].x)
            assertNear(expected[i].y, pts[i].y)
        }
    }

    @Test
    fun `mapVectors ignores translation`() {
        val src = arrayOf(Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f))
        val dst = Array(2) { Vector2F32.Zero }
        // Big translate shouldn't show up in vectors.
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(100f, 200f)
        m.mapVectors(dst, src, 2)
        assertEquals(2f, dst[0].x); assertEquals(0f, dst[0].y)
        assertEquals(0f, dst[1].x); assertEquals(3f, dst[1].y)
    }

    @Test
    fun `mapVectors with perspective matches mapPoint - mapPoint(0, 0)`() {
        // Audit divergence #4: upstream mapVectors uses
        // `mapPointPerspective(src) - mapPointPerspective({0,0})` for
        // perspective matrices; the naive 2x2 linear part is wrong
        // because perspective is non-linear.
        val m = Matrix3x3F32.MakeAll(
            1.2f, 0.3f, 5f,
            0.1f, 1.5f, 7f,
            0.002f, 0.003f, 1f,
        )
        assertTrue(m.hasPerspective())

        val origin = m.mapXY(Vector2F32.of(0f, 0f))
        val srcs = arrayOf(Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f), Vector2F32.of(2.5f, -1.5f))
        val dst = Array(srcs.size) { Vector2F32.Zero }
        m.mapVectors(dst, srcs, srcs.size)

        for (i in srcs.indices) {
            val pt = m.mapXY(srcs[i])
            val expectedX = pt.x - origin.x
            val expectedY = pt.y - origin.y
            assertNear(expectedX, dst[i].x, eps = 1e-4f,
                msg = "mapVectors[$i].x (perspective)")
            assertNear(expectedY, dst[i].y, eps = 1e-4f,
                msg = "mapVectors[$i].y (perspective)")
        }
    }

    @Test
    fun `mapVectors affine fast path unchanged`() {
        // Regression guard: the affine (no perspective) path must keep
        // its closed-form `sx*x + kx*y, ky*x + sy*y` semantics so a
        // pure 2D rotation+scale matrix still produces exact results
        // independent of the translation column.
        val m = Matrix3x3F32.MakeAll(
            2f, -1f, 50f,
            3f,  4f, 90f,
        )
        assertFalse(m.hasPerspective())
        val src = arrayOf(Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f), Vector2F32.of(1f, 1f))
        val dst = Array(src.size) { Vector2F32.Zero }
        m.mapVectors(dst, src, src.size)
        // Linear part = [[2, -1], [3, 4]].
        assertEquals(2f, dst[0].x); assertEquals(3f, dst[0].y)
        assertEquals(-1f, dst[1].x); assertEquals(4f, dst[1].y)
        assertEquals(1f, dst[2].x); assertEquals(7f, dst[2].y)
    }

    @Test
    fun `mapRectScaleTranslate fast path matches mapRect`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val r = RectF32.MakeLTRB(0f, 0f, 10f, 10f)
        // The general mapRect goes through mapRectScaleTranslate fast path
        // when isScaleTranslate; verify the explicit call agrees.
        val fast = m.mapRectScaleTranslate(r)
        val general = m.mapRect(r)
        assertTrue(fast.equalsLTRB(general))
    }

    @Test
    fun `mapRectScaleTranslate handles negative scale by sorting`() {
        val m = Matrix3x3F32.MakeScale(-2f, 1f)
        val r = RectF32.MakeLTRB(1f, 0f, 3f, 5f)
        // x: -2*1 = -2, -2*3 = -6 ⇒ left=-6, right=-2.
        val mapped = m.mapRectScaleTranslate(r)
        assertEquals(-6f, mapped.left); assertEquals(-2f, mapped.right)
        assertEquals(0f, mapped.top); assertEquals(5f, mapped.bottom)
    }

    @Test
    fun `mapRectScaleTranslate throws when not scale-translate`() {
        val m = Matrix3x3F32.MakeRotate(30f)
        assertFailsWith<IllegalStateException> {
            m.mapRectScaleTranslate(RectF32.MakeLTRB(0f, 0f, 1f, 1f))
        }
    }

    @Test
    fun `mapRadius equals scale magnitude for pure scale`() {
        val m = Matrix3x3F32.MakeScale(3f, 4f)
        // |det| = 12, sqrt = sqrt(12).
        assertNear(kotlin.math.sqrt(12f) * 5f, m.mapRadius(5f), eps = 1e-4f)
    }

    @Test
    fun `mapRadius is invariant under rotation`() {
        val r = 7f
        // Pure rotation ⇒ |det| = 1 ⇒ mapRadius(r) = r.
        for (deg in floatArrayOf(0f, 30f, 90f, 137f)) {
            assertNear(r, Matrix3x3F32.MakeRotate(deg).mapRadius(r), eps = 1e-4f)
        }
    }

    @Test
    fun `mapRadius uses mapped axis vector lengths under perspective`() {
        val r = 5f
        val m = Matrix3x3F32.MakeAll(
            1.2f, 0.3f, 5f,
            0.1f, 1.5f, 7f,
            0.002f, 0.003f, 1f,
        )
        val vectors = arrayOf(Vector2F32.of(r, 0f), Vector2F32.of(0f, r))
        m.mapVectors(vectors)
        val d0 = sqrt(vectors[0].x * vectors[0].x + vectors[0].y * vectors[0].y)
        val d1 = sqrt(vectors[1].x * vectors[1].x + vectors[1].y * vectors[1].y)

        assertNear(sqrt(d0 * d1), m.mapRadius(r), eps = 1e-4f)
    }

    // ─── Phase 3: function-style accessors + det ─────────────────────────

    @Test
    fun `function-style accessors mirror direct field access`() {
        val m = Matrix3x3F32(sx = 2f, kx = 3f, tx = 5f, ky = 7f, sy = 11f, ty = 13f)
        assertEquals(2f, m.getScaleX()); assertEquals(11f, m.getScaleY())
        assertEquals(3f, m.getSkewX()); assertEquals(7f, m.getSkewY())
        assertEquals(5f, m.getTranslateX()); assertEquals(13f, m.getTranslateY())
        assertEquals(0f, m.getPerspX()); assertEquals(0f, m.getPerspY())
    }

    @Test
    fun `det2x2 and det match algebraic value`() {
        val m = Matrix3x3F32(sx = 2f, kx = 3f, tx = 100f, ky = 5f, sy = 7f, ty = 200f)
        // sx*sy - kx*ky = 14 - 15 = -1
        assertEquals(-1f, m.det2x2()); assertEquals(-1f, m.det())
    }

    // ─── Phase 3: array exchange ─────────────────────────────────────────

    @Test
    fun `get9 round-trips through MakeFrom9`() {
        val m = Matrix3x3F32(sx = 2f, kx = 3f, tx = 5f, ky = 7f, sy = 11f, ty = 13f)
        val buf = FloatArray(9)
        m.get9(buf)
        // Row-major: [sx, kx, tx, ky, sy, ty, 0, 0, 1]
        assertEquals(floatArrayOf(2f, 3f, 5f, 7f, 11f, 13f, 0f, 0f, 1f).toList(), buf.toList())
        assertEquals(m, Matrix3x3F32.MakeFrom9(buf))
    }

    @Test
    fun `MakeFrom9 accepts arbitrary perspective row`() {
        // Pre-Phase 4 the constructor asserted [0, 0, 1]; now perspective
        // is fully supported, so a non-trivial perspective row builds a
        // perspective matrix that hasPerspective() flags accordingly.
        val buf = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0.5f, 0f, 1f)
        val m = Matrix3x3F32.MakeFrom9(buf)
        assertTrue(m.hasPerspective())
        assertEquals(0.5f, m.persp0)
    }

    @Test
    fun `asAffine round-trips through MakeFromAffine`() {
        val m = Matrix3x3F32(sx = 2f, kx = 3f, tx = 5f, ky = 7f, sy = 11f, ty = 13f)
        val buf = FloatArray(6)
        assertTrue(m.asAffine(buf))
        // Skia COLUMN-major: [scaleX, skewY, skewX, scaleY, transX, transY] = [2, 7, 3, 11, 5, 13]
        assertEquals(floatArrayOf(2f, 7f, 3f, 11f, 5f, 13f).toList(), buf.toList())
        assertEquals(m, Matrix3x3F32.MakeFromAffine(buf))
    }

    // ─── Phase 3: MakeRectToRect ─────────────────────────────────────────

    @Test
    fun `MakeRectToRect kFill stretches independently`() {
        val src = RectF32.MakeLTRB(0f, 0f, 10f, 5f)
        val dst = RectF32.MakeLTRB(100f, 200f, 300f, 600f)
        val m = Matrix3x3F32.MakeRectToRect(src, dst, Matrix3x3F32.ScaleToFit.kFill_ScaleToFit)!!
        // sx = 200/10 = 20, sy = 400/5 = 80
        assertEquals(20f, m.getScaleX()); assertEquals(80f, m.getScaleY())
        // Maps src.TL to dst.TL.
        val tl = m.mapXY(Vector2F32.of(src.left, src.top))
        assertNear(dst.left, tl.x); assertNear(dst.top, tl.y)
        // Maps src.BR to dst.BR.
        val br = m.mapXY(Vector2F32.of(src.right, src.bottom))
        assertNear(dst.right, br.x); assertNear(dst.bottom, br.y)
    }

    @Test
    fun `MakeRectToRect kCenter uses uniform scale and centres in dst`() {
        val src = RectF32.MakeLTRB(0f, 0f, 10f, 5f)
        val dst = RectF32.MakeLTRB(0f, 0f, 200f, 400f)
        val m = Matrix3x3F32.MakeRectToRect(src, dst, Matrix3x3F32.ScaleToFit.kCenter_ScaleToFit)!!
        // Uniform scale = min(200/10, 400/5) = min(20, 80) = 20.
        assertEquals(20f, m.getScaleX()); assertEquals(20f, m.getScaleY())
        // The mapped src is 200×100, centred in 200×400 ⇒ ty = 150.
        assertEquals(0f, m.getTranslateX()); assertEquals(150f, m.getTranslateY())
    }

    @Test
    fun `MakeRectToRect returns null for empty src`() {
        assertEquals(null, Matrix3x3F32.MakeRectToRect(
            RectF32.MakeEmpty(),
            RectF32.MakeLTRB(0f, 0f, 10f, 10f),
            Matrix3x3F32.ScaleToFit.kFill_ScaleToFit,
        ))
    }

    // ─── Phase 3: getMaxScale / getMinScale / getMinMaxScales ────────────

    @Test
    fun `getMaxScale matches legacy computeMaxScale on canonical inputs`() {
        // Pure scale.
        assertNear(2f, Matrix3x3F32.MakeScale(2f, 1f).getMaxScale())
        // Rotation only.
        assertNear(1f, Matrix3x3F32.MakeRotate(45f).getMaxScale())
        // Identity.
        assertNear(1f, Matrix3x3F32.Identity.getMaxScale())
    }

    @Test
    fun `getMinScale on pure scale returns smaller absolute axis`() {
        assertNear(1f, Matrix3x3F32.MakeScale(2f, 1f).getMinScale())
        assertNear(0.5f, Matrix3x3F32.MakeScale(2f, 0.5f).getMinScale())
    }

    @Test
    fun `getMinMaxScales returns sorted min and max`() {
        val s = FloatArray(2)
        Matrix3x3F32.MakeScale(3f, 2f).getMinMaxScales(s)
        assertNear(2f, s[0]); assertNear(3f, s[1])
    }

    @Test
    fun `getMinMaxScales for rotated scale returns scale axes`() {
        val s = FloatArray(2)
        // 45° rotation of an asymmetric scale.
        val m = Matrix3x3F32.MakeScale(4f, 1f).preRotate(45f)
        m.getMinMaxScales(s)
        assertNear(1f, s[0], eps = 1e-3f); assertNear(4f, s[1], eps = 1e-3f)
    }

    @Test
    fun `deprecated computeMaxScale forwards to getMaxScale`() {
        @Suppress("DEPRECATION")
        assertEquals(Matrix3x3F32.MakeScale(7f, 3f).getMaxScale(),
            Matrix3x3F32.MakeScale(7f, 3f).computeMaxScale())
    }

    // ─── Phase 3: decomposeScale ──────────────────────────────────────

    @Test
    fun `decomposeScale on pure scale returns scale and identity remaining`() {
        val (scale, remaining) = Matrix3x3F32.MakeScale(3f, 5f).decomposeScale()!!
        assertNear(3f, scale.x); assertNear(5f, scale.y)
        // Remaining should be identity (within float tolerance).
        assertNear(1f, remaining.getScaleX(), eps = 1e-5f)
        assertNear(1f, remaining.getScaleY(), eps = 1e-5f)
    }

    @Test
    fun `decomposeScale on rotated scale extracts magnitudes`() {
        // Rotate then scale: scale magnitudes survive.
        val m = Matrix3x3F32.MakeScale(3f, 5f).preRotate(45f)
        val (scale, _) = m.decomposeScale()!!
        // Skia's decomposeScale uses Length(sx, ky) and Length(kx, sy).
        // For our 45°-rotated 3×5: row norms ≈ sqrt(3² · cos²45 + 5² · sin²45) ≈ same per axis.
        // Use round-trip via remaining as sanity.
        assertTrue(scale.x > 0f && scale.y > 0f)
    }

    @Test
    fun `decomposeScale fails on near-singular matrix`() {
        val m = Matrix3x3F32(sx = 1e-10f, kx = 0f, tx = 0f, ky = 0f, sy = 1e-10f, ty = 0f)
        assertEquals(null, m.decomposeScale())
    }

    // ─── Phase 4: perspective ────────────────────────────────────────────

    @Test
    fun `default constructor has identity perspective row`() {
        val m = Matrix3x3F32()
        assertEquals(0f, m.persp0); assertEquals(0f, m.persp1); assertEquals(1f, m.persp2)
        assertFalse(m.hasPerspective())
    }

    @Test
    fun `MakePerspective flips hasPerspective and getType`() {
        val m = Matrix3x3F32.MakePerspective(0.001f, 0.002f)
        assertTrue(m.hasPerspective())
        // Skia: perspective ⇒ all type bits set.
        assertEquals(Matrix3x3F32.kPerspective_Mask, m.getType() and Matrix3x3F32.kPerspective_Mask)
    }

    @Test
    fun `mapXY does perspective divide`() {
        // M = identity but with persp0 = 0.5 ⇒ for input (2, 0):
        //   px = 2, py = 0, w = 0.5*2 + 0 + 1 = 2 ⇒ output = (1, 0).
        val m = Matrix3x3F32.MakePerspective(0.5f, 0f)
        val (x, y) = m.mapXY(2f, 0f)
        assertNear(1f, x); assertNear(0f, y)
    }

    @Test
    fun `mapXY in affine matrix is unchanged`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val (x, y) = m.mapXY(1f, 1f)
        // Same as before perspective extension: (2*1 + 2*5, 3*1 + 3*7) = (12, 24).
        assertNear(12f, x); assertNear(24f, y)
    }

    @Test
    fun `mapPoints handles perspective branch`() {
        val m = Matrix3x3F32.MakePerspective(0.5f, 0f)
        val src = arrayOf(Vector2F32.of(2f, 0f), Vector2F32.of(0f, 4f), Vector2F32.of(2f, 4f))
        val dst = Array(3) { Vector2F32.Zero }
        m.mapPoints(dst, src, 3)
        // (2, 0): w=2 ⇒ (1, 0). (0, 4): w=1 ⇒ (0, 4). (2, 4): w=2 ⇒ (1, 2).
        assertNear(1f, dst[0].x); assertNear(0f, dst[0].y)
        assertNear(0f, dst[1].x); assertNear(4f, dst[1].y)
        assertNear(1f, dst[2].x); assertNear(2f, dst[2].y)
    }

    @Test
    fun `concat of two perspective matrices builds 3x3 product`() {
        val a = Matrix3x3F32.MakePerspective(0.5f, 0f)
        val b = Matrix3x3F32.MakePerspective(0f, 0.25f)
        val ab = Matrix3x3F32.concat(a, b)
        // Round-trip via mapXY: (a · b)(p) == a(b(p)).
        val p = Vector2F32.of(2f, 4f)
        val direct = ab.mapXY(p)
        val twoStep = a.mapXY(b.mapXY(p))
        assertNear(twoStep.x, direct.x, eps = 1e-3f)
        assertNear(twoStep.y, direct.y, eps = 1e-3f)
    }

    @Test
    fun `invert of perspective matrix round-trips`() {
        val m = Matrix3x3F32.MakePerspective(0.5f, 0.25f).preTranslate(2f, 3f)
        val inv = m.invert()!!
        val composed = Matrix3x3F32.concat(m, inv)
        // composed should be identity ⇒ map (3, 5) back to (3, 5).
        val (x, y) = composed.mapXY(3f, 5f)
        assertNear(3f, x, eps = 1e-3f); assertNear(5f, y, eps = 1e-3f)
    }

    @Test
    fun `det collapses to det2x2 on affine`() {
        val m = Matrix3x3F32.MakeRotate(30f).preScale(2f, 3f)
        assertNear(m.det2x2(), m.det(), eps = 1e-4f)
    }

    @Test
    fun `det differs from det2x2 on perspective with non-zero translate`() {
        // Need both translate AND perspective for det / det2x2 to diverge:
        // det(3x3) = sx·(sy·p2 - ty·p1) - kx·(ky·p2 - ty·p0) + tx·(ky·p1 - sy·p0)
        // With tx=10, persp0=0.5, sy=3 ⇒ extra term = 10·(0 - 3·0.5) = -15.
        val n = Matrix3x3F32(sx = 2f, kx = 0f, tx = 10f, ky = 0f, sy = 3f, ty = 0f, persp0 = 0.5f)
        // det2x2 = 2·3 - 0·0 = 6
        assertEquals(6f, n.det2x2())
        // det = 2·3 - 0 + 10·(-1.5) = -9
        assertEquals(-9f, n.det())
        assertNotEquals(n.det2x2(), n.det())
    }

    @Test
    fun `mapHomogeneousPoints identity returns w=1`() {
        val src = arrayOf(Vector2F32.of(2f, 3f), Vector2F32.of(5f, 7f))
        val dst = Array(2) { Vector3F32.of() }
        Matrix3x3F32.Identity.mapHomogeneousPoints(dst, src, 2)
        assertEquals(Vector3F32.of(2f, 3f, 1f), dst[0])
        assertEquals(Vector3F32.of(5f, 7f, 1f), dst[1])
    }

    @Test
    fun `mapHomogeneousPoints affine returns w=1 with mapped xy`() {
        val src = arrayOf(Vector2F32.of(1f, 0f))
        val dst = Array(1) { Vector3F32.of() }
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        m.mapHomogeneousPoints(dst, src, 1)
        assertEquals(1f, dst[0].z)
        // mapped xy = (2*1 + 2*5, 3*0 + 3*7) = (12, 21).
        assertNear(12f, dst[0].x); assertNear(21f, dst[0].y)
    }

    @Test
    fun `mapHomogeneousPoints perspective returns un-divided w`() {
        val src = arrayOf(Vector2F32.of(2f, 4f))
        val dst = Array(1) { Vector3F32.of() }
        val m = Matrix3x3F32.MakePerspective(0.5f, 0f)
        m.mapHomogeneousPoints(dst, src, 1)
        // w = 0.5*2 + 0*4 + 1 = 2; xy unchanged from input.
        assertNear(2f, dst[0].x); assertNear(4f, dst[0].y); assertNear(2f, dst[0].z)
    }

    // ─── Phase 4: MakeRSXform ────────────────────────────────────────────

    @Test
    fun `MakeRSXform identity yields identity matrix`() {
        val m = Matrix3x3F32.MakeRSXform(scos = 1f, ssin = 0f, tx = 0f, ty = 0f)
        assertTrue(m.isIdentity)
    }

    @Test
    fun `MakeRSXform 90 deg rotation maps (1, 0) to (0, 1) plus translate`() {
        // 90° rotation: scos = 0, ssin = 1. Plus translation (10, 20).
        val m = Matrix3x3F32.MakeRSXform(scos = 0f, ssin = 1f, tx = 10f, ty = 20f)
        val (x, y) = m.mapXY(1f, 0f)
        assertNear(10f, x); assertNear(21f, y)   // (0, 1) + (10, 20)
    }

    @Test
    fun `MakeRSXform with uniform scale 2 doubles vector lengths`() {
        // 0° rotation, scale 2. scos = 2, ssin = 0.
        val m = Matrix3x3F32.MakeRSXform(scos = 2f, ssin = 0f, tx = 0f, ty = 0f)
        val (x, y) = m.mapXY(1f, 0f)
        assertNear(2f, x); assertNear(0f, y)
    }

    @Test
    fun `MakeRSXform pivoted matches SkRSXform anchor translation`() {
        val m = Matrix3x3F32.MakeRSXform(
            scos = 0f, ssin = 1f,
            anchorX = 2f, anchorY = 3f,
            tx = 10f, ty = 20f,
        )

        assertNear(13f, m.tx)
        assertNear(18f, m.ty)
        val anchored = m.mapXY(Vector2F32.of(2f, 3f))
        assertNear(10f, anchored.x)
        assertNear(20f, anchored.y)
    }

    // ─── Phase 4: MakePolyToPoly ─────────────────────────────────────────

    @Test
    fun `MakePolyToPoly empty returns identity`() {
        val m = Matrix3x3F32.MakePolyToPoly(emptyArray(), emptyArray())!!
        assertTrue(m.isIdentity)
    }

    @Test
    fun `MakePolyToPoly 1 point is pure translate`() {
        val m = Matrix3x3F32.MakePolyToPoly(
            arrayOf(Vector2F32.of(0f, 0f)),
            arrayOf(Vector2F32.of(5f, 7f)),
        )!!
        assertTrue(m.isTranslate())
        assertEquals(5f, m.tx); assertEquals(7f, m.ty)
    }

    @Test
    fun `setPolyToPoly delegates to MakePolyToPoly`() {
        val src = arrayOf(Vector2F32.of(0f, 0f), Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f))
        val dst = arrayOf(Vector2F32.of(10f, 20f), Vector2F32.of(40f, 25f), Vector2F32.of(15f, 70f))

        val viaSet = Matrix3x3F32.setPolyToPoly(src, dst)!!
        val viaMake = Matrix3x3F32.MakePolyToPoly(src, dst)!!

        assertTrue(viaSet.cheapEqualTo(viaMake))
    }

    @Test
    fun `MakePolyToPoly 4 points unit square to skewed quad maps corners`() {
        // Source: unit square with corners (0,0), (1,0), (1,1), (0,1).
        val src = arrayOf(
            Vector2F32.of(0f, 0f), Vector2F32.of(1f, 0f), Vector2F32.of(1f, 1f), Vector2F32.of(0f, 1f),
        )
        // Destination: a skewed quad.
        val dst = arrayOf(
            Vector2F32.of(10f, 20f), Vector2F32.of(110f, 30f), Vector2F32.of(120f, 130f), Vector2F32.of(20f, 120f),
        )
        val m = Matrix3x3F32.MakePolyToPoly(src, dst)!!
        for (i in 0 until 4) {
            val mapped = m.mapXY(src[i])
            assertNear(dst[i].x, mapped.x, eps = 1e-2f, msg = "corner $i x")
            assertNear(dst[i].y, mapped.y, eps = 1e-2f, msg = "corner $i y")
        }
    }

    @Test
    fun `MakePolyToPoly 3 points is general affine`() {
        // 3 non-collinear points.
        val src = arrayOf(Vector2F32.of(0f, 0f), Vector2F32.of(1f, 0f), Vector2F32.of(0f, 1f))
        val dst = arrayOf(Vector2F32.of(10f, 20f), Vector2F32.of(40f, 25f), Vector2F32.of(15f, 70f))
        val m = Matrix3x3F32.MakePolyToPoly(src, dst)!!
        assertFalse(m.hasPerspective(), "3-point fit should be affine")
        for (i in 0 until 3) {
            val mapped = m.mapXY(src[i])
            assertNear(dst[i].x, mapped.x, eps = 1e-3f)
            assertNear(dst[i].y, mapped.y, eps = 1e-3f)
        }
    }

    @Test
    fun `MakePolyToPoly mismatched sizes returns null`() {
        assertEquals(null, Matrix3x3F32.MakePolyToPoly(
            arrayOf(Vector2F32.of(0f, 0f)),
            arrayOf(Vector2F32.of(1f, 1f), Vector2F32.of(2f, 2f)),
        ))
    }

    @Test
    fun `MakePolyToPoly more than 4 points returns null`() {
        val src = Array(5) { Vector2F32.Zero }
        val dst = Array(5) { Vector2F32.Zero }
        assertEquals(null, Matrix3x3F32.MakePolyToPoly(src, dst))
    }

    // ─── Phase 5: post* family ──────────────────────────────────────────

    @Test
    fun `postTranslate affine adds dx dy directly`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val post = m.postTranslate(10f, 20f)
        // For affine, postTranslate just bumps tx/ty.
        assertEquals(m.tx + 10f, post.tx); assertEquals(m.ty + 20f, post.ty)
        // Other fields unchanged.
        assertEquals(m.sx, post.sx); assertEquals(m.kx, post.kx)
        assertEquals(m.ky, post.ky); assertEquals(m.sy, post.sy)
    }

    @Test
    fun `postTranslate matches T concat M for any matrix`() {
        // Identity check: postTranslate(a, b) = T(a, b) · M.
        val m = Matrix3x3F32.MakeRotate(30f).preScale(2f, 3f).preTranslate(5f, 7f)
        val viaPost = m.postTranslate(11f, 13f)
        val viaConcat = Matrix3x3F32.concat(Matrix3x3F32.MakeTrans(11f, 13f), m)
        for ((a, b) in listOf(viaPost.sx to viaConcat.sx, viaPost.kx to viaConcat.kx,
            viaPost.tx to viaConcat.tx, viaPost.ky to viaConcat.ky,
            viaPost.sy to viaConcat.sy, viaPost.ty to viaConcat.ty)) {
            assertNear(b, a, eps = 1e-4f)
        }
    }

    @Test
    fun `postTranslate perspective uses postConcat`() {
        // For perspective M, postTranslate must dispatch through postConcat
        // because T · M also affects the persp row terms in column 2.
        val m = Matrix3x3F32.MakePerspective(0.5f, 0f).preTranslate(2f, 3f)
        val viaPost = m.postTranslate(11f, 13f)
        val viaConcat = Matrix3x3F32.concat(Matrix3x3F32.MakeTrans(11f, 13f), m)
        assertNear(viaConcat.tx, viaPost.tx); assertNear(viaConcat.ty, viaPost.ty)
        assertNear(viaConcat.persp2, viaPost.persp2, eps = 1e-4f)
    }

    @Test
    fun `postScale closed form matches postConcat`() {
        val m = Matrix3x3F32.MakeRotate(45f).preTranslate(5f, 7f)
        val viaPost = m.postScale(2f, 3f)
        val viaConcat = Matrix3x3F32.concat(Matrix3x3F32.MakeScale(2f, 3f), m)
        assertNear(viaConcat.sx, viaPost.sx, eps = 1e-4f)
        assertNear(viaConcat.kx, viaPost.kx, eps = 1e-4f)
        assertNear(viaConcat.tx, viaPost.tx, eps = 1e-4f)
        assertNear(viaConcat.ky, viaPost.ky, eps = 1e-4f)
        assertNear(viaConcat.sy, viaPost.sy, eps = 1e-4f)
        assertNear(viaConcat.ty, viaPost.ty, eps = 1e-4f)
    }

    @Test
    fun `postScale unity short-circuit returns same matrix`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f)
        // (sx_, sy_) == (1, 1) ⇒ short-circuit.
        assertEquals(m, m.postScale(1f, 1f))
    }

    @Test
    fun `postScale pivoted fixes pivot in device space`() {
        val m = Matrix3x3F32.MakeTrans(0f, 0f)
        val post = m.postScale(2f, 3f, 5f, 7f)
        // Pivot (5, 7) in device space stays at (5, 7).
        val (x, y) = post.mapXY(5f, 7f)
        assertNear(5f, x); assertNear(7f, y)
    }

    @Test
    fun `postRotate matches R concat M`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val viaPost = m.postRotate(45f)
        val viaConcat = Matrix3x3F32.concat(Matrix3x3F32.MakeRotate(45f), m)
        assertNear(viaConcat.sx, viaPost.sx, eps = 1e-4f)
        assertNear(viaConcat.ty, viaPost.ty, eps = 1e-4f)
    }

    @Test
    fun `postRotate pivoted anchors at pivot`() {
        val m = Matrix3x3F32.Identity
        val post = m.postRotate(90f, 10f, 20f)
        // Pivot stays fixed.
        val (x, y) = post.mapXY(10f, 20f)
        assertNear(10f, x); assertNear(20f, y)
    }

    @Test
    fun `postSkew matches Skew concat M`() {
        val m = Matrix3x3F32.MakeScale(2f, 3f).preTranslate(5f, 7f)
        val viaPost = m.postSkew(0.5f, 0f)
        val viaConcat = Matrix3x3F32.concat(Matrix3x3F32.MakeSkew(0.5f, 0f), m)
        assertNear(viaConcat.sx, viaPost.sx, eps = 1e-4f)
        assertNear(viaConcat.ty, viaPost.ty, eps = 1e-4f)
    }

    // ─── Phase 5: perspective-correct pre* ──────────────────────────────

    @Test
    fun `preTranslate perspective dispatches through preConcat`() {
        val m = Matrix3x3F32.MakePerspective(0.5f, 0f)
        val viaPre = m.preTranslate(3f, 5f)
        val viaConcat = Matrix3x3F32.concat(m, Matrix3x3F32.MakeTrans(3f, 5f))
        // Includes the persp2 update which the affine fast path would miss.
        assertNear(viaConcat.tx, viaPre.tx, eps = 1e-4f)
        assertNear(viaConcat.ty, viaPre.ty, eps = 1e-4f)
        assertNear(viaConcat.persp2, viaPre.persp2, eps = 1e-4f)
    }

    @Test
    fun `preScale perspective propagates to persp0 and persp1`() {
        // M · S: scales the perspective row's x and y components by sx_ and sy_.
        val m = Matrix3x3F32.MakePerspective(0.5f, 0.25f)
        val pre = m.preScale(2f, 3f)
        assertNear(0.5f * 2f, pre.persp0); assertNear(0.25f * 3f, pre.persp1)
        // persp2 unchanged on preScale (column 2 of M times scale's column 2 = unchanged).
        assertEquals(m.persp2, pre.persp2)
    }

    @Test
    fun `preScale on affine leaves persp row at (0, 0, 1)`() {
        val m = Matrix3x3F32.MakeRotate(30f).preTranslate(5f, 7f)
        val pre = m.preScale(2f, 3f)
        assertEquals(0f, pre.persp0); assertEquals(0f, pre.persp1); assertEquals(1f, pre.persp2)
    }

    // ─── Phase 5: cosmetic aliases ──────────────────────────────────────

    @Test
    fun `I returns Identity`() {
        assertEquals(Matrix3x3F32.Identity, Matrix3x3F32.I())
    }

    @Test
    fun `MakeTrans Vector2F32 overload matches dx dy form`() {
        val m1 = Matrix3x3F32.MakeTrans(Vector2F32.of(5f, 7f))
        val m2 = Matrix3x3F32.MakeTrans(5f, 7f)
        assertEquals(m1, m2)
    }

    @Test
    fun `operator times equals concat`() {
        val a = Matrix3x3F32.MakeRotate(30f)
        val b = Matrix3x3F32.MakeScale(2f, 3f)
        assertEquals(Matrix3x3F32.concat(a, b), a * b)
    }
}
