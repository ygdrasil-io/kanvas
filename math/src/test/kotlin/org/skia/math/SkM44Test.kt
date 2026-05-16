package org.skia.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class SkM44Test {

    private fun assertV4Near(expected: SkV4, actual: SkV4, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps, "x")
        assertEquals(expected.y, actual.y, eps, "y")
        assertEquals(expected.z, actual.z, eps, "z")
        assertEquals(expected.w, actual.w, eps, "w")
    }

    private fun assertMatrixNear(a: SkM44, b: SkM44, eps: Float = 1e-5f) {
        for (r in 0..3) for (c in 0..3) {
            assertEquals(a.rc(r, c), b.rc(r, c), eps, "rc($r,$c)")
        }
    }

    @Test
    fun `default constructor produces identity`() {
        val m = SkM44()
        assertTrue(m.isIdentity())
        for (r in 0..3) for (c in 0..3) {
            assertEquals(if (r == c) 1f else 0f, m.rc(r, c))
        }
    }

    @Test
    fun `identity factory matches default`() {
        assertEquals(SkM44(), SkM44.identity())
    }

    @Test
    fun `identity times vector returns vector`() {
        val v = SkV4(1f, 2f, 3f, 4f)
        assertV4Near(v, SkM44() * v)
    }

    @Test
    fun `translate factory shifts point`() {
        val t = SkM44.translate(10f, 20f, 30f)
        val mapped = t.map(1f, 2f, 3f, 1f)
        assertV4Near(SkV4(11f, 22f, 33f, 1f), mapped)
    }

    @Test
    fun `scale factory scales vector`() {
        val s = SkM44.scale(2f, 3f, 4f)
        assertV4Near(SkV4(2f, 6f, 12f, 1f), s.map(1f, 2f, 3f, 1f))
    }

    @Test
    fun `rotate Z 90 degrees maps unit-X to unit-Y`() {
        val r = SkM44.rotate(SkV3(0f, 0f, 1f), (PI / 2).toFloat())
        val out = r * SkV4(1f, 0f, 0f, 1f)
        assertV4Near(SkV4(0f, 1f, 0f, 1f), out, 1e-5f)
    }

    @Test
    fun `rotate falls back to identity for zero axis`() {
        val r = SkM44.rotate(SkV3(0f, 0f, 0f), 1f)
        assertTrue(r.isIdentity())
    }

    @Test
    fun `perspective produces non-affine matrix`() {
        val p = SkM44.perspective(1f, 100f, (PI / 4).toFloat())
        // Bottom row has -1 in column 2 — non-affine (would be [0,0,0,1] for affine).
        assertEquals(0f, p.rc(3, 0))
        assertEquals(0f, p.rc(3, 1))
        assertEquals(-1f, p.rc(3, 2))
        // Upstream leaves rc(3,3) at the default 1 (it never resets it).
        assertEquals(1f, p.rc(3, 3))
        // The perspective matrix has setRC(2, 3) = 2*near*far/(far-near) — non-zero
        assertNotEquals(0f, p.rc(2, 3))
    }

    @Test
    fun `invert of rotation equals its transpose`() {
        val r = SkM44.rotate(SkV3(1f, 2f, 3f).normalize(), 0.7f)
        val inv = r.invert()
        assertNotNull(inv)
        assertMatrixNear(r.transpose(), inv!!, 1e-4f)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        // Scale by zero on x → singular.
        val s = SkM44.scale(0f, 1f, 1f)
        assertNull(s.invert())
    }

    @Test
    fun `invert of identity is identity`() {
        val inv = SkM44().invert()
        assertNotNull(inv)
        assertMatrixNear(SkM44(), inv!!)
    }

    @Test
    fun `invert and multiply yields identity`() {
        val m = SkM44.translate(1f, 2f, 3f).also { it.preScale(2f, 3f, 4f) }
        val inv = m.invert()!!
        assertMatrixNear(SkM44(), m * inv, 1e-4f)
        assertMatrixNear(SkM44(), inv * m, 1e-4f)
    }

    @Test
    fun `transpose twice equals original`() {
        val m = SkM44(
            1f,  2f,  3f,  4f,
            5f,  6f,  7f,  8f,
            9f,  10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )
        assertMatrixNear(m, m.transpose().transpose())
    }

    @Test
    fun `row and col round-trip via setRow setCol`() {
        val m = SkM44(
            1f,  2f,  3f,  4f,
            5f,  6f,  7f,  8f,
            9f,  10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )
        for (i in 0..3) {
            val r = m.row(i)
            val c = m.col(i)
            val tmp = SkM44()
            tmp.setRow(i, r)
            assertV4Near(r, tmp.row(i))
            tmp.setCol(i, c)
            assertV4Near(c, tmp.col(i))
        }
    }

    @Test
    fun `rc and setRC interact correctly`() {
        val m = SkM44()
        m.setRC(2, 1, 7f)
        assertEquals(7f, m.rc(2, 1))
        // Column-major: row=2, col=1 → index 1*4 + 2 = 6
        assertEquals(7f, m.fMat[6])
    }

    @Test
    fun `preTranslate matches setConcat with Translate`() {
        val s = SkM44.scale(2f, 3f, 4f)
        val a = SkM44(s).also { it.preTranslate(5f, 6f, 7f) }
        val b = SkM44(s, SkM44.translate(5f, 6f, 7f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `postTranslate matches setConcat with Translate on the left`() {
        val s = SkM44.scale(2f, 3f, 4f)
        val a = SkM44(s).also { it.postTranslate(5f, 6f, 7f) }
        val b = SkM44(SkM44.translate(5f, 6f, 7f), s)
        assertMatrixNear(a, b)
    }

    @Test
    fun `preScale 2D matches setConcat with Scale 2D`() {
        val t = SkM44.translate(1f, 2f, 3f)
        val a = SkM44(t).also { it.preScale(2f, 3f) }
        val b = SkM44(t, SkM44.scale(2f, 3f, 1f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `preScale 3D matches setConcat with Scale 3D`() {
        val t = SkM44.translate(1f, 2f, 3f)
        val a = SkM44(t).also { it.preScale(2f, 3f, 4f) }
        val b = SkM44(t, SkM44.scale(2f, 3f, 4f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `multiplication is associative`() {
        val a = SkM44.translate(1f, 2f, 3f)
        val b = SkM44.scale(2f, 3f, 4f)
        val c = SkM44.rotate(SkV3(0f, 0f, 1f), 0.5f)
        assertMatrixNear((a * b) * c, a * (b * c), 1e-4f)
    }

    @Test
    fun `mapPoint matches SkMatrix when m44 is affine`() {
        val sk2D = SkMatrix.MakeAll(2f, 0f, 5f, 0f, 3f, 7f)
        val m44 = SkM44(sk2D)
        val p = SkPoint(4f, 8f)
        val viaM44 = m44.mapPoint(p)
        val viaMatrix = sk2D.mapXY(p)
        assertEquals(viaMatrix.fX, viaM44.fX, 1e-5f)
        assertEquals(viaMatrix.fY, viaM44.fY, 1e-5f)
    }

    @Test
    fun `mapRect on translate matches SkRect-shifted`() {
        val m = SkM44.translate(10f, 20f, 0f)
        val r = SkRect(0f, 0f, 4f, 6f)
        val out = m.mapRect(r)
        assertEquals(SkRect(10f, 20f, 14f, 26f), out)
    }

    @Test
    fun `mapRect on scale produces scaled bounds`() {
        val m = SkM44.scale(2f, 3f, 1f)
        val out = m.mapRect(SkRect(0f, 0f, 4f, 6f))
        assertEquals(SkRect(0f, 0f, 8f, 18f), out)
    }

    @Test
    fun `asM33 round-trip preserves affine fields`() {
        val src = SkMatrix.MakeAll(2f, 0f, 5f, 0f, 3f, 7f, 0f, 0f, 1f)
        val viaM44 = SkM44(src).asM33()
        assertNotNull(viaM44)
        // Match the affine subset (perspective row is [0, 0, 1] for affine).
        assertEquals(src.sx, viaM44!!.sx, 1e-5f)
        assertEquals(src.kx, viaM44.kx, 1e-5f)
        assertEquals(src.tx, viaM44.tx, 1e-5f)
        assertEquals(src.ky, viaM44.ky, 1e-5f)
        assertEquals(src.sy, viaM44.sy, 1e-5f)
        assertEquals(src.ty, viaM44.ty, 1e-5f)
        assertEquals(src.persp0, viaM44.persp0, 1e-5f)
        assertEquals(src.persp1, viaM44.persp1, 1e-5f)
        assertEquals(src.persp2, viaM44.persp2, 1e-5f)
    }

    @Test
    fun `setM33 reproduces SkMatrix constructor`() {
        val src = SkMatrix.MakeAll(2f, 1f, 5f, 0.5f, 3f, 7f)
        val fromCtor = SkM44(src)
        val viaSet = SkM44().setM33(src)
        assertMatrixNear(fromCtor, viaSet)
    }

    @Test
    fun `normalizePerspective scales rows so bottom-right becomes one`() {
        val m = SkM44(
            2f, 0f, 0f, 4f,
            0f, 4f, 0f, 8f,
            0f, 0f, 6f, 12f,
            0f, 0f, 0f, 2f,
        )
        m.normalizePerspective()
        assertEquals(1f, m.rc(3, 3), 1e-6f)
        assertEquals(1f, m.rc(0, 0), 1e-6f)
        assertEquals(2f, m.rc(0, 3), 1e-6f)
        assertEquals(3f, m.rc(2, 2), 1e-6f)
    }

    @Test
    fun `normalizePerspective is a no-op for perspective row not bottom-only`() {
        val m = SkM44(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            1f, 0f, 0f, 2f,
        )
        val before = SkM44(m)
        m.normalizePerspective()
        assertMatrixNear(before, m)
    }

    @Test
    fun `lookAt produces invertible matrix for typical eye`() {
        val v = SkM44.lookAt(
            eye = SkV3(0f, 0f, 5f),
            center = SkV3(0f, 0f, 0f),
            up = SkV3(0f, 1f, 0f),
        )
        // The Skia look-at is the inverse of [right, up, -fwd, eye]; it
        // should be a valid affine view matrix (finite, invertible).
        assertTrue(v.isFinite())
        assertNotNull(v.invert())
    }

    @Test
    fun `rectToRect maps src corners onto dst corners`() {
        val src = SkRect(0f, 0f, 2f, 4f)
        val dst = SkRect(10f, 20f, 14f, 28f)
        val m = SkM44.rectToRect(src, dst)
        assertV4Near(SkV4(10f, 20f, 0f, 1f), m.map(0f, 0f, 0f, 1f))
        assertV4Near(SkV4(14f, 28f, 0f, 1f), m.map(2f, 4f, 0f, 1f))
    }

    @Test
    fun `rectToRect returns identity for empty src`() {
        val empty = SkRect(0f, 0f, 0f, 0f)
        val dst = SkRect(1f, 2f, 3f, 4f)
        assertTrue(SkM44.rectToRect(empty, dst).isIdentity())
    }

    @Test
    fun `equals and hashCode match by content`() {
        val a = SkM44.translate(1f, 2f, 3f)
        val b = SkM44.translate(1f, 2f, 3f)
        val c = SkM44.translate(1f, 2f, 0f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `copy constructor produces independent backing array`() {
        val a = SkM44.translate(1f, 2f, 3f)
        val b = SkM44(a)
        b.setRC(0, 3, 999f)
        assertEquals(1f, a.rc(0, 3))
        assertEquals(999f, b.rc(0, 3))
    }

    @Test
    fun `getColMajor and getRowMajor produce transposed layouts`() {
        val m = SkM44(
            1f,  2f,  3f,  4f,
            5f,  6f,  7f,  8f,
            9f,  10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )
        val colBuf = FloatArray(16)
        val rowBuf = FloatArray(16)
        m.getColMajor(colBuf)
        m.getRowMajor(rowBuf)
        // First column (col=0) in col-major == first row (col 0..3 of row 0) in row-major.
        for (i in 0..3) {
            assertEquals(colBuf[i], rowBuf[i * 4])
        }
    }

    @Test
    fun `isFinite catches NaN and Inf`() {
        val m = SkM44()
        assertTrue(m.isFinite())
        m.setRC(0, 0, Float.NaN)
        assertFalse(m.isFinite())
        m.setRC(0, 0, Float.POSITIVE_INFINITY)
        assertFalse(m.isFinite())
    }

    @Test
    fun `times SkV3 drops translation`() {
        val m = SkM44.translate(10f, 20f, 30f)
        val v = SkV3(1f, 2f, 3f)
        assertEquals(SkV3(1f, 2f, 3f), m * v)
    }
}
