package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SurfaceSrgbSeparableMaskBlurCpuOracleTest {
    @Test
    fun `normal sigma three fixture derives a symmetric normalized seven tap kernel`() {
        val oracle = SurfaceSrgbSeparableMaskBlurCpuOracle()
        val weights = oracle.kernelWeights()
        assertEquals(7, weights.size)
        assertEquals(1.0, weights.sum(), 1e-12)
        weights.indices.forEach { index -> assertEquals(weights[index], weights[weights.lastIndex - index], 1e-12) }
    }

    @Test
    fun `fixture local frame and transparent decal boundary are closed`() {
        val oracle = SurfaceSrgbSeparableMaskBlurCpuOracle()
        assertEquals(SurfaceSrgbOracleMath.PixelRect(7, 7, 57, 57), oracle.localFrame)
        assertEquals(0.0, oracle.sampleMask(-1, 16))
        assertEquals(0.0, oracle.sampleMask(64, 16))
        assertEquals(0.0, oracle.sampleMask(6, 16))
    }

    @Test
    fun `mask horizontal vertical and style stages are independently q8 quantized`() {
        val stages = SurfaceSrgbSeparableMaskBlurCpuOracle().stages()
        assertEquals(0.0, stages.mask[6 * 64 + 16])
        assertEquals(27.0 / 255.0, stages.horizontal[16 * 64 + 13])
        assertEquals(3.0 / 255.0, stages.vertical[13 * 64 + 13])
        assertEquals(3.0 / 255.0, stages.style[13 * 64 + 13])
        listOf(stages.mask, stages.horizontal, stages.vertical, stages.style).forEach { stage ->
            assertTrue(stage.all { it == SurfaceSrgbOracleMath.q8(it) / 255.0 })
        }
    }

    @Test
    fun `quantized blur fixture matches independently derived final pixels`() {
        val pixels = SurfaceSrgbSeparableMaskBlurCpuOracle().render(64, 64)
        assertPixel(pixels, 13, 13, intArrayOf(1, 6, 19, 3))
        assertPixel(pixels, 15, 16, intArrayOf(19, 53, 101, 62))
        assertPixel(pixels, 32, 32, intArrayOf(46, 107, 194, 255))
        assertPixel(pixels, 6, 16, intArrayOf(0, 0, 0, 0))
    }

    private fun assertPixel(pixels: ByteArray, x: Int, y: Int, expected: IntArray) {
        val offset = (y * 64 + x) * 4
        assertContentEquals(expected.map(Int::toByte).toByteArray(), pixels.copyOfRange(offset, offset + 4))
    }
}
