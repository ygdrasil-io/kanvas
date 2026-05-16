package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.math.SkColor4f
import kotlin.math.abs

/**
 * D2.4.c.5 verification suite for [SkBuiltinShaderEffectsIntrinsicsMatrix].
 *
 * Pins :
 *  1. Hash resolution for all 6 entries (compMult × 3 dims +
 *     inverse × 3 dims).
 *  2. `selN(x)` partitioning function.
 *  3. `matrixInverse(...)` correctness on small matrices vs
 *     hand-computed inverses.
 *  4. End-to-end shader sampling — feed a known matrix, sample
 *     at a known `(p.x, p.y)`, verify the output cell value.
 */
class SkBuiltinShaderEffectsIntrinsicsMatrixTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinShaderEffectsIntrinsicsMatrix.registerAll()
    }

    @Test
    fun `every matrix hash resolves`() {
        for (dim in 2..4) {
            val compMult = SkBuiltinShaderEffectsIntrinsicsMatrix.makeMatrixCompMultSksl(dim)
            assertNotNull(SkRuntimeEffect.MakeForShader(compMult).effect,
                "compMult(${dim}x$dim) should resolve")

            val inverse = SkBuiltinShaderEffectsIntrinsicsMatrix.makeMatrixInverseSksl(dim)
            assertNotNull(SkRuntimeEffect.MakeForShader(inverse).effect,
                "inverse(${dim}x$dim) should resolve")
        }
    }

    @Test
    fun `selN partitioning matches upstream literals`() {
        // sel2 — split at 0.5
        assertEquals(0, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.4f, 2))
        assertEquals(1, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.5f, 2))
        assertEquals(1, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.99f, 2))

        // sel3 — split at 0.33 / 0.66 (literal, not exact 1/3)
        assertEquals(0, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.32f, 3))
        assertEquals(1, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.33f, 3))
        assertEquals(1, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.65f, 3))
        assertEquals(2, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.66f, 3))

        // sel4 — split at 0.25 / 0.5 / 0.75
        assertEquals(0, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.0f, 4))
        assertEquals(1, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.25f, 4))
        assertEquals(2, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.5f, 4))
        assertEquals(3, SkBuiltinShaderEffectsIntrinsicsMatrix.selN(0.75f, 4))
    }

    @Test
    fun `matrixInverse 2x2 reproduces hand-computed result`() {
        // m = [[1, 2], [3, 4]] in column-major: [1, 3, 2, 4]
        // det = 1*4 - 2*3 = -2
        // inv = (1/-2) * [[4, -2], [-3, 1]] = [[-2, 1], [1.5, -0.5]]
        // column-major: [-2, 1.5, 1, -0.5]
        val m = floatArrayOf(1f, 3f, 2f, 4f)
        val inv = SkBuiltinShaderEffectsIntrinsicsMatrix.matrixInverse(m, 2)
        assertNotNull(inv)
        assertNearly(-2f, inv!![0])
        assertNearly(1.5f, inv[1])
        assertNearly(1f, inv[2])
        assertNearly(-0.5f, inv[3])
    }

    @Test
    fun `matrixInverse identity returns identity`() {
        val id = floatArrayOf(1f, 0f, 0f, 1f)
        val inv = SkBuiltinShaderEffectsIntrinsicsMatrix.matrixInverse(id, 2)
        assertNotNull(inv)
        assertNearly(1f, inv!![0])
        assertNearly(0f, inv[1])
        assertNearly(0f, inv[2])
        assertNearly(1f, inv[3])
    }

    @Test
    fun `matrixInverse rejects singular matrix`() {
        // [[1, 2], [2, 4]] — rank 1 (rows linearly dependent)
        val singular = floatArrayOf(1f, 2f, 2f, 4f)
        assertNull(SkBuiltinShaderEffectsIntrinsicsMatrix.matrixInverse(singular, 2))
    }

    @Test
    fun `matrixInverse 3x3 round-trip — A * A_inv ≈ I`() {
        // Random invertible matrix ; verify A * inv(A) ≈ I.
        val a = floatArrayOf(
            -1.13f, -2.96f, -0.14f,  // column 0
            1.45f, -1.88f, -1.02f,   // column 1
            -2.54f, -2.58f, -1.17f,  // column 2
        )
        val inv = SkBuiltinShaderEffectsIntrinsicsMatrix.matrixInverse(a, 3)
        assertNotNull(inv)

        // Compute A * inv(A) — column-major: result[col][row].
        // result[col, row] = Σ_k A[k, row] * inv[col, k]
        for (col in 0 until 3) {
            for (row in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) {
                    sum += a[k * 3 + row] * inv!![col * 3 + k]
                }
                val expected = if (col == row) 1f else 0f
                assertTrue(abs(sum - expected) < 1e-3f,
                    "A * inv(A) at ($col, $row) = $sum ; expected $expected")
            }
        }
    }

    @Test
    fun `compMult shader at center returns m1_k_l times m2_k_l`() {
        // 2×2 matrices, sample at (0.25, 0.75) → k=0, l=1.
        // m1[0][1] = m1 column-major[0*2+1] = m1[1] ; m2[1] same.
        val m1 = floatArrayOf(1f, 3f, 2f, 4f)   // m1 col 0 = (1, 3) col 1 = (2, 4)
        val m2 = floatArrayOf(0.5f, 0.25f, 2f, 1f)
        val sksl = SkBuiltinShaderEffectsIntrinsicsMatrix.makeMatrixCompMultSksl(2)
        val effect = SkRuntimeEffect.MakeForShader(sksl).effect!!
        val builder = SkRuntimeEffectBuilder(effect)
        builder.uniform("m1").set(m1)
        builder.uniform("m2").set(m2)
        val shader = builder.makeShader()!!
        val color = SkColor4f.FromColor(shader.sampleAtLocal(0.25f, 0.75f))
        // Expected : m1[0][1] * m2[0][1] = 3 * 0.25 = 0.75.
        assertNearly(0.75f, color.fR, msg = "compMult at (k=0, l=1)")
    }

    private fun assertNearly(
        expected: Float, actual: Float,
        tol: Float = 2f / 255f, msg: String = "",
    ) {
        assertTrue(
            abs(expected - actual) < tol,
            "$msg : expected=$expected actual=$actual diff=${abs(expected - actual)} tol=$tol",
        )
    }
}
