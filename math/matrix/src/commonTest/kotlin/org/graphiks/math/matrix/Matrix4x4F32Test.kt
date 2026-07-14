package org.graphiks.math.matrix

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.PI
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.graphiks.math.vector.Vector4F32

class Matrix4x4F32Test {

    private fun assertV4Near(expected: Vector4F32, actual: Vector4F32, eps: Float = 1e-5f) {
        assertEquals(expected.x, actual.x, eps, "x")
        assertEquals(expected.y, actual.y, eps, "y")
        assertEquals(expected.z, actual.z, eps, "z")
        assertEquals(expected.w, actual.w, eps, "w")
    }

    private fun assertMatrixNear(a: Matrix4x4F32, b: Matrix4x4F32, eps: Float = 1e-5f) {
        for (r in 0..3) for (c in 0..3) {
            assertEquals(a.rc(r, c), b.rc(r, c), eps, "rc($r,$c)")
        }
    }

    @Test
    fun `default constructor produces identity`() {
        val m = Matrix4x4F32()
        assertTrue(m.isIdentity())
        for (r in 0..3) for (c in 0..3) {
            assertEquals(if (r == c) 1f else 0f, m.rc(r, c))
        }
    }

    @Test
    fun `identity factory matches default`() {
        assertEquals(Matrix4x4F32(), Matrix4x4F32.identity())
    }

    @Test
    fun `identity times vector returns vector`() {
        val v = Vector4F32.of(1f, 2f, 3f, 4f)
        assertV4Near(v, Matrix4x4F32() * v)
    }

    @Test
    fun `translate factory shifts point`() {
        val t = Matrix4x4F32.translate(10f, 20f, 30f)
        val mapped = t.map(1f, 2f, 3f, 1f)
        assertV4Near(Vector4F32.of(11f, 22f, 33f, 1f), mapped)
    }

    @Test
    fun `scale factory scales vector`() {
        val s = Matrix4x4F32.scale(2f, 3f, 4f)
        assertV4Near(Vector4F32.of(2f, 6f, 12f, 1f), s.map(1f, 2f, 3f, 1f))
    }

    @Test
    fun `rotate Z 90 degrees maps unit-X to unit-Y`() {
        val r = Matrix4x4F32.rotate(Vector3F32.of(0f, 0f, 1f), (PI / 2).toFloat())
        val out = r * Vector4F32.of(1f, 0f, 0f, 1f)
        assertV4Near(Vector4F32.of(0f, 1f, 0f, 1f), out, 1e-5f)
    }

    @Test
    fun `rotate falls back to identity for zero axis`() {
        val r = Matrix4x4F32.rotate(Vector3F32.of(0f, 0f, 0f), 1f)
        assertTrue(r.isIdentity())
    }

    @Test
    fun `perspective produces non-affine matrix`() {
        val p = Matrix4x4F32.perspective(1f, 100f, (PI / 4).toFloat())
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
        val r = Matrix4x4F32.rotate(Vector3F32.of(1f, 2f, 3f).normalize(), 0.7f)
        val inv = r.invert()
        assertNotNull(inv)
        assertMatrixNear(r.transpose(), inv!!, 1e-4f)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        // Scale by zero on x → singular.
        val s = Matrix4x4F32.scale(0f, 1f, 1f)
        assertNull(s.invert())
    }

    @Test
    fun `invert of identity is identity`() {
        val inv = Matrix4x4F32().invert()
        assertNotNull(inv)
        assertMatrixNear(Matrix4x4F32(), inv!!)
    }

    @Test
    fun `invert and multiply yields identity`() {
        val m = Matrix4x4F32.translate(1f, 2f, 3f).also { it.preScale(2f, 3f, 4f) }
        val inv = m.invert()!!
        assertMatrixNear(Matrix4x4F32(), m * inv, 1e-4f)
        assertMatrixNear(Matrix4x4F32(), inv * m, 1e-4f)
    }

    @Test
    fun `transpose twice equals original`() {
        val m = Matrix4x4F32(
            1f,  2f,  3f,  4f,
            5f,  6f,  7f,  8f,
            9f,  10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )
        assertMatrixNear(m, m.transpose().transpose())
    }

    @Test
    fun `row and col round-trip via setRow setCol`() {
        val m = Matrix4x4F32(
            1f,  2f,  3f,  4f,
            5f,  6f,  7f,  8f,
            9f,  10f, 11f, 12f,
            13f, 14f, 15f, 16f,
        )
        for (i in 0..3) {
            val r = m.row(i)
            val c = m.col(i)
            val tmp = Matrix4x4F32()
            tmp.setRow(i, r)
            assertV4Near(r, tmp.row(i))
            tmp.setCol(i, c)
            assertV4Near(c, tmp.col(i))
        }
    }

    @Test
    fun `rc and setRC interact correctly`() {
        val m = Matrix4x4F32()
        m.setRC(2, 1, 7f)
        assertEquals(7f, m.rc(2, 1))
        assertEquals(7f, m[2, 1])
    }

    @Test
    fun `preTranslate matches setConcat with Translate`() {
        val s = Matrix4x4F32.scale(2f, 3f, 4f)
        val a = Matrix4x4F32(s).also { it.preTranslate(5f, 6f, 7f) }
        val b = Matrix4x4F32(s, Matrix4x4F32.translate(5f, 6f, 7f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `postTranslate matches setConcat with Translate on the left`() {
        val s = Matrix4x4F32.scale(2f, 3f, 4f)
        val a = Matrix4x4F32(s).also { it.postTranslate(5f, 6f, 7f) }
        val b = Matrix4x4F32(Matrix4x4F32.translate(5f, 6f, 7f), s)
        assertMatrixNear(a, b)
    }

    @Test
    fun `preScale 2D matches setConcat with Scale 2D`() {
        val t = Matrix4x4F32.translate(1f, 2f, 3f)
        val a = Matrix4x4F32(t).also { it.preScale(2f, 3f) }
        val b = Matrix4x4F32(t, Matrix4x4F32.scale(2f, 3f, 1f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `preScale 3D matches setConcat with Scale 3D`() {
        val t = Matrix4x4F32.translate(1f, 2f, 3f)
        val a = Matrix4x4F32(t).also { it.preScale(2f, 3f, 4f) }
        val b = Matrix4x4F32(t, Matrix4x4F32.scale(2f, 3f, 4f))
        assertMatrixNear(a, b)
    }

    @Test
    fun `multiplication is associative`() {
        val a = Matrix4x4F32.translate(1f, 2f, 3f)
        val b = Matrix4x4F32.scale(2f, 3f, 4f)
        val c = Matrix4x4F32.rotate(Vector3F32.of(0f, 0f, 1f), 0.5f)
        assertMatrixNear((a * b) * c, a * (b * c), 1e-4f)
    }

    @Test
    fun `mapPoint matches Matrix3x3F32 when m44 is affine`() {
        val sk2D = Matrix3x3F32.of(2f, 0f, 5f, 0f, 3f, 7f)
        val m44 = Matrix4x4F32(sk2D)
        val p = Vector2F32.of(4f, 8f)
        val viaM44 = m44.mapPoint(p)
        val viaMatrix = sk2D.mapXY(p)
        assertEquals(viaMatrix.x, viaM44.x, 1e-5f)
        assertEquals(viaMatrix.y, viaM44.y, 1e-5f)
    }

    @Test
    fun `mapRect on translate matches RectF32-shifted`() {
        val m = Matrix4x4F32.translate(10f, 20f, 0f)
        val r = RectF32(0f, 0f, 4f, 6f)
        val out = m.mapRect(r)
        assertEquals(RectF32(10f, 20f, 14f, 26f), out)
    }

    @Test
    fun `mapRect on scale produces scaled bounds`() {
        val m = Matrix4x4F32.scale(2f, 3f, 1f)
        val out = m.mapRect(RectF32(0f, 0f, 4f, 6f))
        assertEquals(RectF32(0f, 0f, 8f, 18f), out)
    }

    @Test
    fun `mapRect identity returns rect unchanged`() {
        val m = Matrix4x4F32()
        val r = RectF32(-1.5f, 2f, 3.25f, 7.5f)
        val out = m.mapRect(r)
        assertEquals(r, out)
    }

    @Test
    fun `mapRect with extreme perspective clips w less or equal 0 instead of NaN or Inf`() {
        // Audit divergence #5: upstream `map_rect_perspective` clips
        // corners with w < kW0PlaneDistance against the w-plane;
        // the previous Kotlin implementation projected every corner
        // and produced Inf/NaN whenever one of them was at or behind
        // the camera. Build a matrix that places a corner exactly
        // there: row 3 = (1, 0, 0, -0.5) maps `x = 0.5` to `w = 0`.
        val m = Matrix4x4F32(
            1f, 0f, 0f,  0f,
            0f, 1f, 0f,  0f,
            0f, 0f, 1f,  0f,
            1f, 0f, 0f, -0.5f, // bottom row (perspective): w = x - 0.5
        )
        // The right edge x = 0.5 collapses to w = 0; the right edge
        // x = 2 has w = 1.5; the left edge x = -1 has w = -1.5.
        val r = RectF32(-1f, -1f, 2f, 1f)
        val out = m.mapRect(r)
        assertTrue(out.left.isFinite(),    "left was ${out.left}")
        assertTrue(out.top.isFinite(),     "top was ${out.top}")
        assertTrue(out.right.isFinite(),   "right was ${out.right}")
        assertTrue(out.bottom.isFinite(),  "bottom was ${out.bottom}")
        assertFalse(out.left.isNaN());   assertFalse(out.right.isNaN())
        assertFalse(out.top.isNaN());    assertFalse(out.bottom.isNaN())
    }

    @Test
    fun `mapRect with mild perspective gives finite bounds matching projected corners`() {
        // All four corners have w well above kW0PlaneDistance ⇒ the
        // result is just min/max of (x/w, y/w) per corner, identical
        // to the old behaviour.
        val m = Matrix4x4F32(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0.1f, 0.2f, 0f, 1f, // bottom row: w = 1 + 0.1x + 0.2y
        )
        val r = RectF32(0f, 0f, 2f, 4f)
        val out = m.mapRect(r)

        // Reference: project each corner manually.
        fun proj(x: Float, y: Float): Pair<Float, Float> {
            val w = 0.1f * x + 0.2f * y + 1f
            return Pair(x / w, y / w)
        }
        val (x0, y0) = proj(0f, 0f)
        val (x1, y1) = proj(2f, 0f)
        val (x2, y2) = proj(0f, 4f)
        val (x3, y3) = proj(2f, 4f)
        assertEquals(minOf(x0, x1, x2, x3), out.left,   1e-5f)
        assertEquals(minOf(y0, y1, y2, y3), out.top,    1e-5f)
        assertEquals(maxOf(x0, x1, x2, x3), out.right,  1e-5f)
        assertEquals(maxOf(y0, y1, y2, y3), out.bottom, 1e-5f)
    }

    @Test
    fun `asM33 round-trip preserves affine fields`() {
        val src = Matrix3x3F32.of(2f, 0f, 5f, 0f, 3f, 7f, 0f, 0f, 1f)
        val viaM44 = Matrix4x4F32(src).asM33()
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
    fun `setM33 reproduces Matrix3x3F32 constructor`() {
        val src = Matrix3x3F32.of(2f, 1f, 5f, 0.5f, 3f, 7f)
        val fromCtor = Matrix4x4F32(src)
        val viaSet = Matrix4x4F32().setM33(src)
        assertMatrixNear(fromCtor, viaSet)
    }

    @Test
    fun `normalizePerspective scales rows so bottom-right becomes one`() {
        val m = Matrix4x4F32(
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
        val m = Matrix4x4F32(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            1f, 0f, 0f, 2f,
        )
        val before = Matrix4x4F32(m)
        m.normalizePerspective()
        assertMatrixNear(before, m)
    }

    @Test
    fun `lookAt produces invertible matrix for typical eye`() {
        val v = Matrix4x4F32.lookAt(
            eye = Vector3F32.of(0f, 0f, 5f),
            center = Vector3F32.of(0f, 0f, 0f),
            up = Vector3F32.of(0f, 1f, 0f),
        )
        // The Skia look-at is the inverse of [right, up, -fwd, eye]; it
        // should be a valid affine view matrix (finite, invertible).
        assertTrue(v.isFinite())
        assertNotNull(v.invert())
    }

    @Test
    fun `rectToRect maps src corners onto dst corners`() {
        val src = RectF32(0f, 0f, 2f, 4f)
        val dst = RectF32(10f, 20f, 14f, 28f)
        val m = Matrix4x4F32.rectToRect(src, dst)
        assertV4Near(Vector4F32.of(10f, 20f, 0f, 1f), m.map(0f, 0f, 0f, 1f))
        assertV4Near(Vector4F32.of(14f, 28f, 0f, 1f), m.map(2f, 4f, 0f, 1f))
    }

    @Test
    fun `rectToRect returns identity for empty src`() {
        val empty = RectF32(0f, 0f, 0f, 0f)
        val dst = RectF32(1f, 2f, 3f, 4f)
        assertTrue(Matrix4x4F32.rectToRect(empty, dst).isIdentity())
    }

    @Test
    fun `equals and hashCode match by content`() {
        val a = Matrix4x4F32.translate(1f, 2f, 3f)
        val b = Matrix4x4F32.translate(1f, 2f, 3f)
        val c = Matrix4x4F32.translate(1f, 2f, 0f)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun `copy constructor produces independent backing array`() {
        val a = Matrix4x4F32.translate(1f, 2f, 3f)
        val b = Matrix4x4F32(a)
        b.setRC(0, 3, 999f)
        assertEquals(1f, a.rc(0, 3))
        assertEquals(999f, b.rc(0, 3))
    }

    @Test
    fun `getColMajor and getRowMajor produce transposed layouts`() {
        val m = Matrix4x4F32(
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
        val m = Matrix4x4F32()
        assertTrue(m.isFinite())
        m.setRC(0, 0, Float.NaN)
        assertFalse(m.isFinite())
        m.setRC(0, 0, Float.POSITIVE_INFINITY)
        assertFalse(m.isFinite())
    }

    @Test
    fun `times Vector3F32 drops translation`() {
        val m = Matrix4x4F32.translate(10f, 20f, 30f)
        val v = Vector3F32.of(1f, 2f, 3f)
        assertEquals(Vector3F32.of(1f, 2f, 3f), m * v)
    }
}
