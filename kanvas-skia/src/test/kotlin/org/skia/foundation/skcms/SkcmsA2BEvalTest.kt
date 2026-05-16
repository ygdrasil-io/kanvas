package org.skia.foundation.skcms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase F4 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify N-dimensional
 * CLUT eval, the A2B / B2A pipeline composition, and the
 * content-aware [SkcmsA2B] / [SkcmsB2A] equality.
 *
 * Ground-truth CLUT outputs come from a standalone C++ driver
 * (`tools/clut_test.cpp`) that mirrors the upstream `clut(...)` from
 * `Transform_inl.h:685-764` verbatim.
 */
class SkcmsA2BEvalTest {

    // ----- skcmsClut: 1D / 2D / 3D / 4D -----

    @Test
    fun `1D 4-entry RGB-grayscale ramp matches upstream ground truth`() {
        // Grid layout: 4 entries, each (R, G, B) bytes, ramp 0/64/128/255 on
        // all 3 channels.
        val grid = byteArrayOf(
            0, 0, 0,
            64, 64, 64,
            128.toByte(), 128.toByte(), 128.toByte(),
            255.toByte(), 255.toByte(), 255.toByte(),
        )
        for ((x, expected) in listOf(
            0.0f to 0.000000f,
            0.25f to 0.188235f,
            0.50f to 0.376471f,
            0.75f to 0.626471f,
            1.0f to 1.000000f,
            0.10f to 0.075294f,
        )) {
            val v = floatArrayOf(x, 0f, 0f, 0f)
            skcmsClut(
                inputChannels = 1, outputChannels = 3,
                gridPoints = intArrayOf(4, 0, 0, 0),
                grid8 = grid, grid16 = null, values = v,
            )
            assertNear(expected, v[0], 1e-5f, "x=$x R")
            assertNear(expected, v[1], 1e-5f, "x=$x G")
            assertNear(expected, v[2], 1e-5f, "x=$x B")
        }
    }

    @Test
    fun `3D 2x2x2 RGB identity cube interpolates linearly`() {
        // ICC convention: first input (R) varies slowest in storage.
        // ix = R*4 + G*2 + B for a 2^3 cube.
        val grid = ByteArray(8 * 3)
        for (r in 0..1) for (g in 0..1) for (b in 0..1) {
            val ix = r * 4 + g * 2 + b
            grid[ix * 3 + 0] = (r * 255).toByte()
            grid[ix * 3 + 1] = (g * 255).toByte()
            grid[ix * 3 + 2] = (b * 255).toByte()
        }
        // Ground-truth values from clut_test.cpp.
        for ((rgb, expected) in listOf(
            Triple(0f, 0f, 0f) to floatArrayOf(0f, 0f, 0f),
            Triple(1f, 0f, 0f) to floatArrayOf(1f, 0f, 0f),
            Triple(0.5f, 0.5f, 0.5f) to floatArrayOf(0.5f, 0.5f, 0.5f),
            Triple(0.25f, 0.75f, 0.5f) to floatArrayOf(0.25f, 0.75f, 0.5f),
        )) {
            val v = floatArrayOf(rgb.first, rgb.second, rgb.third, 0f)
            skcmsClut(
                inputChannels = 3, outputChannels = 3,
                gridPoints = intArrayOf(2, 2, 2, 0),
                grid8 = grid, grid16 = null, values = v,
            )
            for (i in 0..2) assertNear(expected[i], v[i], 1e-5f,
                "input=$rgb out[$i]")
        }
    }

    @Test
    fun `3D 2x2x2 RGB identity cube reads big-endian 16-bit grid`() {
        val grid = ByteArray(8 * 3 * 2)
        for (r in 0..1) for (g in 0..1) for (b in 0..1) {
            val ix = r * 4 + g * 2 + b
            // Big-endian 16-bit storage.
            fun put(off: Int, v16: Int) {
                grid[ix * 6 + off * 2 + 0] = ((v16 shr 8) and 0xFF).toByte()
                grid[ix * 6 + off * 2 + 1] = (v16 and 0xFF).toByte()
            }
            put(0, r * 65535)
            put(1, g * 65535)
            put(2, b * 65535)
        }
        val v = floatArrayOf(0.25f, 0.75f, 0.5f, 0f)
        skcmsClut(
            inputChannels = 3, outputChannels = 3,
            gridPoints = intArrayOf(2, 2, 2, 0),
            grid8 = null, grid16 = grid, values = v,
        )
        assertNear(0.25f, v[0], 1e-5f, "R")
        assertNear(0.75f, v[1], 1e-5f, "G")
        assertNear(0.50f, v[2], 1e-5f, "B")
    }

    @Test
    fun `4D CMYK to RGB cube approximates 1-c times 1-k`() {
        // Build a 2^4 CMYK cube with the rough RGB approximation
        // (R, G, B) ≈ ((1-c)(1-k), (1-m)(1-k), (1-y)(1-k)).
        // ICC layout: C slowest, K fastest. ix = c*8 + m*4 + y*2 + k.
        val grid = ByteArray(16 * 3)
        for (c in 0..1) for (m in 0..1) for (y in 0..1) for (k in 0..1) {
            val ix = c * 8 + m * 4 + y * 2 + k
            grid[ix * 3 + 0] = (((1 - c) * (1 - k)) * 255).toByte()
            grid[ix * 3 + 1] = (((1 - m) * (1 - k)) * 255).toByte()
            grid[ix * 3 + 2] = (((1 - y) * (1 - k)) * 255).toByte()
        }
        for ((cmyk, expected) in listOf(
            floatArrayOf(0f, 0f, 0f, 0f) to floatArrayOf(1f, 1f, 1f),
            floatArrayOf(1f, 0f, 0f, 0f) to floatArrayOf(0f, 1f, 1f),
            floatArrayOf(0f, 0f, 0f, 1f) to floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f) to floatArrayOf(0.25f, 0.25f, 0.25f),
        )) {
            val v = floatArrayOf(cmyk[0], cmyk[1], cmyk[2], cmyk[3])
            skcmsClut(
                inputChannels = 4, outputChannels = 3,
                gridPoints = intArrayOf(2, 2, 2, 2),
                grid8 = grid, grid16 = null, values = v,
            )
            for (i in 0..2) assertNear(expected[i], v[i], 1e-5f,
                "input=${cmyk.toList()} out[$i]")
        }
    }

    // ----- evalA2b / evalB2a pipeline -----

    @Test
    fun `evalA2b with identity input curves and identity 2x2x2 grid is identity`() {
        val identityGrid = ByteArray(8 * 3)
        for (r in 0..1) for (g in 0..1) for (b in 0..1) {
            val ix = r * 4 + g * 2 + b
            identityGrid[ix * 3 + 0] = (r * 255).toByte()
            identityGrid[ix * 3 + 1] = (g * 255).toByte()
            identityGrid[ix * 3 + 2] = (b * 255).toByte()
        }
        val identityCurve = SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        val a2b = SkcmsA2B(
            inputCurves = arrayOf(identityCurve, identityCurve, identityCurve, null),
            grid8 = identityGrid,
            inputChannels = 3,
            gridPoints = intArrayOf(2, 2, 2, 0),
            // outputChannels is the CLUT's output dim (3 here). Output curves
            // are nulls so they're no-ops.
            outputChannels = 3,
            outputCurves = arrayOfNulls(3),
            matrixChannels = 0,    // skip matrix step
        )
        val v = floatArrayOf(0.3f, 0.6f, 0.9f, 1f)
        evalA2b(a2b, v)
        assertNear(0.3f, v[0], 1e-5f)
        assertNear(0.6f, v[1], 1e-5f)
        assertNear(0.9f, v[2], 1e-5f)
    }

    @Test
    fun `evalA2b applies input curves before the CLUT lookup`() {
        // Identity grid, but apply a 2.0-power input curve on each channel.
        // Output should be RGB^2.0 (modulo grid sampling drift).
        val identityGrid = ByteArray(8 * 3)
        for (r in 0..1) for (g in 0..1) for (b in 0..1) {
            val ix = r * 4 + g * 2 + b
            identityGrid[ix * 3 + 0] = (r * 255).toByte()
            identityGrid[ix * 3 + 1] = (g * 255).toByte()
            identityGrid[ix * 3 + 2] = (b * 255).toByte()
        }
        val tf2 = SkcmsTransferFunction(g = 2f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f)
        val curve2 = SkcmsCurve.Parametric(tf2)
        val a2b = SkcmsA2B(
            inputCurves = arrayOf(curve2, curve2, curve2, null),
            grid8 = identityGrid,
            inputChannels = 3,
            gridPoints = intArrayOf(2, 2, 2, 0),
            outputChannels = 3,
            outputCurves = arrayOfNulls(3),
        )
        val v = floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        evalA2b(a2b, v)
        assertNear(0.25f, v[0], 1e-5f, "0.5^2 = 0.25")
        assertNear(0.25f, v[1], 1e-5f)
        assertNear(0.25f, v[2], 1e-5f)
    }

    @Test
    fun `evalA2b applies the 3x4 matrix after the matrix curves`() {
        // Skip the CLUT (inputChannels=0) — drive the matrix path directly.
        // Matrix swaps R and B and adds 0.1 offset on G.
        val swap = SkcmsMatrix3x4(arrayOf(
            floatArrayOf(0f, 0f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f, 0.1f),
            floatArrayOf(1f, 0f, 0f, 0f),
        ))
        val a2b = SkcmsA2B(
            inputChannels = 0,  // skip input curves + CLUT
            matrixChannels = 3,
            matrixCurves = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
                SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
                SkcmsCurve.Parametric(SkNamedTransferFn.kLinear),
            ),
            matrix = swap,
            outputChannels = 0,
        )
        val v = floatArrayOf(0.2f, 0.5f, 0.7f, 1f)
        evalA2b(a2b, v)
        assertNear(0.7f, v[0], 1e-6f, "R = old B")
        assertNear(0.6f, v[1], 1e-6f, "G = old G + 0.1")
        assertNear(0.2f, v[2], 1e-6f, "B = old R")
    }

    @Test
    fun `evalA2b CMYK input forces alpha to 1`() {
        // 4-input CMYK CLUT: alpha gets pinned to 1 after the CLUT.
        val grid = ByteArray(16 * 3)
        for (c in 0..1) for (m in 0..1) for (y in 0..1) for (k in 0..1) {
            val ix = c * 8 + m * 4 + y * 2 + k
            grid[ix * 3 + 0] = (((1 - c) * (1 - k)) * 255).toByte()
            grid[ix * 3 + 1] = (((1 - m) * (1 - k)) * 255).toByte()
            grid[ix * 3 + 2] = (((1 - y) * (1 - k)) * 255).toByte()
        }
        val identityCurve = SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        val a2b = SkcmsA2B(
            inputCurves = arrayOf(identityCurve, identityCurve, identityCurve, identityCurve),
            grid8 = grid,
            inputChannels = 4,
            gridPoints = intArrayOf(2, 2, 2, 2),
            outputChannels = 3,
            outputCurves = arrayOfNulls(3),
        )
        val v = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f)  // (c, m, y, k)
        evalA2b(a2b, v)
        assertEquals(1f, v[3], "alpha must be forced to 1 for CMYK")
    }

    @Test
    fun `evalB2a applies pipeline in reverse order`() {
        // Build a B2A that routes RGB → CMYK via:
        //   identity input curves → 3x4 identity matrix → 3D 2^3 CMYK grid
        //   → identity output curves on 3 channels (we skip the 4th output).
        val identityCurve = SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        val identityMatrix = SkcmsMatrix3x4(arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f, 0f),
            floatArrayOf(0f, 0f, 1f, 0f),
        ))
        // Cube where (R, G, B) ∈ {0,1}^3 maps to (R, G, B) RGB → output.
        val grid = ByteArray(8 * 3)
        for (r in 0..1) for (g in 0..1) for (b in 0..1) {
            val ix = r * 4 + g * 2 + b
            grid[ix * 3 + 0] = (r * 255).toByte()
            grid[ix * 3 + 1] = (g * 255).toByte()
            grid[ix * 3 + 2] = (b * 255).toByte()
        }
        val b2a = SkcmsB2A(
            inputCurves = arrayOf(identityCurve, identityCurve, identityCurve),
            inputChannels = 3,
            matrixChannels = 3,
            matrixCurves = arrayOf(identityCurve, identityCurve, identityCurve),
            matrix = identityMatrix,
            outputChannels = 3,
            outputCurves = arrayOf(identityCurve, identityCurve, identityCurve, null),
            grid8 = grid,
            gridPoints = intArrayOf(2, 2, 2, 0),
        )
        val v = floatArrayOf(0.3f, 0.6f, 0.9f, 1f)
        evalB2a(b2a, v)
        assertNear(0.3f, v[0], 1e-5f)
        assertNear(0.6f, v[1], 1e-5f)
        assertNear(0.9f, v[2], 1e-5f)
    }

    // ----- equals / hashCode -----

    @Test
    fun `SkcmsA2B equals returns true for content-equal instances`() {
        val a = SkcmsA2B(inputChannels = 3, gridPoints = intArrayOf(2, 2, 2, 0))
        val b = SkcmsA2B(inputChannels = 3, gridPoints = intArrayOf(2, 2, 2, 0))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `SkcmsA2B equals returns false on differing grid8`() {
        val a = SkcmsA2B(grid8 = byteArrayOf(0, 1, 2))
        val b = SkcmsA2B(grid8 = byteArrayOf(0, 1, 3))
        assertNotEquals(a, b)
    }

    @Test
    fun `SkcmsB2A equals discriminates inputChannels`() {
        val a = SkcmsB2A(inputChannels = 3)
        val b = SkcmsB2A(inputChannels = 4)
        assertNotEquals(a, b)
    }

    @Test
    fun `SkcmsB2A equals returns true for two EMPTYs`() {
        assertEquals(SkcmsB2A.EMPTY, SkcmsB2A.EMPTY)
        assertEquals(SkcmsB2A(), SkcmsB2A())
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float, label: String = "") {
        assertTrue(kotlin.math.abs(expected - actual) <= tol,
            "$label: expected $expected ± $tol, got $actual (diff ${actual - expected})")
    }
}
