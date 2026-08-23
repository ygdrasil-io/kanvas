package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

class SeparableBlurCpuOracleTest {
    @Test
    fun `sigma three uses the hand-derived normalized symmetric seven-tap kernel`() {
        val weights = SeparableBlurCpuOracle(GPUPixelBounds(0, 0, 1, 1), floatArrayOf(1f, 1f, 1f, 1f), 3f).kernelWeights()

        assertEquals(7, weights.size)
        assertTrue(abs(weights.sum() - 1f) < 0.00001f)
        assertTrue(abs(weights[0] - 0.10629f) < 0.0001f)
        assertTrue(abs(weights[1] - 0.14032f) < 0.0001f)
        assertTrue(abs(weights[2] - 0.16577f) < 0.0001f)
        assertTrue(abs(weights[3] - 0.17524f) < 0.0001f)
        assertTrue(abs(weights[0] - weights[6]) < 0.000001f)
        assertTrue(abs(weights[1] - weights[5]) < 0.000001f)
        assertTrue(abs(weights[2] - weights[4]) < 0.000001f)
    }

    @Test
    fun `one-pixel opaque impulse is symmetric after both passes and quantizes only at the end`() {
        val pixels = SeparableBlurCpuOracle(GPUPixelBounds(3, 3, 4, 4), floatArrayOf(1f, 1f, 1f, 1f), 3f).render(7, 7)

        assertEquals(8, channel(pixels, 7, 3, 3))
        assertEquals(7, channel(pixels, 7, 2, 3))
        assertEquals(7, channel(pixels, 7, 4, 3))
        assertEquals(7, channel(pixels, 7, 3, 2))
        assertEquals(7, channel(pixels, 7, 3, 4))
        assertEquals(channel(pixels, 7, 1, 3), channel(pixels, 7, 5, 3))
        assertEquals(channel(pixels, 7, 3, 1), channel(pixels, 7, 3, 5))
    }

    @Test
    fun `transparent decal outside the target darkens an opaque edge and preserves transparent input`() {
        val opaque = SeparableBlurCpuOracle(GPUPixelBounds(0, 0, 7, 7), floatArrayOf(1f, 1f, 1f, 1f), 3f).render(7, 7)
        val transparent = SeparableBlurCpuOracle(GPUPixelBounds(3, 3, 4, 4), floatArrayOf(0f, 0f, 0f, 0f), 3f).render(7, 7)

        assertEquals(255, channel(opaque, 7, 3, 3))
        assertEquals(88, channel(opaque, 7, 0, 0))
        assertTrue(transparent.all { it == 0.toByte() })
    }

    private fun channel(pixels: ByteArray, width: Int, x: Int, y: Int): Int =
        pixels[(y * width + x) * 4].toInt() and 0xff
}
