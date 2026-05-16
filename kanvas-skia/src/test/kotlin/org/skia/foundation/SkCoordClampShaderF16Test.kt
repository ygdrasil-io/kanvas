package org.skia.foundation


import org.skia.math.SkColor
import org.skia.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkAlphaType
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import kotlin.math.abs

/**
 * R-suivi.2 — verifies that the F16 path through [SkShaders.CoordClamp]
 * preserves precision instead of round-tripping through the 8-bit byte
 * path (which would quantize all components into `[0, 1]` and clip
 * everything above 1.0).
 *
 * The proof is built on a synthetic "HDR" child shader whose
 * [SkShader.sampleAtLocalF16] returns float components > 1.0, and whose
 * [SkShader.sampleAtLocal] clips them to the 8-bit range. If the
 * clamp wrapper goes through the byte path the HDR information is
 * lost ; if it routes through `sampleAtLocalF16` directly, the
 * `2.0`-range components survive.
 */
class SkCoordClampShaderF16Test {

    private val identityXform: SkColorSpaceXformSteps = SkColorSpaceXformSteps(
        src = SkColorSpace.makeSRGB(), srcAT = SkAlphaType.kUnpremul,
        dst = SkColorSpace.makeSRGB(), dstAT = SkAlphaType.kUnpremul,
    )

    /**
     * Synthetic HDR shader. Returns `(2.0, 1.5, 0.5, 1.0)` premul in
     * F16, but the byte path clips R/G to `0xFF` (= 1.0) — so the byte
     * route loses everything above 1.0.
     */
    private class HdrShader : SkShader() {
        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            val opaqueOrange = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0x80)
            for (i in 0 until count) dst[i] = opaqueOrange
        }
        override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0x80)
        override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
            // HDR — R/G > 1.0, B in-range, alpha 1.0 (premul = unpremul
            // when alpha = 1).
            dst[dstOffset]     = 2.0f
            dst[dstOffset + 1] = 1.5f
            dst[dstOffset + 2] = 0.5f
            dst[dstOffset + 3] = 1.0f
        }
        override fun shadeRowF16(devX: Int, devY: Int, count: Int, dst: FloatArray) {
            var di = 0
            for (i in 0 until count) {
                dst[di]     = 2.0f
                dst[di + 1] = 1.5f
                dst[di + 2] = 0.5f
                dst[di + 3] = 1.0f
                di += 4
            }
        }
    }

    @Test
    fun `shadeRowF16 routes through child sampleAtLocalF16 and preserves HDR components`() {
        val hdr = HdrShader()
        val clamped = SkShaders.CoordClamp(hdr, SkRect(0f, 0f, 10f, 10f))
        clamped.setupForDraw(SkMatrix.Identity, identityXform)
        // Sample 4 pixels — every one of them inside the clamp rect,
        // so the wrapper should sample the child at unchanged coords.
        val out = FloatArray(4 * 4)
        clamped.shadeRowF16(0, 0, 4, out)
        for (i in 0 until 4) {
            val o = i * 4
            assertEquals(2.0f, out[o], 1e-5f, "R[$i] HDR must survive clamp")
            assertEquals(1.5f, out[o + 1], 1e-5f, "G[$i] HDR must survive clamp")
            assertEquals(0.5f, out[o + 2], 1e-5f, "B[$i] preserved")
            assertEquals(1.0f, out[o + 3], 1e-5f, "A[$i]")
        }
    }

    @Test
    fun `sampleAtLocalF16 forwards through the clamp wrapper`() {
        val hdr = HdrShader()
        val clamped = SkShaders.CoordClamp(hdr, SkRect(0f, 0f, 10f, 10f))
        clamped.setupForDraw(SkMatrix.Identity, identityXform)
        val dst = FloatArray(4)
        clamped.sampleAtLocalF16(5f, 5f, dst, 0)
        assertEquals(2.0f, dst[0], 1e-5f)
        assertEquals(1.5f, dst[1], 1e-5f)
        assertEquals(0.5f, dst[2], 1e-5f)
        assertEquals(1.0f, dst[3], 1e-5f)
    }

    @Test
    fun `sampleAtLocalF16 outside the rect snaps to the boundary in float space`() {
        // A shader that returns the local coords as F16 components, so
        // we can directly read the clamped values.
        val coordShader = object : SkShader() {
            override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
                for (i in 0 until count) dst[i] = 0
            }
            override fun sampleAtLocal(lx: Float, ly: Float): SkColor = 0
            override fun sampleAtLocalF16(lx: Float, ly: Float, dst: FloatArray, dstOffset: Int) {
                dst[dstOffset]     = lx
                dst[dstOffset + 1] = ly
                dst[dstOffset + 2] = 0f
                dst[dstOffset + 3] = 1f
            }
        }
        val clamped = SkShaders.CoordClamp(coordShader, SkRect(1f, 2f, 3f, 4f))
        clamped.setupForDraw(SkMatrix.Identity, identityXform)
        val dst = FloatArray(4)
        // (10, 10) → snaps to (3, 4).
        clamped.sampleAtLocalF16(10f, 10f, dst, 0)
        assertEquals(3f, dst[0], 1e-5f)
        assertEquals(4f, dst[1], 1e-5f)
        // (-5, -5) → snaps to (1, 2).
        clamped.sampleAtLocalF16(-5f, -5f, dst, 0)
        assertEquals(1f, dst[0], 1e-5f)
        assertEquals(2f, dst[1], 1e-5f)
        // (1.5, 2.5) inside rect — unchanged.
        clamped.sampleAtLocalF16(1.5f, 2.5f, dst, 0)
        assertEquals(1.5f, dst[0], 1e-5f)
        assertEquals(2.5f, dst[1], 1e-5f)
    }

    @Test
    fun `default sampleAtLocalF16 forwards to sampleAtLocal with premul promotion`() {
        // A shader that only implements the byte sampleAtLocal — the
        // default sampleAtLocalF16 fallback should promote correctly.
        val byteOnlyShader = object : SkShader() {
            override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
                for (i in 0 until count) dst[i] = SkColorSetARGB(0x80, 0x80, 0x40, 0x20)
            }
            override fun sampleAtLocal(lx: Float, ly: Float): SkColor =
                SkColorSetARGB(0x80, 0x80, 0x40, 0x20)
            // No sampleAtLocalF16 override — inherit the base default.
        }
        byteOnlyShader.setupForDraw(SkMatrix.Identity, identityXform)
        val dst = FloatArray(4)
        byteOnlyShader.sampleAtLocalF16(0f, 0f, dst, 0)
        val a = 0x80 / 255f
        // R/G/B premultiplied.
        assertTrue(abs(dst[0] - 0x80 / 255f * a) < 0.005f, "R premul")
        assertTrue(abs(dst[1] - 0x40 / 255f * a) < 0.005f, "G premul")
        assertTrue(abs(dst[2] - 0x20 / 255f * a) < 0.005f, "B premul")
        assertTrue(abs(dst[3] - a) < 0.005f, "A")
    }
}
