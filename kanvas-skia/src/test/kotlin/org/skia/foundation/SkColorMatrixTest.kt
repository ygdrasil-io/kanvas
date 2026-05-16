package org.skia.foundation


import org.graphiks.math.SkColorMatrix
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkColorMatrix] — mirrors the C++ contract from
 * [`include/effects/SkColorMatrix.h`](https://github.com/google/skia/blob/main/include/effects/SkColorMatrix.h)
 * (`src/effects/SkColorMatrix.cpp`).
 */
class SkColorMatrixTest {

    private fun identityFloats() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun rowMajorOf(cm: SkColorMatrix): FloatArray =
        FloatArray(20).also { cm.getRowMajor(it) }

    /** Apply this 4×5 matrix to a (R,G,B,A) color and return the result. */
    private fun apply(cm: SkColorMatrix, r: Float, g: Float, b: Float, a: Float): FloatArray {
        val m = rowMajorOf(cm)
        val out = FloatArray(4)
        for (row in 0..3) {
            val o = row * 5
            out[row] = m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 3] * a + m[o + 4]
        }
        return out
    }

    @Test
    fun `default ctor produces identity`() {
        val cm = SkColorMatrix()
        assertArrayEquals(identityFloats(), rowMajorOf(cm), 0f)
    }

    @Test
    fun `setIdentity overwrites previous state`() {
        val cm = SkColorMatrix()
        cm.setScale(2f, 3f, 4f, 5f)
        cm.setIdentity()
        assertArrayEquals(identityFloats(), rowMajorOf(cm), 0f)
    }

    @Test
    fun `setScale produces diagonal-only matrix`() {
        val cm = SkColorMatrix()
        cm.setScale(2f, 3f, 4f, 0.5f)
        val expected = floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 3f, 0f, 0f, 0f,
            0f, 0f, 4f, 0f, 0f,
            0f, 0f, 0f, 0.5f, 0f,
        )
        assertArrayEquals(expected, rowMajorOf(cm), 0f)
    }

    @Test
    fun `postTranslate accumulates onto bias column`() {
        val cm = SkColorMatrix()
        cm.postTranslate(10f, 20f, 30f, 0f)
        cm.postTranslate(1f, 2f, 3f, 4f)
        val out = apply(cm, 0.5f, 0.5f, 0.5f, 1f)
        assertEquals(0.5f + 11f, out[0], 1e-5f)
        assertEquals(0.5f + 22f, out[1], 1e-5f)
        assertEquals(0.5f + 33f, out[2], 1e-5f)
        assertEquals(1f + 4f, out[3], 1e-5f)
    }

    @Test
    fun `RGB2YUV then YUV2RGB applied to color returns the original`() {
        val toYuv = SkColorMatrix().apply { setRGB2YUV() }
        val toRgb = SkColorMatrix().apply { setYUV2RGB() }

        // Apply toYuv on a color, then toRgb on the result — should round-trip.
        val r = 0.4f; val g = 0.6f; val b = 0.8f; val a = 1f
        val yuv = apply(toYuv, r, g, b, a)
        val rgb = apply(toRgb, yuv[0], yuv[1], yuv[2], yuv[3])
        assertEquals(r, rgb[0], 1e-3f)
        assertEquals(g, rgb[1], 1e-3f)
        assertEquals(b, rgb[2], 1e-3f)
        assertEquals(a, rgb[3], 1e-3f)
    }

    @Test
    fun `setSaturation 1 keeps RGB unchanged for non-grey colors`() {
        val cm = SkColorMatrix(); cm.setSaturation(1f)
        val out = apply(cm, 0.2f, 0.5f, 0.9f, 1f)
        // sat = 1 → R + sat row reduces to (1, 0, 0); other rows similar. RGB pass through.
        assertEquals(0.2f, out[0], 1e-5f)
        assertEquals(0.5f, out[1], 1e-5f)
        assertEquals(0.9f, out[2], 1e-5f)
        assertEquals(1f, out[3], 1e-5f)
    }

    @Test
    fun `setSaturation 0 produces grayscale across all RGB channels`() {
        val cm = SkColorMatrix(); cm.setSaturation(0f)
        // At sat=0, each output channel is the same luma : 0.213·R + 0.715·G + 0.072·B
        val out = apply(cm, 0.4f, 0.5f, 0.6f, 1f)
        val luma = 0.213f * 0.4f + 0.715f * 0.5f + 0.072f * 0.6f
        assertEquals(luma, out[0], 1e-5f)
        assertEquals(luma, out[1], 1e-5f)
        assertEquals(luma, out[2], 1e-5f)
        assertEquals(1f, out[3], 1e-5f)
    }

    @Test
    fun `setRowMajor and getRowMajor round-trip 20 floats verbatim`() {
        val src = FloatArray(20) { (it + 1).toFloat() }
        val cm = SkColorMatrix()
        cm.setRowMajor(src)
        val out = FloatArray(20); cm.getRowMajor(out)
        assertArrayEquals(src, out, 0f)
    }

    @Test
    fun `20-float constructor takes values verbatim`() {
        val src = FloatArray(20) { (it + 1).toFloat() * 0.1f }
        val cm = SkColorMatrix(src)
        assertArrayEquals(src, rowMajorOf(cm), 0f)
    }

    @Test
    fun `preConcat then postConcat compose correctly`() {
        // A = scale(2,3,4,1), B = scale(0.5, 1, 1, 1).
        // postConcat(other) means result = other · this.
        // preConcat(other) means result = this · other.
        val a = SkColorMatrix().apply { setScale(2f, 3f, 4f, 1f) }
        val b = SkColorMatrix().apply { setScale(0.5f, 1f, 1f, 1f) }

        val ab = SkColorMatrix().apply { setScale(2f, 3f, 4f, 1f); preConcat(b) }
        val ba = SkColorMatrix().apply { setScale(2f, 3f, 4f, 1f); postConcat(b) }

        // preConcat : R scaled by 2 then 0.5 ⇒ 1.0
        // postConcat: R scaled by 0.5 then 2 ⇒ 1.0 (commutative diag in this case)
        val outAB = apply(ab, 0.5f, 0.5f, 0.5f, 1f)
        val outBA = apply(ba, 0.5f, 0.5f, 0.5f, 1f)
        assertEquals(0.5f * 1f, outAB[0], 1e-5f)
        assertEquals(0.5f * 1f, outBA[0], 1e-5f)
        assertEquals(0.5f * 3f, outAB[1], 1e-5f)
        assertEquals(0.5f * 4f, outAB[2], 1e-5f)
    }

    @Test
    fun `times operator returns a fresh matrix without mutating operands`() {
        val a = SkColorMatrix().apply { setScale(2f, 1f, 1f, 1f) }
        val b = SkColorMatrix().apply { setScale(3f, 1f, 1f, 1f) }
        val before = rowMajorOf(a)
        val c = a * b
        // a · b applied to R channel : 2 then 3? times is post-concat semantics : c = a then b ⇒ apply a first, then b
        // For diagonal scales it doesn't matter — but check that a wasn't mutated.
        assertArrayEquals(before, rowMajorOf(a), 0f)
        val out = apply(c, 0.5f, 0.5f, 0.5f, 1f)
        assertEquals(0.5f * 6f, out[0], 1e-5f)
    }

    @Test
    fun `equals and hashCode reflect contents`() {
        val a = SkColorMatrix()
        val b = SkColorMatrix()
        assertEquals(a, b); assertEquals(a.hashCode(), b.hashCode())
        b.setScale(2f, 2f, 2f, 2f)
        assertNotEquals(a, b)
    }
}
