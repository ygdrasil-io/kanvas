package org.skia.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkShader
import org.skia.math.SkMatrix

/**
 * Exercises [SkShaderMaskFilter] : the filter must multiply incoming
 * coverage by the shader's per-pixel alpha.
 */
class SkShaderMaskFilterTest {

    /**
     * Tiny in-test shader that returns a fixed alpha per pixel (RGB = 0xFF
     * to catch any implementation that confuses channels).
     */
    private class ConstantAlphaShader(private val alpha: Int) : SkShader() {
        override fun setupForDraw(canvasCtm: SkMatrix, xform: SkColorSpaceXformSteps) {
            super.setupForDraw(canvasCtm, xform)
        }

        override fun shadeRow(devX: Int, devY: Int, count: Int, dst: IntArray) {
            val v = SkColorSetARGB(alpha, 0xFF, 0xFF, 0xFF)
            for (i in 0 until count) dst[i] = v
        }
    }

    @Test
    fun `Make returns a non-null filter`() {
        val mf = SkShaderMaskFilter.Make(ConstantAlphaShader(128))
        assertNotNull(mf)
        assertEquals(0, mf.margin())
    }

    @Test
    fun `coverage is multiplied by shader alpha`() {
        val mf = SkShaderMaskFilter.Make(ConstantAlphaShader(128))
        // 4-pixel input mask with varied coverage.
        val src = byteArrayOf(0, 64, 128.toByte(), 255.toByte())
        val out = mf.filterMask(src, 4, 1)
        // (cov * 128 + 127) / 255 — same formula as alpha premultiply.
        assertEquals(0,    out[0].toInt() and 0xFF)
        assertEquals(32,   out[1].toInt() and 0xFF)  // (64*128+127)/255 = 32
        assertEquals(64,   out[2].toInt() and 0xFF)  // (128*128+127)/255 = 64
        assertEquals(128,  out[3].toInt() and 0xFF) // (255*128+127)/255 = 128
    }

    @Test
    fun `opaque shader leaves coverage unchanged`() {
        val mf = SkShaderMaskFilter.Make(ConstantAlphaShader(255))
        val src = byteArrayOf(0, 32, 200.toByte(), 255.toByte())
        val out = mf.filterMask(src, 4, 1)
        for (i in src.indices) {
            assertEquals(src[i].toInt() and 0xFF, out[i].toInt() and 0xFF)
        }
    }

    @Test
    fun `transparent shader zeroes the mask`() {
        val mf = SkShaderMaskFilter.Make(ConstantAlphaShader(0))
        val src = byteArrayOf(0, 32, 200.toByte(), 255.toByte())
        val out = mf.filterMask(src, 4, 1)
        for (b in out) assertEquals(0, b.toInt() and 0xFF)
    }
}
