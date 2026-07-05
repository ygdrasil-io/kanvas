package org.graphiks.kanvas.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComparisonUtilsTest {
    @Test
    fun `ssim of identical buffers is 1 point 0`() {
        val w = 32; val h = 32
        val identical = ByteArray(w * h * 4) { i -> (i % 4 + 10).toByte() }
        val ssim = ComparisonUtils.computeSSIM(identical, identical, w, h)
        assertTrue(ssim > 0.999, "Expected ~1.0 for identical, got $ssim")
    }

    @Test
    fun `ssim of inverted buffers is low`() {
        val w = 32; val h = 32
        val a = ByteArray(w * h * 4) { i -> if (i % 4 == 0) 200.toByte() else 50.toByte() }
        val b = ByteArray(w * h * 4) { i -> if (i % 4 == 0) 50.toByte() else 200.toByte() }
        val ssim = ComparisonUtils.computeSSIM(a, b, w, h)
        assertTrue(ssim < 0.9, "Expected low SSIM for different buffers, got $ssim")
    }

    @Test
    fun `ssim blocks returns correct count`() {
        val w = 32; val h = 32
        val buf = ByteArray(w * h * 4) { 100.toByte() }
        val blocks = ComparisonUtils.computeSSIMBlocks(buf, buf, w, h, blockSize = 16)
        assertEquals(4, blocks.size)
    }
}
